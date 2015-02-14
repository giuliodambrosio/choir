/**
 *
 */
package uk.co.dambrosio.choir.client;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * Scans a directory contents and sends a music packet for each audio file
 * found.
 * The audio files are also added to the local client collection.
 *
 * @author Giulio D'Ambrosio
 */
public class DirectoryScanner implements Runnable {
    private static final int AUDIO_FILE_SENDER_THREAD_POOL_SIZE = 4;

    private static final int DIRECTORY_SCANNER_THREAD_POOL_SIZE = 2;

    /**
     * This is a counter semaphor.
     * It gets incremented every time a new task is added and decremented
     * every time a task has finished.
     * A notification on this instance is sent when the count reaches zero.
     */
    private final AtomicInteger remaining = new AtomicInteger(0);

    private File scanningPath;

    private Client mainClient;

    private ExecutorService senderThreadPool;

    private ExecutorService scannerThreadPool;

    public DirectoryScanner(final Client mainClient, final File scanningPath) {
        super();
        this.mainClient = mainClient;
        this.scanningPath = scanningPath;
        senderThreadPool = Executors.newFixedThreadPool(AUDIO_FILE_SENDER_THREAD_POOL_SIZE);
        scannerThreadPool = Executors.newFixedThreadPool(DIRECTORY_SCANNER_THREAD_POOL_SIZE);
    }

    /**
     * Launches the
     */
    @Override
    public final void run() {
        remaining.set(1);
        scanDir(scanningPath);

        try {
            synchronized (remaining) {
                remaining.wait();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        scannerThreadPool.shutdownNow();
        senderThreadPool.shutdownNow();
        System.out.printf(
                "Completed scanning directory %s for audio files: %d audio files found and notified to the server",
                scanningPath,
                mainClient.getNumAudioFiles()
        );
        System.out.println();
    }

    /**
     * Recursively scans a directory structure using one pool of threads
     * to read the directories and one pool of threads to send the found audio
     * files information to the server
     */
    private void scanDir(final File path) {
        /**
         * Analizza un file, e se si tratta di file audio lo inserisce nella
         * collezione del client, e ne comunica l'id al server.
         */
        class DirectoryEntry implements Runnable {
            private File entryPath;

            public DirectoryEntry(final File path) {
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
                    System.err.printf(
                            "File %s is not an audio file or has an unsupported format",
                            entryPath
                    );
                    System.err.println();
                } catch (IOException e1) {
                    ais = null;
                    System.err.printf(
                            "File %s can't be opened",
                            entryPath
                    );
                    System.err.println();
                }

                return ais != null;
            }

            @Override
            public void run() {
                ClientAudioFile audioFile;

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
                System.err.printf(
                        "Server was notified about audio file: '%s'",
                        audioFile.getMusicTitle()
                );
                System.err.println();
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
                    scannerThreadPool.submit(() -> scanDir(entry));
                } else {
                    senderThreadPool.submit(new DirectoryEntry(entry));
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
