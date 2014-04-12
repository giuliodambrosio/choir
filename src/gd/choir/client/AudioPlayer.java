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
import gd.choir.common.IncomingPacketDispatcher;
import gd.choir.proto.packets.audio.PacketData;
import gd.choir.proto.packets.audio.PacketEnd;

/**
 * Plays an audio file
 *
 * @author Giulio D'Ambrosio
 */
public class AudioPlayer implements AudioDataPacketListener,
        AudioEndPacketListener, Runnable {
    /**
     * Minimum number of frames to collect in the buffer.
     * When this number of frames is not available playing stops and and buffering starts
     */
    private static final int MIN_BUFFERED_FRAMES = 2;

    /**
     * Maximum number of frames to collect before starting to play
     */
    private static final int MAX_BUFFERED_FRAMES = 4;

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

    /**
     */
    private char currentlyPlayingMusicId;

    /**
     * Titolo del brano in riproduzione.
     */
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
    private AudioPacketStreamReader incomingPacketStream;

    /**
     * Sorgente di pacchetti multicast (via notifiche packetArrived).
     */
    private IncomingPacketDispatcher demu;

    /**
     * Istanza del gestore della playlist del client.
     */
    private ClientPlayer clientPlayer;

    private AudioInputStream ais;
    private AudioFormat af;
    private SourceDataLine sdl;
    private int bufsize;
    private byte[] buffer;

    public AudioPlayer(final char currentlyPlayingMusicId, final String currentlyPlayingMusicTitle,
                       final ClientPlayer clientPlayer) throws Exception {
        this.currentlyPlayingMusicId = currentlyPlayingMusicId;
        this.currentlyPlayingMusicTitle = currentlyPlayingMusicTitle;
        this.demu = clientPlayer.getIncomingPacketDispatcher();
        this.clientPlayer = clientPlayer;
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
            clientPlayer.notifyEndOf(this);
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
        try {
            ais = AudioSystem.getAudioInputStream(incomingPacketStream);
            af = ais.getFormat();
            sdl = AudioSystem.getSourceDataLine(af);

            // Inizializza un buffer di dimensione adeguata a contenere un
            // secondo di audio
            bufsize = (int) (af.getSampleRate() * af.getFrameSize());
            buffer = new byte[bufsize];
            minBufferedSize = bufsize * MIN_BUFFERED_FRAMES;
            maxBufferedSize = bufsize * MAX_BUFFERED_FRAMES;

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
                System.err.println("Impossibile iniziare la riproduzione di "
                        + currentlyPlayingMusicTitle);
            } else {
                System.out.println("Riproduzione brano : " + currentlyPlayingMusicTitle);
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
    public final void packetArrived(final PacketData jbdp) {
        if (alive && jbdp.musicId == currentlyPlayingMusicId) {
            try {
                incomingPacketStream.addPacket(jbdp.audioData, jbdp.audioData.length);
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
    public final void packetArrived(final PacketEnd jbdp) {
        if (alive && jbdp.musicId == currentlyPlayingMusicId) {
            incomingPacketStream.setCompleted();
            if (buffering) {
                synchronized (this) {
                    notify();
                }
            }
        }
    }

    /**
     * Crea e lancia il thread associato a questa istanza.
     */
    public final void start() {
        alive = true;
        runningThread = new Thread(this);
        runningThread.start();
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
            System.out.println("AudioPlayer salterà questo brano...");
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
                                System.err.println("AudioPlayer: in attesa di bufferizzare "
                                        + (maxBufferedSize - incomingPacketStream.available())
                                        / 1024 + " KB");
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
                                System.err.println("\n\tinizio riproduzione");
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
                e.printStackTrace();
            }
            // Aspetta che sia terminata la riproduzione dell'audio, quindi
            // ferma tutto
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
            System.out.println("Terminata la riproduzione di " + currentlyPlayingMusicTitle);
        }
        demu.unregisterListener((AudioEndPacketListener) this);
        demu.unregisterListener((AudioDataPacketListener) this);
        destroy();
    }

}
