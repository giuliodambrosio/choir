/**
 *
 */
package gd.choir.client;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * Legge il contenuto della directory audio e spedisce un pacchetto music per
 * ogni file audio trovato. Aggiunge anche il file audio alla collezione del
 * client.
 *
 * @author Giulio D'Ambrosio
 */
public class DirectoryScanner implements Runnable {
    /**
     * Numero di thread contemporanei per la scansione delle entries delle
     * sottodirectories del percorso da scansionare.
     */
    private static final int MAX_THREADS_SENDERS = 4;

    /**
     * Numero di thread contemporanei per l'invio di notifiche brani disponibili
     * verso il server.
     */
    private static final int MAX_THREADS_SCANNERS = 2;

    /**
     * Semaforo / contatore. Ad ogni nuovo task viene incrementato e
     * decrementato ad ogni completamento. Quando viene raggiunto lo zero, viene
     * inviata una notifica su questo oggetto.
     */
    private AtomicInteger remaining = new AtomicInteger(0);

    /**
     * Percorso da scansionare.
     */
    private File audioPath;

    /**
     * Istanza del client principale.
     */
    private Client mainClient;

    /**
     * Pool dei thread che eseguono la scansione delle directories.
     */
    private ExecutorService execSenders;

    /**
     * Pool dei thread che notificano la disponibilità dei brani.
     */
    private ExecutorService execScanners;

    /**
     * Costruttore.
     *
     * @param mainClient Istanza del client principale
     * @param audioPath  Percorso da scansionare
     */
    public DirectoryScanner(final Client mainClient, final File audioPath) {
        super();
        this.mainClient = mainClient;
        this.audioPath = audioPath;
        execSenders = Executors.newFixedThreadPool(MAX_THREADS_SENDERS);
        execScanners = Executors.newFixedThreadPool(MAX_THREADS_SCANNERS);
    }

    /**
     * Esegue la lettura della directory utilizzando scanDir, quindi attende sul
     * monitor remaining che sia stata completata la scansione e libera i pool
     * dei thread uscendo con un messaggio informativo.
     */
    @Override
    public final void run() {
        remaining.set(1);
        scanDir(audioPath);

        try {
            synchronized (remaining) {
                remaining.wait();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        execScanners.shutdownNow();
        execSenders.shutdownNow();
        System.out.println("Scansione dei files audio terminata: "
                + mainClient.getNumAudioFiles()
                + " files audio trovati ed inviati al server");
    }

    /**
     * Esegue la lettura ricorsiva di un percorso nel file system utilizzando
     * due pool di thread: uno per la lettura delle directories ed uno per
     * l'analisi dei file audio e la comunicazione dei file verso il server.
     */
    private void scanDir(final File path) {
        /**
         * Analizza un file, e se si tratta di file audio lo inserisce nella
         * collezione del client, e ne comunica l'id al server.
         */
        class JBDirectoryEntry implements Runnable {
            private File entryPath;

            public JBDirectoryEntry(final File path) {
                super();
                entryPath = path;
                remaining.incrementAndGet();
            }

            private boolean isAudioFile() {
                AudioInputStream ais;

                try {
                    ais = AudioSystem.getAudioInputStream(entryPath);
                } catch (UnsupportedAudioFileException e1) {
                    ais = null;
                    System.err.println("File " + entryPath
                            + " is not an audio file or has an unsupported format");
                } catch (IOException e1) {
                    ais = null;
                    System.err.println("File " + entryPath
                            + " can't be opened");
                }

                return ais != null;
            }

            @Override
            public void run() {
                ClientAudioFile audioFile;

                // controllo sul tipo di file
                if (!isAudioFile()) {
                    if (remaining.decrementAndGet() == 0) {
                        synchronized (remaining) {
                            remaining.notify();
                        }
                    }
                    return;
                }
                audioFile = new ClientAudioFile(entryPath);
                if (mainClient.putAudioFile(audioFile)) {
                    try {
                        mainClient.notifyNewAudioFile(audioFile);
                    } catch (IOException e) {
                        e.printStackTrace();
                        mainClient.removeAudioFile(audioFile);
                    }
                }
                System.err.println("File audio notificato al server : "
                        + audioFile.getMusicTitle());
                if (remaining.decrementAndGet() == 0) {
                    synchronized (remaining) {
                        remaining.notify();
                    }
                }
            }

        }

        if (path != null) {
            for (final File entry : path.listFiles()) {
                if (entry.isDirectory()) {
                    remaining.incrementAndGet();
                    execScanners.submit(() -> scanDir(entry));
                } else {
                    execSenders.submit(new JBDirectoryEntry(entry));
                }
            }
        }
        if (remaining.decrementAndGet() == 0) {
            synchronized (remaining) {
                remaining.notify();
            }
        }
    }

}
