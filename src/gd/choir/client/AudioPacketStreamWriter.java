/**
 *
 */
package gd.choir.client;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.Calendar;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import gd.choir.common.IncomingPacketDispatcher;
import gd.choir.proto.packets.audio.PacketBegin;
import gd.choir.proto.packets.audio.PacketData;
import gd.choir.proto.packets.audio.PacketEnd;

/**
 * Manda in streaming i dati audio di un file ad un gruppo multicast.
 *
 * @author Giulio D'Ambrosio
 */
public class AudioPacketStreamWriter implements Runnable {
    /**
     * Dimensione ottimale di ciascun pacchetto dati audio.
     */
    private static final int AUDIOPACKET_SIZE = 1000;

    /**
     * Flag: se false questo task è in chiusura.
     */
    private boolean alive = true;

    /**
     * Audio file da mandare in streaming.
     */
    private ClientAudioFile audioFile;

    /**
     * Indirizzo del gruppo multicast verso cui vengono spedititi i dati del
     * file.
     */
    private InetAddress groupAddress;

    /**
     * Porta del gruppo multicast verso cui vengono spedititi i dati del file.
     */
    private char groupPort;

    /**
     * Gestore dei pacchetti multicast.
     */
    private IncomingPacketDispatcher demu;

    /**
     * Thread principale associato a questa istanza.
     */
    private Thread runningThread = null;

    /**
     * Costruttore.
     *
     * @throws IOException
     */
    public AudioPacketStreamWriter(final InetAddress groupAddress, final char groupPort,
                                   final ClientAudioFile audioFile, IncomingPacketDispatcher demu)
            throws IOException {
        super();
        this.groupAddress = groupAddress;
        this.groupPort = groupPort;
        this.audioFile = audioFile;
        this.demu = demu;
    }

    /**
     * @param runningThread il thread che esegue questo task
     */
    public final void setRunningThread(final Thread runningThread) {
        this.runningThread = runningThread;
    }

    /**
     * @return il thread associato
     */
    public final Thread getRunningThread() {
        return runningThread;
    }

    /**
     * @return lo stato del thread principale
     */
    public final boolean isAlive() {
        return runningThread != null && runningThread.isAlive();
    }

    /**
     * Interrompe lo streaming del file associato.
     */
    public final void stop() {
        alive = false;
        if (!isAlive()) {
            return;
        }
        runningThread.interrupt();
        try {
            runningThread.join();
        } catch (InterruptedException e) {
        }
    }

    /**
     * Crea un nuovo thread che eseguirà lo streaming del brano associato a
     * questa istanza.
     */
    public final void start() {
        runningThread = new Thread(this);
        runningThread.start();
    }

    /**
     * Manda i dati audio in streaming su socket multicast fino al termine del
     * brano. Nel caso di un errore, viene comunque spedito il pacchetto ENDF
     *
     * @see java.lang.Runnable#run()
     */
    @Override
    public final void run() {
        int size = -1;
        PacketData pd;
        byte[] framebuf = null;
        AudioInputStream ais;
        AudioFormat af;
        InputStream fr = null;
        long lastTimestamp = 0L, // timestamp dell'ultimo pacchetto inviato
                lastFrameTimestamp = 0L, // timestamp dell'ultimo frame inviato
                toSleep; // numero di millisecondi da attendere
        int frameSize = 0, // dimensione in bytes di un frame
                packetRate = 0, // numero di pacchetti da inviare ogni secondo
                packetIntvTime = 0, // intervallo in millisecondi fra l'invio di
                // pacchetti
                packetSize = 0, // dimensione in bytes di un pacchetto
                packetCount = 0; // contatore del numero di pacchetti inviati (resettato
        // ad ogni frame)
        double syncInterval = 0d; // Contiene il ritardo in aggiunta / difetto
        // per pacchetto, calcolato di frame in
        // frame
        double frameBytesPerMs = 0; // Bytes x millisecondo originale

        // Thread.currentThread().setPriority(Thread.MAX_PRIORITY);

        try {
            demu.send(new PacketBegin(audioFile, groupAddress, groupPort));
            ais = AudioSystem.getAudioInputStream(audioFile.getFile());
            fr = new BufferedInputStream(new FileInputStream(audioFile.getFile()));
            af = ais.getFormat();
            if (af.getFrameSize() == AudioSystem.NOT_SPECIFIED) {
                throw new UnsupportedAudioFileException("Frame size non specificato nel file audio");
            } else {
                frameSize = af.getFrameSize();
            }
            if (af.getFrameRate() != AudioSystem.NOT_SPECIFIED) {
                frameSize *= (int) af.getFrameRate();
            }
            ais.close();
            frameBytesPerMs = frameSize / 1000.d;

            // Scelta della frequenza e dimensione dei pacchetti audio in cui
            // suddividere ogni frame:
            packetRate = (int) Math.round(((double) frameSize)
                    / AUDIOPACKET_SIZE);
            packetSize = (int) Math.ceil(((double) frameSize) / packetRate);
            packetIntvTime = (int) Math.floor(1000.0d / packetRate);
            syncInterval = (((packetRate * packetSize - frameSize) / frameBytesPerMs))
                    / packetRate;
            framebuf = new byte[packetSize];
            System.err.println("Inizio invio brano : "
                    + audioFile.getMusicTitle()
                    + " ["
                    + (int) audioFile.getMusicId()
                    + "]"
                    + "\n\taudio kbps:"
                    + (af.getChannels() * af.getSampleSizeInBits() * af.getSampleRate()) / 1000
                    + ", frame size:" + af.getFrameSize() + ", frame rate:"
                    + af.getFrameRate() + ", frame KBps:" + frameBytesPerMs
                    + "\n\tframe size:" + af.getFrameSize() + ", frame rate:"
                    + af.getFrameRate() + ", frame KBps:"
                    + (frameBytesPerMs * 1000) / 1024 + "\n\tpacket rate:"
                    + packetRate + ", pkt payload size:" + packetSize
                    + "\n\tpacket intv.:" + packetIntvTime
                    + ", packet sync intv.:"
                    + String.format("%.2f", syncInterval));
        } catch (IOException e) {
            alive = false;
            e.printStackTrace();
            System.err.println(getClass() + " : errore i/o");
        } catch (UnsupportedAudioFileException e) {
            alive = false;
            System.err.println(getClass() + " : audio file non supportato");
        }
        // try {
        // fw=new BufferedOutputStream(new
        // FileOutputStream(audioFile.getFile().getName()));
        // } catch (FileNotFoundException e1) {
        // e1.printStackTrace();
        // }
        while (alive) {
            try {
                if (alive = alive
                        && (size = fr.read(framebuf, 0, framebuf.length)) >= 0) {
                    // fw.write(framebuf,0,size);
                    if (lastTimestamp != 0
                            && (toSleep = (int) Math.round(syncInterval
                            + packetIntvTime
                            - (Calendar.getInstance().getTimeInMillis() - lastTimestamp))) > 0) {
                        Thread.sleep(toSleep);
                    }
                    lastTimestamp = Calendar.getInstance().getTimeInMillis();
                    if (packetCount++ == packetRate) {
                        // Completato l'invio di un frame audio
                        // System.err.println(String.format("sync: %.2f",
                        // syncInterval));
                        if (lastFrameTimestamp != 0) {

							/*
                             * Al termine di ogni frame, viene calcolato il
							 * ritardo / l'anticipo accumulato che viene
							 * redistribuito sui pacchetti del frame successivo,
							 * in modo da adeguarsi e recuperare dinamicamente
							 * il passo giusto. Ad esso viene aggiunto il tempo
							 * da aggiungere in pausa per la differenza fra i
							 * bytes spediti e quelli che dovevano essere
							 * spediti idealmente in base al numero di bytes per
							 * ms.
							 */
                            syncInterval += (((packetCount * packetSize - frameSize) / frameBytesPerMs) + (1000.0d - (Calendar.getInstance().getTimeInMillis() - lastFrameTimestamp)))
                                    / packetRate;
                        }
                        packetCount = 0;
                        lastFrameTimestamp = Calendar.getInstance().getTimeInMillis();
                    }
                    pd = new PacketData(audioFile.getMusicId(), framebuf, (char) size, groupAddress, groupPort);
                    demu.send(pd);
                }
            } catch (IOException e) {
                alive = false;
                e.printStackTrace();
            } catch (InterruptedException e) {
                alive = false;
                System.err.println("Streamer è stato interrotto");
            }
        }
        try {
            demu.send((new PacketEnd(audioFile.getMusicId(), groupAddress, groupPort)));
            if (fr != null) {
                fr.close();
            }
            // if (fw!=null) {
            // fw.close();
            // }
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.err.println("Streamer ha terminato l'invio del file audio...");
    }

}
