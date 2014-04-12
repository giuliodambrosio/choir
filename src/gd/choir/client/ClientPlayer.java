/**
 *
 */
package gd.choir.client;

import java.io.IOException;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import gd.choir.common.IncomingPacketDispatcher;
import gd.choir.common.AudioBeginPacketListener;
import gd.choir.proto.packets.audio.PacketBegin;

/**
 * Gestore della playlist di riproduzione brani. Si occupa di riprodurre un file
 * audio in streaming proveniente dal gruppo multicast. Per assicurarsi che
 * venga riprodotto solamente un brano alla volta e per ragioni di efficienza,
 * viene utilizzato un pool di thread contenente un solo thread, a cui viene
 * passato di volta in volta il nuovo task di riproduzione AudioPlayer.
 *
 * @author Giulio D'Ambrosio
 */
public class ClientPlayer implements AudioBeginPacketListener {
    /**
     * Gestore del gruppo multicast.
     */
    private IncomingPacketDispatcher demu;

    /**
     * Pool dell'esecuzione playlist.
     */
    private ExecutorService execPlayers;

    /**
     * Coda dei brani da riprodurre.
     */
    private Vector<Integer> backlogPlaylist;

    /**
     * Brani disponibili.
     */
    private ConcurrentHashMap<Integer, AudioPlayer> players;

    /**
     * Costruttore.
     *
     * @param demu Istanza del gestore del gruppo multicast
     * @throws IOException
     */
    public ClientPlayer(final IncomingPacketDispatcher demu)
            throws IOException {
        super();
        this.demu = demu;
        this.demu.registerListener(this);
        players = new ConcurrentHashMap<>();
        execPlayers = Executors.newFixedThreadPool(1);
        backlogPlaylist = new Vector<>();
    }

    /**
     * @param demu the demu to set
     */
    public void setIncomingPacketDispatcher(IncomingPacketDispatcher demu) {
        this.demu = demu;
    }

    /**
     * @return the demu
     */
    public IncomingPacketDispatcher getIncomingPacketDispatcher() {
        return demu;
    }

    /**
     * Riproduce il brano audio in arrivo dal socket multicast.
     *
     * @param jbdp Il pacchetto arrivato
     */
    @Override
    public final void packetArrived(final PacketBegin jbdp) {
        AudioPlayer pl;
        Integer plid;
        try {
            synchronized (backlogPlaylist) {
                plid = (int) jbdp.musicId;
                pl = new AudioPlayer(jbdp.musicId, jbdp.musicTitle, this);
                backlogPlaylist.add(plid);
                players.put(plid, pl);
                execPlayers.execute(pl);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Termina gentilmente l'esecuzione, evitando di gestire nuove richieste di
     * riproduzione, ma completando l'eventuale riproduzione in corso.
     */
    public final void stop() {
        demu.unregisterListener(this);
        execPlayers.shutdown();
    }

    /**
     * Interrompe la riproduzione di un brano.
     */
    public final void stop(char musicId) {
        Integer plid = (int) musicId;
        AudioPlayer pl;
        synchronized (backlogPlaylist) {
            while (backlogPlaylist.contains(plid)) {
                pl = players.get(plid);
                try {
                    pl.stop();
                    backlogPlaylist.wait(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Attende che sia terminata la riproduzione di un brano.
     */
    public final void waitForEndOf(final char syncMusicId) {
        Integer plid = (int) syncMusicId;
        synchronized (backlogPlaylist) {
            while (backlogPlaylist.contains(plid)) {
                try {
                    backlogPlaylist.wait();
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }

    /**
     * Rimuove un riproduttore di un brano musicale aggiunto alla lista dei brani
     * da riprodurre. Il metodo sblocca eventuali thread in attesa sul metodo
     * {@link #waitForEndOf(char)}
     *
     * @param pl Task di riproduzione di cui attendere la fine
     */
    public final void notifyEndOf(final AudioPlayer pl) {
        Integer plid = (int) pl.getCurrentlyPlayingMusicId();
        synchronized (backlogPlaylist) {
            players.remove(plid);
            backlogPlaylist.remove(plid);
            backlogPlaylist.notifyAll();
        }
    }

}
