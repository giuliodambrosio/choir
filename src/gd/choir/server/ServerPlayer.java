/**
 *
 */
package gd.choir.server;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Calendar;

import gd.choir.common.AudioBeginPacketListener;
import gd.choir.common.AudioDataPacketListener;
import gd.choir.common.AudioEndPacketListener;
import gd.choir.common.IncomingPacketDispatcher;
import gd.choir.proto.packets.audio.PacketBegin;
import gd.choir.proto.packets.audio.PacketData;
import gd.choir.proto.packets.audio.PacketEnd;

/**
 * Manages
 *
 * @author Giulio D'Ambrosio
 */
public class ServerPlayer implements AudioBeginPacketListener,
        AudioEndPacketListener, AudioDataPacketListener, Runnable {
    /**
     * Pause between two reproductions
     */
    private static final int PAUSE_BETWEEN_REPRODUCTIONS_IN_SECONDS = 2;

    /**
     * Maximum allowed delay before interrupting current client reproduction
     */
    private static final int MAXIMUM_CLIENT_DELAY_TIMEOUT = 1500;

    private boolean alive = true;

    private ServerAudioFile currentlyStreamingAudioFile = null;

    private long lastReceivedAudioPacketTimestamp = 0;

    private InetAddress multicastGroupInetAddress;

    private char multicastGroupPort;

    /**
     * Gestore della porta multicast.
     */
    private IncomingPacketDispatcher multicastDemultiplexer;

    private Server mainServer;

    /**
     * Istanza del thread in esecuzione per questo task.
     */
    private Thread runningThread = null;

    /**
     * Costruttore.
     *
     * @throws IOException
     */
    public ServerPlayer(final InetAddress multicastGroupInetAddress, final char multicastGroupPort,
                        final Server mainServer) throws IOException {
        this.multicastGroupInetAddress = multicastGroupInetAddress;
        this.multicastGroupPort = multicastGroupPort;
        this.mainServer = mainServer;
        multicastDemultiplexer = mainServer.getDemultiplexer();
        multicastDemultiplexer.registerListener((AudioBeginPacketListener) this);
        multicastDemultiplexer.registerListener((AudioDataPacketListener) this);
        multicastDemultiplexer.registerListener((AudioEndPacketListener) this);
    }

    /**
     * Scelta del file audio da mandare in streaming. Viene prima scelto pseudo
     * casualmente un client, quindi viene scelto pseudo casualmente un brano
     * audio. La scelta viene delegata prima al server principale
     * {@link Server}, per la selezione di un client, poi alla relativo thread
     * {@link ServerClientHandler} per la scelta del brano.
     *
     * @return l'oggetto scelto
     * @throws IOException
     */
    private ServerAudioFile pickAudioFile() throws IOException {
        ServerClientHandler cli = mainServer.pickClient();
        return cli != null ? cli.pickAudioFile() : null;
    }

    /**
     * @param runningThread Il thread da associare a questa istanza
     */
    public final void setRunningThread(final Thread runningThread) {
        this.runningThread = runningThread;
    }

    /**
     * @return Il thread associato a questa istanza
     */
    public final Thread getRunningThread() {
        return runningThread;
    }

    /**
     * Segnalazione dell'inizio dello streaming di un file audio. Il file audio
     * viene marcato come effettivamente in streaming, e viene salvato il
     * timestamp per controllare che i pacchetti arrivino regolarmente. Il
     * controllo viene effettuato nel metodo run.
     *
     * @param jbdp Il pacchetto arrivato
     */
    @Override
    public final void packetArrived(final PacketBegin jbdp) {
        if (currentlyStreamingAudioFile != null && currentlyStreamingAudioFile.getMusicId() == jbdp.musicId) {
            currentlyStreamingAudioFile.setBeingStreamed();
            lastReceivedAudioPacketTimestamp = Calendar.getInstance().getTimeInMillis();
        }
    }

    /**
     * Segnalazione dell'arrivo di un pacchetto di dati di un brano in
     * streaming. Il metodo aggiorna solamente il timestamp dell'ultimo
     * pacchetto ricevuto: nel thread associato a questa classe, verr&agrave;
     * controllato che questo timestamp sia sempre abbastanza recente.
     *
     * @param jbdp Il pacchetto arrivato
     */
    @Override
    public final void packetArrived(final PacketData jbdp) {
        if (currentlyStreamingAudioFile != null && currentlyStreamingAudioFile.getMusicId() == jbdp.musicId) {
            lastReceivedAudioPacketTimestamp = Calendar.getInstance().getTimeInMillis();
        }
    }

    /**
     * Segnalazione del termine dello streaming Il metodo aggiorna anche il
     * timestamp dell'ultimo pacchetto ricevuto: nel thread associato a questa
     * classe, verrà controllato che questo timestamp sia sempre abbastanza
     * recente.
     *
     * @param jbdp Il pacchetto arrivato
     */
    @Override
    public final void packetArrived(final PacketEnd jbdp) {
        synchronized (this) {
            if (currentlyStreamingAudioFile != null && currentlyStreamingAudioFile.getMusicId() == jbdp.musicId) {
                System.out.println("[Server] il client "
                        + currentlyStreamingAudioFile.getOwner().getAddressAsString()
                        + " ha terminato lo streaming di "
                        + currentlyStreamingAudioFile.getMusicTitle());
                try {
                    currentlyStreamingAudioFile.streamingEnded();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                currentlyStreamingAudioFile = null;
                lastReceivedAudioPacketTimestamp = Calendar.getInstance().getTimeInMillis();
                notify();
            }
        }
    }

    /**
     * Ciclo principale : controlla la riproduzione dei brani. Viene scelto di
     * volta in volta il brano da riprodurre, viene quindi inviato un pacchetto
     * play al relativo client e viene controllato che questo effettui lo
     * streaming correttamente : se non arrivano pacchetti entro un timeout
     * configurabile {@link ServerPlayer#MAXIMUM_CLIENT_DELAY_TIMEOUT}, interrompe la
     * trasmissione tramite la spedizione di un pacchetto {@link gd.choir.proto.packets.audio.PacketEnd} al
     * gruppo multicast.
     */
    @Override
    public final void run() {
        char lastMusicId = (char) -1;

        while (alive) {
            try {

                // Attesa del termine del brano in corso di riproduzione
                synchronized (this) {
                    // Attesa del termine dello streaming
                    if ((lastMusicId != (char) -1)) {
                        lastReceivedAudioPacketTimestamp = Calendar.getInstance().getTimeInMillis();
                        while (currentlyStreamingAudioFile != null) {
                            wait(1000);
                            if (currentlyStreamingAudioFile != null
                                    && Calendar.getInstance().getTimeInMillis()
                                    - lastReceivedAudioPacketTimestamp > MAXIMUM_CLIENT_DELAY_TIMEOUT) {
                                // Il client ha tardato troppo a spedire dati:
                                // viene spedito il pacchetto che indica la fine
                                // dello streaming
                                System.out.println("[Server] il client "
                                        + currentlyStreamingAudioFile.getOwner().getAddressAsString()
                                        + " non sta spedendo i dati di "
                                        + currentlyStreamingAudioFile.getMusicTitle()
                                        + " : il brano viene interrotto");
                                multicastDemultiplexer.send(new PacketEnd(currentlyStreamingAudioFile.getMusicId(), multicastGroupInetAddress, multicastGroupPort));
                                mainServer.getLocalClient().getPlayer().stop(currentlyStreamingAudioFile.getMusicId());
                            }
                        }
                        // Attesa del termine del player locale
                        System.err.println("[Server] in attesa del termine della riproduzione...");
                        mainServer.getLocalClient().getPlayer().waitForEndOf(lastMusicId);
                    }
                }

                // Pausa fra un brano e l'altro
                System.out.println("[Server] Pausa prima del prossimo brano: "
                        + PAUSE_BETWEEN_REPRODUCTIONS_IN_SECONDS + " secondi");
                Thread.sleep(PAUSE_BETWEEN_REPRODUCTIONS_IN_SECONDS * 1000);

                // Attesa della disponibilità di un brano da riprodurre
                synchronized (this) {
                    while (alive && !((currentlyStreamingAudioFile = pickAudioFile()) != null && currentlyStreamingAudioFile.startStreaming())) {
                        wait(2000);
                    }
                    if (!alive) {
                        break;
                    }
                    // Imposta l'ultimo timestamp per il controllo sullo
                    // streaming da parte del client
                    lastMusicId = currentlyStreamingAudioFile.getMusicId();
                    lastReceivedAudioPacketTimestamp = Calendar.getInstance().getTimeInMillis();
                    System.out.println("[Server] Prossimo brano : "
                            + currentlyStreamingAudioFile.getMusicTitle() + " dal client "
                            + currentlyStreamingAudioFile.getOwner().getAddressAsString());
                }

            } catch (InterruptedException e) {
                alive = false;
            } catch (IOException e) {
                e.printStackTrace();
                alive = false;
            }
        }
    }

}
