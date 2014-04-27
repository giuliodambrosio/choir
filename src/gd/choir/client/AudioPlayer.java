/**
 *
 */
package gd.choir.client;

import java.io.IOException;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
//import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;

import gd.choir.common.AudioDataPacketListener;
import gd.choir.common.AudioEndPacketListener;
import gd.choir.common.PacketDispatcher;
import gd.choir.proto.packets.audio.PacketData;
import gd.choir.proto.packets.audio.PacketEnd;

/**
 * Plays an audio file
 *
 * @author Giulio D'Ambrosio
 */
public class AudioPlayer implements
        AudioDataPacketListener,
        AudioEndPacketListener,
        Runnable {
    /**
     * Minimum number of frames to collect in the buffer.
     * When this number of frames is not available playing stops and and buffering starts
     */
    private static final int MIN_BUFFERED_FRAMES_TO_KEEP_PLAYING = 2;

    /**
     * Maximum number of frames to collect before starting to play
     */
    private static final int MAX_BUFFERED_FRAMES_TO_COLLECT_BEFORE_PLAYING = 4;

    /**
     * This flag is used to stop thread execution
     */
    private boolean alive = true;

    /**
     * True when the player is collecting frames in the buffer before starting to play
     */
    private boolean buffering = true;

    /**
     * Thread running this task
     */
    private Thread runningThread = null;

    private char currentlyPlayingMusicId;

    private String currentlyPlayingMusicTitle;

    /**
     * Minimum number of bytes of data to be available in order to continue to play
     */
    private int minBufferedSize;

    /**
     * Bytes of data to collect in the buffer before starting to play
     */
    private int maxBufferedSize;

    /**
     * Incoming packet stream
     */
    private final AudioPacketStreamReader incomingPacketStream;

    private PacketDispatcher demu;

    /**
     * Istanza del gestore della playlist del client.
     */
    private ClientPlaylistStreamingManager clientPlaylistStreamingManager;

    private AudioInputStream ais;
    private SourceDataLine sdl;
    private byte[] buffer;

    public AudioPlayer(final char currentlyPlayingMusicId, final String currentlyPlayingMusicTitle,
                       final ClientPlaylistStreamingManager clientPlaylistStreamingManager) throws Exception {
        this.currentlyPlayingMusicId = currentlyPlayingMusicId;
        this.currentlyPlayingMusicTitle = currentlyPlayingMusicTitle;
        this.demu = clientPlaylistStreamingManager.getIncomingPacketDispatcher();
        this.clientPlaylistStreamingManager = clientPlaylistStreamingManager;
        incomingPacketStream = new AudioPacketStreamReader();
        demu.registerListener((AudioDataPacketListener) this);
        demu.registerListener((AudioEndPacketListener) this);
    }

    /**
     * Ultime operazioni di pulizia. Se non era stato fatto precedentemente,
     * viene rimosso questo oggetto dagli ascoltatori registrati in
     * {@link #demu}
     *
     * @throws Throwable
     */
    protected final void finalize() throws Throwable {
        super.finalize();
        demu.unregisterListener((AudioEndPacketListener) this);
        demu.unregisterListener((AudioDataPacketListener) this);
    }

    /**
     * Scollega questa istanza dalla sorgente di pacchetti multicast.
     */
    private void destroy() {
        if (currentlyPlayingMusicId != (char) -1) {
            clientPlaylistStreamingManager.notifyEndOfAudioPlayer(this);
            currentlyPlayingMusicId = (char) -1;
        }
    }

    /**
     * @return the currentlyPlayingMusicId
     */
    public final char getCurrentlyPlayingMusicId() {
        return currentlyPlayingMusicId;
    }

    /**
     * Inizia la riproduzione del brano corrente.
     *
     * @return false se non è stato possibile iniziare la riproduzione
     */
    private boolean startPlaying() {
        boolean success = true;
        AudioFormat af;
        int bufferSize;
        try {
            ais = AudioSystem.getAudioInputStream(incomingPacketStream);
            af = ais.getFormat();
            sdl = AudioSystem.getSourceDataLine(af);

            // Inizializza un buffer di dimensione adeguata a contenere un
            // secondo di audio
            bufferSize = (int) (af.getSampleRate() * af.getFrameSize());
            buffer = new byte[bufferSize];
            minBufferedSize = bufferSize * MIN_BUFFERED_FRAMES_TO_KEEP_PLAYING;
            maxBufferedSize = bufferSize * MAX_BUFFERED_FRAMES_TO_COLLECT_BEFORE_PLAYING;

            // Apre il canale audio e imposta il volume master al massimo
            sdl.open(af);
//			if (sdl.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
//				 FloatControl volume = (FloatControl) sdl.getControl(
//				 FloatControl.Type.MASTER_GAIN );
//				 volume.setValue( volume.getMaximum() );
//			}
            // Avvia la riproduzione dell'audio
            sdl.start();
        } catch (IllegalArgumentException e) {
            // e.printStackTrace();
            success = false;
        } catch (UnsupportedAudioFileException e) {
            e.printStackTrace();
            success = false;
        } catch (IOException e) {
            // e.printStackTrace();
            success = false;
        } catch (LineUnavailableException e) {
            // e.printStackTrace();
            success = false;
            System.err.println(e.getMessage());
        } finally {
            if (!success) {
                System.err.printf(
                        "Could not play streaming audio for: %s"
                                + currentlyPlayingMusicTitle
                );
                System.out.println();
            } else {
                System.out.printf(
                        "Playing streaming audio for: %s",
                        currentlyPlayingMusicTitle
                );
                System.out.println();
            }
        }
        return success;
    }

    /**
     * Riceve un pacchetto di dati audio. Viene notificato su questo stesso
     * oggetto la disponibilità di nuovi dati, se il thread che esegue la
     * riproduzione audio è in modalità {@link #buffering}.
     */
    @Override
    public final void packetArrived(final PacketData packet) {
        if (alive && packet.musicId == currentlyPlayingMusicId) {
            try {
                incomingPacketStream.addPacket(packet.audioData, packet.audioData.length);
            } catch (Exception e) {
                alive = false;
            }
            if (buffering || !alive) {
                synchronized (this) {
                    notify();
                }
            }
        }
    }

    /**
     * Gestisce l'arrivo di un pacchetto di tipo end, riguardo il brano
     * corrente. Se il thread di riproduzione audio è in attesa di dati (
     * {@link #buffering}) segnala su questo stesso oggetto la fine dei dati.
     */
    @Override
    public final void packetArrived(final PacketEnd packet) {
        if (alive && packet.musicId == currentlyPlayingMusicId) {
            incomingPacketStream.setCompleted();
            if (buffering) {
                synchronized (this) {
                    notify();
                }
            }
        }
    }

    /**
     * Interrompe la riproduzione di un brano e chiude il thread associato a
     * questa istanza.
     *
     * @throws InterruptedException
     * @throws IOException
     */
    public final void stop() throws InterruptedException, IOException {
        System.err.println("AudioPlayer is stopping...");
        synchronized (incomingPacketStream) {
            alive = false;
            incomingPacketStream.close();
        }
        synchronized (this) {
            notify();
        }
        if (runningThread != null && runningThread.isAlive()) {
            runningThread.join();
        }
        destroy();
    }

    /**
     * Ciclo principale: apre la periferica audio di riproduzione, attende che
     * l'input stream a pacchetti abbia riempito il buffer e legge/scrive i dati
     * audio fino al termine dello stream in ingresso, o fino alla terminazione
     * forzata (tramite chiamata a {@link #stop()}).
     */
    @Override
    public final void run() {
        int dotcount;
        // Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
        if (!(alive = startPlaying())) {
            // Il thread terminerà perchè non è riuscito ad aprire il brano
            System.out.println("AudioPlayer will skip this file...");
        } else {

            // Legge, a blocchi, i dati dall'AudioInputStream e li scrive sul
            // canale audio
            int l = 1;
            try {

                while (alive && l >= 0) {
                    if (alive
                            && (buffering = !incomingPacketStream.isCompleted()
                            && incomingPacketStream.available() < minBufferedSize)) {
                        // Quando i dati disponibili scendono sotto la soglia
                        // minima, riattende la bufferizzazione del
                        // massimo del buffer
                        sdl.stop();
                        for (dotcount = 0; alive && buffering; dotcount++) {
                            if (dotcount == 0) {
                                System.err.printf(
                                        "AudioPlayer: filling up the buffer with %d Kb",
                                        (maxBufferedSize - incomingPacketStream.available()) / 1024
                                );
                                System.err.println();
                            } else if (dotcount % 780 == 0) {
                                System.err.println(".");
                            } else if (dotcount % 10 == 0) {
                                System.err.print(".");
                            }
                            synchronized (this) {
                                wait();
                            }
                            if (!(buffering = !incomingPacketStream.isCompleted()
                                    && incomingPacketStream.available() < maxBufferedSize)) {
                                sdl.start();
                                System.err.println("\n\tstarting playback");
                            }
                        }
                    }

                    // Legge i dati dallo stream audio e li scrive nel
                    // dispositivo audio
                    l = ais.read(buffer);
                    if (l >= 0) {
                        sdl.write(buffer, 0, l);
                    }

                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                //
            }
            // Wait for the running audio to finish
            if (alive) {
                sdl.drain();
            }
            sdl.stop();
            sdl.close();
            try {
                incomingPacketStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.printf(
                    "Streaming completed for '%s'",
                    currentlyPlayingMusicTitle
            );
            System.out.println();
        }
        demu.unregisterListener((AudioEndPacketListener) this);
        demu.unregisterListener((AudioDataPacketListener) this);
        destroy();
    }
}
