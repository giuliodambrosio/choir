/**
 *
 */
package uk.co.dambrosio.choir.server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.concurrent.ConcurrentHashMap;

import uk.co.dambrosio.choir.client.Client;
import uk.co.dambrosio.choir.common.PacketDispatcher;
import uk.co.dambrosio.choir.common.JoinPacketListener;
import uk.co.dambrosio.choir.data.packet.datagram.DatagramPacket;
import uk.co.dambrosio.choir.data.packet.datagram.PacketHello;

/**
 * Thread principale del server. Ascolta il socket multicast in attesa di
 * pacchetti di tipo join, e mantiene riferimenti a tutti i componenti del
 * server.
 *
 * @author Giulio D'Ambrosio
 */
public class ServerMain implements JoinPacketListener, Runnable {
    /**
     * Maximum frequency in sending hello packets, replying to join packets
     * dealing with the same client
     */
    private static final long MAX_HELLO_PACKET_FREQ = 1000L;

    private static final int HEARTBEAT_INTERVAL_MILLISECONDS = 5 * 1000;

    /**
     * Flag: se false, il server è chiuso o in chiusura.
     */
    private boolean alive = true;

    /**
     * Thread principale del server.
     */
    private Thread runningThread;

    private ServerSocket serverSocket;

    private InetAddress multicastGroupAddress;

    private char multicastGroupPort;

    private PacketDispatcher packetDispatcher;

    /**
     * Gestore della riproduzione da parte dei client.
     */
    private ServerPlaylistStreamingManager playlistManager;

    /**
     * Lista dei client connessi.
     */
    private ArrayList<ServerClientHandler> clientHandlers;

    /**
     * Ultimo client scelto per l'esecuzione di un brano.
     */
    private ServerClientHandler lastChosenClient;

    private final ConcurrentHashMap<InetAddress, Long> lastHelloedClientTimestamps;

    private Client localClient;

    private char serverSocketPort;

    public ServerMain(final String multicastGroupAddress, final char multicastGroupPort,
                      final char serverSocketPort, final Client localClient) throws IOException {
        super();

        this.multicastGroupAddress = InetAddress.getByName(multicastGroupAddress);
        this.multicastGroupPort = multicastGroupPort;
        this.serverSocketPort = serverSocketPort;
        this.localClient = localClient;

        tryToCreateServerSocket();

        if ((packetDispatcher = localClient.getPacketDispatcher()) == null) {
            packetDispatcher = new PacketDispatcher(this.multicastGroupAddress, multicastGroupPort);
            localClient.setPacketDispatcher(packetDispatcher);
        }

        packetDispatcher.start();

        clientHandlers = new ArrayList<>();
        lastHelloedClientTimestamps = new ConcurrentHashMap<>();
    }


    public final ServerPlaylistStreamingManager getPlaylistManager() {
        return playlistManager;
    }

    /**
     * Starts all the threads
     * @throws IOException
     */
    public final void start() throws IOException {
        packetDispatcher.registerListener(this);
        playlistManager = new ServerPlaylistStreamingManager(multicastGroupAddress, multicastGroupPort, this);

        runningThread = new Thread(this);
        playlistManager.setRunningThread(new Thread(playlistManager));

        runningThread.start();
        playlistManager.getRunningThread().start();

        packetDispatcher.start();
    }

    /**
     * Closes all the running threads
     *
     * @throws InterruptedException
     */
    public final synchronized void stop() throws InterruptedException {
        alive = false;
        packetDispatcher.stop();
        for (ServerClientHandler c : clientHandlers) {
            stopClient(c);
        }
    }

    public final void packetArrived(DatagramPacket.PacketJoin packet) {
        InetAddress clientAddress;

        try {
            clientAddress = packet.getRawPacket().getAddress();

            if (hasBeenHelloedMoreThanMillisecondsAgo(clientAddress, MAX_HELLO_PACKET_FREQ)) {
                sayHello(multicastGroupAddress, multicastGroupPort);
                clientHelloed(clientAddress);
                System.err.printf(
                        "[ServerMain] Sent HELLO packet to client %s:%d",
                        clientAddress,
                        packet.getRawPacket().getPort()
                );
                System.err.println();
            } else {
                System.err.printf(
                        "[ServerMain] Client from %s:%d is sending too many join packets: ignoring this request...",
                        clientAddress,
                        packet.getRawPacket().getPort()
                );
                System.err.println();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Verifica se un client ha ottenuto in risposta un pacchetto HELO da più di
     * un certo numero di millisecondi.
     */
    public final boolean hasBeenHelloedMoreThanMillisecondsAgo(final InetAddress clientAddress,
                                                               final long milliseconds) {
        synchronized (lastHelloedClientTimestamps) {
            Long lastTimestamp = lastHelloedClientTimestamps.get(clientAddress);
            return (lastTimestamp == null
                    || ((Calendar.getInstance().getTimeInMillis() - lastTimestamp) > milliseconds));
        }
    }

    /**
     * Memorizza l'indirizzo del client a cui è stato risposto con pacchetto
     * HELO ed associa il timestamp attuale al suo indirizzo.
     *
     * @param clientAddress Indirizzo del client da controllare
     */
    private void clientHelloed(final InetAddress clientAddress) {
        synchronized (lastHelloedClientTimestamps) {
            Long lastTimestamp = Calendar.getInstance().getTimeInMillis();
            if (lastHelloedClientTimestamps.containsKey(clientAddress)) {
                lastHelloedClientTimestamps.remove(clientAddress);
            }
            lastHelloedClientTimestamps.put(clientAddress, lastTimestamp);
        }
    }

    /**
     * Aggiunge un client alla lista dei client connessi, e ne lancia il thread
     * principale.
     */
    public final synchronized void startClient(final ServerClientHandler client) {
        clientHandlers.add(client);
        client.start();
    }

    /**
     * Ferma il thread di un client.
     *
     * @throws InterruptedException
     */
    private synchronized void stopClient(final ServerClientHandler client)
            throws InterruptedException {
        clientHandlers.remove(client);
        client.stop();
        if (alive && clientHandlers.size() == 0) {
            // Quando termina l'ultimo client, il server chiude anch'esso
            stop();
        }
    }

    /**
     * @return il client che gira su questo host
     */
    public final Client getLocalClient() {
        return localClient;
    }

    /**
     * @return the packetDispatcher
     */
    public final PacketDispatcher getDemultiplexer() {
        return packetDispatcher;
    }

    /**
     * Compila un pacchetto di tipo hello da spedire ad un client che ha inviato
     * un pacchetto join.
     *
     * @throws IOException
     */
    public final void sayHello(final InetAddress groupAddress, final char groupPort)
            throws IOException {
        packetDispatcher.send(new PacketHello(serverSocket.getInetAddress(), (char) serverSocket.getLocalPort(), groupAddress, groupPort));
    }

    /**
     * Seleziona un client a caso per lo streaming di un brano. Viene fatto un
     * tentativo per evitare di scegliere più volte di seguito lo stesso client.
     *
     * @return un client scelto a caso
     */
    public final ServerClientHandler pickRandomClient() {
        if (clientHandlers.size() == 0) {
            return null;
        }
        ServerClientHandler res;
		/*
		 * Se è stato scelto l'ultimo client selezionato per la riproduzione ed
		 * è disponibile più di un client, viene scelto il client successivo a
		 * quello "sorteggiato"
		 */
        int i = (int) Math.round(Math.random() * (clientHandlers.size() - 1));
        if ((res = clientHandlers.get(i)) == lastChosenClient
                && clientHandlers.size() > 1) {
            res = clientHandlers.get((++i) % clientHandlers.size());
        }
        return (lastChosenClient = res);
    }

    public final Thread getRunningThread() {
        return runningThread;
    }

    /**
     * Ciclo principale.
     * Accetta connessioni sul socket tcp e lancia per ognuna una
     * nuova istanza di {@link ServerClientHandler}.
     */
    @Override
    public final void run() {
        while (alive) {
            Socket newcon;
            ServerClientHandler newcli;
            try {
                newcon = serverSocket.accept();
                newcli = new ServerClientHandler(newcon, getPlaylistManager());
                startClient(newcli);
            } catch (SocketTimeoutException e) {
                // Heartbeat check
            } catch (IOException e) {
                System.err.printf(
                        "Error while waiting for client connections: %s. Trying to reset the connection...",
                        e.getMessage()
                );
                System.err.println();
                tryToCreateServerSocket();
            }
        }
    }

    private void tryToCreateServerSocket() {
        try {
            serverSocket = new ServerSocket(serverSocketPort, 10, InetAddress.getLocalHost());
            serverSocket.setSoTimeout(HEARTBEAT_INTERVAL_MILLISECONDS);
        } catch (UnknownHostException e) {
            alive = false;
            System.err.printf(
                    "Could not create a server socket. Reason: %s",
                    e.getMessage()
            );
            System.err.println();
        } catch (IOException e) {
            alive = false;
            System.err.printf(
                    "Could not create a server socket. Reason: %s",
                    e.getMessage()
            );
            System.err.println();
        }
    }
}
