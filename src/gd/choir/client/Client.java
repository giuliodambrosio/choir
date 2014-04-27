/**
 *
 */
package gd.choir.client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Calendar;
import java.util.concurrent.ConcurrentHashMap;

import gd.choir.common.PacketDispatcher;
import gd.choir.proto.packets.*;
import gd.choir.proto.packets.Packet;

/**
 * Classe principale del client.
 *
 * @author Giulio D'Ambrosio
 */
public class Client implements Runnable {
    private static final int WAITING_FOR_SERVER_TIMEOUT_IN_SECONDS = 5;

    private static final int AUDIO_FILES_HASH_MAP_SIZE = 512;

    private PacketDispatcher packetDispatcher;

    private MulticastSocket multicastSocket = null;

    private InetAddress multicastGroupAddress;

    private char multicastGroupPort;

    private Socket serverSocket;

    private DataOutputStream serverStream;

    private InetAddress serverAddress;

    private char serverPort;

    /**
     * Istanza del gestore della playlist di riproduzione brani
     */
    private ClientPlaylistStreamingManager clientPlaylistStreamingManager = null;

    /**
     * Istanza del thread che manda in streaming un brano audio
     */
    private AudioPacketStreamWriter audioPacketStreamWriter = null;

    /**
     * Istanza del thread che tenta di ricevere l'indirizzo del server dal
     * gruppo multicast
     */
    private Thread connector;

    /**
     * Istanza del thread in esecuzione per questo task
     */
    private Thread runningThread = null;

    /**
     * Flag: true se ha ricevuto un pacchetto HELO.
     */
    private boolean connected = false;

    /**
     * Flag: false se il client è chiuso o in chiusura
     */
    private boolean alive = true;

    /**
     * Percorso dei file audio resi disponibili da questo client
     */
    private String audioPath;

    /**
     * L'insieme dei brani resi disponibili da questo client (memorizzate come
     * associazioni
     */
    private final ConcurrentHashMap<Integer, ClientAudioFile> audioFiles = new ConcurrentHashMap<>(AUDIO_FILES_HASH_MAP_SIZE);

    /**
     * Contatore del numero di file audio resi disponibili {@link #audioFiles}
     */
    private int numAudioFiles = 0;

    /**
     * Costruttore.
     *
     * @param strGroupAddress Indirizzo del gruppo multicast a cui collegarsi
     * @param multicastGroupPort       Porta del gruppo multicast a cui collegarsi
     * @param audioPath       Percorso dei file audio da rendere disponibili al gruppo
     * @throws IOException
     */
    public Client(final String strGroupAddress, final char multicastGroupPort, final String audioPath)
            throws IOException {
        super();
        File aPath = new File(audioPath);
        if (!aPath.isDirectory()) {
            throw new IOException(audioPath
                    + " is not a directory");
        }
        if (!aPath.canRead()) {
            throw new IOException(audioPath
                    + " can't be read");
        }
        this.multicastGroupAddress = InetAddress.getByName(strGroupAddress);
        this.multicastGroupPort = multicastGroupPort;
        this.audioPath = audioPath;
    }

    /**
     * Tenta di connettersi al grouppo multicast del gd.choir mandando un
     * pacchetto join, ed attendendo la risposta hello. Se la risposta non
     * giunge in tempo, il metodo restituisce false. Il metodo genera un thread
     * che tenta di connettersi ad intervalli regolari
     *
     * @return true in caso di riuscita connessione
     * @throws IOException
     */
    public final boolean connect() throws IOException {
        if (isConnected()) {
            return true;
        }
        if (multicastSocket == null) {
            System.err.printf(
                    "Trying to connect to multicast group: %s:%d",
                    multicastGroupAddress,
                    (int) multicastGroupPort
            );
            System.err.println();
            multicastSocket = new MulticastSocket(multicastGroupPort);
            multicastSocket.joinGroup(multicastGroupAddress);
            multicastSocket.setSoTimeout(1000);
        }
        connector = new Thread(new Runnable() {
            public void run() {
                while (!Client.this.isConnected() && Client.this.alive) {
                    sendJoinMessage();
                    receiveHelloMessage();
                }
            }

            private void receiveHelloMessage() {
                PacketHello pktHello;
                DatagramPacket dp;
                try {

                    dp = new DatagramPacket(new byte[Packet.MAX_PACKET_PAYLOAD_SIZE], Packet.MAX_PACKET_PAYLOAD_SIZE);

                    Client.this.multicastSocket.receive(dp);

                    pktHello = new PacketHello(dp);
                    Client.this.serverAddress = pktHello.serverAddress;
                    Client.this.serverPort = pktHello.serverPort;
                    Client.this.setConnected(true);
                    System.err.printf(
                            "ServerMain answered from %s:%d",
                            serverAddress,
                            (int) serverPort
                    );
                    System.err.println();
                } catch (UnexpectedPacketException e) {
                    // Packet is not the expected Hello
                } catch (SocketTimeoutException e) {
                    // Packet did not arrive
                } catch (IOException e) {
                    // Error while listening: bail out
                    e.printStackTrace();
                    alive = false;
                }
            }

            private void sendJoinMessage() {
                PacketJoin pktJoin;

                if (isTimestampAtLeastOneSecondOld(lastJoinMessageSentTimestamp)) {
                    try {
                        pktJoin = new PacketJoin(multicastGroupAddress, multicastGroupPort);
                        Client.this.multicastSocket.send(pktJoin.getRawPacket());
                        System.err.println("join request sent");
                        lastJoinMessageSentTimestamp = Calendar.getInstance().getTimeInMillis();
                    } catch (IOException e) {
                        // Error while listening: bail out
                        e.printStackTrace();
                        alive = false;
                    }
                }
            }

            private boolean isTimestampAtLeastOneSecondOld(long lastTimestamp) {
                return (Calendar.getInstance().getTimeInMillis() - lastTimestamp) >= 1000;
            }

            private long lastJoinMessageSentTimestamp = 0L;
        });
        connector.start();
        try {
            connector.join(WAITING_FOR_SERVER_TIMEOUT_IN_SECONDS * 1000, 0);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return isConnected();
    }

    /**
     * Ciclo principale del client. Riceve pacchetti di tipo play dal server, e
     * genera un'istanza della classe AudioPacketStreamWriter per riprodurne il
     * contenuto verso il gruppo multicast.
     */
    @Override
    public final void run() {
        if (isConnected()) {
            PacketPlay pktPlay;
            ClientAudioFile audioFile;

            while (alive) {
                try {
                    pktPlay = new PacketPlay();
                    pktPlay.fromStream(new DataInputStream(serverSocket.getInputStream()));
                    if ((audioFile = getAudioFile(pktPlay.musicId)) != null) {
                        if (audioPacketStreamWriter != null && audioPacketStreamWriter.isAlive()) {
                            audioPacketStreamWriter.stop();
                        }
                        audioPacketStreamWriter = new AudioPacketStreamWriter(multicastGroupAddress, multicastGroupPort, audioFile, getPacketDispatcher());
                        audioPacketStreamWriter.start();
                    } else {
                        System.err.println("Stale audio file requested: "
                                + pktPlay.musicId);
                    }
                } catch (UnexpectedPacketException e) {
                    System.err.println("Unexpected server message");
                    alive = false;
                } catch (SocketException e) {
                    alive = false;
                    System.err.println("Lost server connection");
                } catch (IOException e) {
                    e.printStackTrace();
                    alive = false;
                }
            }
            try {
                stop();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Inserisce un audio file nella collezione condivisa.
     *
     * @return false in caso di fallimento (il file era già stato incluso)
     */
    public final boolean putAudioFile(final ClientAudioFile audioFile) {
        synchronized (audioFiles) {
            if (audioFiles.containsKey(audioFile.hashCode())) {
                System.err.println("File duplicato: "
                        + audioFile.getMusicTitle());
                return false;
            }
            audioFiles.put(audioFile.hashCode(), audioFile);
            numAudioFiles++;
            return true;
        }
    }

    public final void removeAudioFile(final ClientAudioFile audioFile) {
        synchronized (audioFiles) {
            audioFiles.remove(audioFile.hashCode());
            numAudioFiles--;
        }
    }

    /**
     * Spedisce al server un pacchetto che notifica la disponibilità di un file
     * audio.
     *
     * @param audioFile file
     *                  audio
     * @throws IOException
     */
    public final void notifyNewAudioFile(final ClientAudioFile audioFile)
            throws IOException {
        PacketMusic pm = new PacketMusic(audioFile);
        synchronized (serverStream) {
            pm.toStream(serverStream);
        }
    }

    /**
     * Restituisce l'audiofile associato ad un id dato.
     *
     * @param musicId id del brano richiesto.
     * @return audiofile associato
     */
    public final ClientAudioFile getAudioFile(final char musicId) {
        synchronized (audioFiles) {
            return audioFiles.get((int) musicId);
        }
    }

    /**
     * @return The number of available audio files
     */
    public final int getNumAudioFiles() {
        synchronized (audioFiles) {
            return numAudioFiles;
        }
    }

    /**
     * Lancia il client in piena esecuzione
     *
     * @throws IOException
     */
    public final void start() throws IOException {
        // Se non era già connesso, tenta di nuovo di connettersi
        if (!connect()) {
            // Se fallisce significa che qualcosa non va con il server che gira
            // su questa
            // stessa macchina, ed esce. In teoria il client potrebbe comunque
            // eseguire i dati audio in arrivo, senza spedire nulla al gruppo
            // multicast
            // ma per equità, questo client decide che, se non dà, non vuole
            // neanche ricevere.
            alive = false;
            return;
        }
        if (packetDispatcher == null) {
            packetDispatcher = new PacketDispatcher(multicastGroupAddress, multicastGroupPort, multicastSocket);
        }

        // Tenta di connettersi con socket tcp al server
        System.out.printf(
                "Trying to connect to server at %s:%d",
                serverAddress,
                (int) serverPort
        );
        System.out.println();
        serverSocket = new Socket(serverAddress, serverPort);
        serverStream = new DataOutputStream(serverSocket.getOutputStream());

        runningThread = new Thread(this);

        DirectoryScanner dirScanner = new DirectoryScanner(this, new File(audioPath));

        Thread dirScannerThread = new Thread(dirScanner);
        dirScannerThread.setPriority(Thread.MIN_PRIORITY);

        clientPlaylistStreamingManager = new ClientPlaylistStreamingManager(getPacketDispatcher());

        runningThread.start();

        packetDispatcher.start();

        dirScannerThread.start();
    }

    public final void setConnected(boolean isConnected) {
        connected = isConnected;
    }

    public final boolean isConnected() {
        return connected;
    }

    public final void stop() throws InterruptedException {
        if (alive && runningThread != null && runningThread.isAlive()) {
            runningThread.join();
        }
        alive = false;
        if (connector != null && connector.isAlive()) {
            connector.join();
        }
        if (clientPlaylistStreamingManager != null) {
            clientPlaylistStreamingManager.stop();
        }
        if (audioPacketStreamWriter != null) {
            audioPacketStreamWriter.stop();
        }
        if (packetDispatcher != null) {
            packetDispatcher.stopNow();
        }
    }

    public final void setPacketDispatcher(final PacketDispatcher packetDispatcher) {
        this.packetDispatcher = packetDispatcher;
    }

    public final PacketDispatcher getPacketDispatcher() {
        return packetDispatcher;
    }

    public final ClientPlaylistStreamingManager getPlaylistStreamingManager() {
        return clientPlaylistStreamingManager;
    }

    public final Thread getRunningThread() {
        return runningThread;
    }
}
