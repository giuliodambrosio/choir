/**
 *
 */
package gd.choir.server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Calendar;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

import gd.choir.client.Client;
import gd.choir.common.IncomingPacketDispatcher;
import gd.choir.common.JoinPacketListener;
import gd.choir.proto.packets.PacketHello;
import gd.choir.proto.packets.PacketJoin;

/**
 * Thread principale del server. Ascolta il socket multicast in attesa di
 * pacchetti di tipo join, e mantiene riferimenti a tutti i componenti del
 * server.
 *
 * @author Giulio D'Ambrosio
 */
public class Server implements JoinPacketListener, Runnable {
    /**
     * Massima frequenza di spedizione pacchetti di tipo hello in risposta a
     * pacchetti join provenienti dallo stesso client.
     */
    private static final long MAX_HELLO_PACKET_FREQ = 1000L;

    /**
     * Timeout del socket tcp.
     */
    private static final int SSO_TIMEOUT = 5;

    /**
     * Flag: se false, il server è chiuso o in chiusura.
     */
    private boolean alive = true;

    /**
     * Thread principale del server.
     */
    private Thread runningThread = null;

    /**
     * Socket tcp del server.
     */
    private ServerSocket sso = null;

    /**
     * Indirizzo del gruppo multicast.
     */
    private InetAddress groupAddress;

    /**
     * Porta del gruppo multicast.
     */
    private char groupPort;

    /**
     * Gestore delle comunicazioni da e verso il gruppo multicast.
     */
    private IncomingPacketDispatcher demu = null;

    /**
     * Gestore della riproduzione da parte dei client.
     */
    private ServerPlayer playerServer = null;

    /**
     * Lista dei client connessi.
     */
    private Vector<ServerClientHandler> clients = null;

    /**
     * Ultimo client scelto per l'esecuzione di un brano.
     */
    private ServerClientHandler lastChosenClient = null;

    /**
     * Mappa dei timestamp delle ultime richieste di pacchetti Hello da parte
     * dei client.
     */
    private ConcurrentHashMap<InetAddress, Long> helloed = null;

    /**
     * Istanza del client in esecuzione su questo host.
     */
    private Client client;

    /**
     * Porta tcp su cui rimane in ascolto il socket tcp.
     */
    private char serverTcpPort;

    /**
     * Costruttore
     *
     * @throws IOException
     */
    public Server(final String groupAddress, final char groupPort,
                  final char serverTcpPort, final Client client) throws IOException {
        super();

        this.groupAddress = InetAddress.getByName(groupAddress);
        this.groupPort = groupPort;
        this.serverTcpPort = serverTcpPort;

        sso = new ServerSocket(serverTcpPort, 10, InetAddress.getLocalHost());
        sso.setSoTimeout(SSO_TIMEOUT);

        this.client = client;
        if ((demu = client.getDemultiplexer()) == null) {
            demu = new IncomingPacketDispatcher(this.groupAddress, groupPort);
            client.setDemultiplexer(demu);
        }
        if (demu.getRunningThread() == null) {
            demu.setRunningThread(new Thread(demu));
        }

        clients = new Vector<>();
        helloed = new ConcurrentHashMap<>();
    }

    /**
     * @return Restituisce l'istanza del {@link #playerServer}
     */
    public final ServerPlayer getPlayerServer() {
        return playerServer;
    }

    /**
     * Inizializza i task ed i thread principali del server e lancia il thread
     * principale (questo).
     *
     * @throws IOException
     */
    public final void start() throws IOException {
        demu.registerListener(this);
        playerServer = new ServerPlayer(groupAddress, groupPort, this);

        runningThread = new Thread(this);
        playerServer.setRunningThread(new Thread(playerServer));

        runningThread.start();
        playerServer.getRunningThread().start();

        demu.getRunningThread().start();
    }

    /**
     * Chiude il server, fermando ciascun task di controllo dei singoli client
     * connessi.
     *
     * @throws InterruptedException
     */
    public final synchronized void stop() throws InterruptedException {
        alive = false;
        demu.stop();
        for (ServerClientHandler c : clients) {
            stopClient(c);
        }
    }

    /**
     * Ciclo principale. Attende pacchetti di tipo join e risponde con pacchetti
     * di tipo hello, contenenti indirizzo e porta del socket tcp dell'istanza
     * JBServerAcceptor.
     */
    public final void packetArrived(PacketJoin p) {
        InetAddress ca;

        try {
            /*
             * risponde con un pacchetto hello con l'indirizzo del socket tcp
			 * usato dal Thread JBControlServer
			 */
            if (hasBeenHelloed(ca = p.getRawPacket().getAddress(), MAX_HELLO_PACKET_FREQ)) {
                /*
				 * Manda il pacchetto helo, solamente se questa è la prima
				 * richiesta join da parte di questo client, o se è passato un
				 * secondo dall'ultima richiesta
				 */
                sayHello(groupAddress, groupPort);
                clientHelloed(ca);
                System.err.println("[Server] Spedito pacchetto HELO in risposta a pacchetto JOIN da "
                        + ca + "[" + p.getRawPacket().getPort() + "]");
            } else {
                System.err.println("[Server] Ignorato pacchetto join da " + ca
                        + "[" + p.getRawPacket().getPort() + "]");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Verifica se un client ha ottenuto in risposta un pacchetto HELO da più di
     * un certo numero di millisecondi.
     */
    public final boolean hasBeenHelloed(final InetAddress clientAddress,
                                        final long byMoreThanMillis) {
        Long lts;
        synchronized (helloed) {
            return ((lts = helloed.get(clientAddress)) == null
                    || ((Calendar.getInstance().getTimeInMillis() - lts.longValue()) > byMoreThanMillis));
        }
    }

    /**
     * Memorizza l'indirizzo del client a cui è stato risposto con pacchetto
     * HELO ed associa il timestamp attuale al suo indirizzo.
     *
     * @param clientAddress Indirizzo del client da controllare
     */
    private void clientHelloed(final InetAddress clientAddress) {
        synchronized (helloed) {
            if (helloed.containsKey(clientAddress)) {
                helloed.remove(clientAddress);
            }
            helloed.put(clientAddress, Calendar.getInstance().getTimeInMillis());
        }
    }

    /**
     * Aggiunge un client alla lista dei client connessi, e ne lancia il thread
     * principale.
     */
    public final synchronized void startClient(final ServerClientHandler client) {
        clients.add(client);
        client.setRunningThread(new Thread(client));
        client.getRunningThread().start();
    }

    /**
     * Ferma il thread di un client.
     *
     * @throws InterruptedException
     */
    private synchronized void stopClient(final ServerClientHandler client)
            throws InterruptedException {
        clients.remove(client);
        client.stop();
        if (alive && clients.size() == 0) {
            // Quando termina l'ultimo client, il server chiude anch'esso
            stop();
        }
    }

    /**
     * @return il client che gira su questo host
     */
    public final Client getLocalClient() {
        return client;
    }

    /**
     * @return the demu
     */
    public final IncomingPacketDispatcher getDemultiplexer() {
        return demu;
    }

    /**
     * Compila un pacchetto di tipo hello da spedire ad un client che ha inviato
     * un pacchetto join.
     *
     * @throws IOException
     */
    public final void sayHello(final InetAddress groupAddress, final char groupPort)
            throws IOException {
        demu.send(new PacketHello(sso.getInetAddress(), (char) sso.getLocalPort(), groupAddress, groupPort));
    }

    /**
     * Seleziona un client a caso per lo streaming di un brano. Viene fatto un
     * tentativo per evitare di scegliere più volte di seguito lo stesso client.
     *
     * @return un client scelto a caso
     */
    public final ServerClientHandler pickClient() {
        if (clients.size() == 0) {
            return null;
        }
        ServerClientHandler res;
		/*
		 * Se è stato scelto l'ultimo client selezionato per la riproduzione ed
		 * è disponibile più di un client, viene scelto il client successivo a
		 * quello "sorteggiato"
		 */
        int i = (int) Math.round(Math.random() * (clients.size() - 1));
        if ((res = clients.elementAt(i)) == lastChosenClient
                && clients.size() > 1) {
            res = clients.elementAt((++i) % clients.size());
        }
        return (lastChosenClient = res);
    }

    public final void setRunningThread(final Thread runningThread) {
        this.runningThread = runningThread;
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
                newcon = sso.accept();
                newcli = new ServerClientHandler(newcon, getPlayerServer());
                startClient(newcli);
            } catch (SocketTimeoutException e) {
                // nessun pacchetto, continua per verificare se il thread va
                // chiuso
            } catch (IOException e) {
                e.printStackTrace();

                // Tenta di ricreare il socket
                try {
                    sso = new ServerSocket(serverTcpPort, 10, InetAddress.getLocalHost());
                    sso.setSoTimeout(SSO_TIMEOUT);
                } catch (UnknownHostException e1) {
                    alive = false;
                } catch (IOException e1) {
                    alive = false;
                }
            }
        }
    }

}
