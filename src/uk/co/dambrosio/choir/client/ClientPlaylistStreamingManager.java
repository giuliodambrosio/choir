/**
 *
 */
package uk.co.dambrosio.choir.client;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import uk.co.dambrosio.choir.common.PacketDispatcher;
import uk.co.dambrosio.choir.common.AudioBeginPacketListener;
import uk.co.dambrosio.choir.data.packet.datagram.audio.PacketBegin;

/**
 * Gestore della playlist di riproduzione brani. Si occupa di riprodurre un file
 * audio in streaming proveniente dal gruppo multicast. Per assicurarsi che
 * venga riprodotto solamente un brano alla volta e per ragioni di efficienza,
 * viene utilizzato un pool di thread contenente un solo thread, a cui viene
 * passato di volta in volta il nuovo task di riproduzione AudioPlayer.
 *
 * @author Giulio D'Ambrosio
 */
public class ClientPlaylistStreamingManager implements AudioBeginPacketListener {
    private PacketDispatcher packetDispatcher;

    private ExecutorService audioPlayerExecutors;

    private final ConcurrentLinkedQueue<Integer> playlist;

    /**
     * Brani disponibili.
     */
    private ConcurrentHashMap<Integer, AudioPlayer> players;

    /**
     * Costruttore.
     *
     * @param packetDispatcher Istanza del gestore del gruppo multicast
     * @throws IOException
     */
    public ClientPlaylistStreamingManager(final PacketDispatcher packetDispatcher)
            throws IOException {
        super();
        this.packetDispatcher = packetDispatcher;
        this.packetDispatcher.registerListener(this);
        players = new ConcurrentHashMap<>();
        audioPlayerExecutors = Executors.newFixedThreadPool(1);
        playlist = new ConcurrentLinkedQueue<>();
    }

    /**
     * @return the packetDispatcher
     */
    public PacketDispatcher getIncomingPacketDispatcher() {
        return packetDispatcher;
    }

    /**
     * Riproduce il brano audio in arrivo dal socket multicast.
     *
     * @param packet Il pacchetto arrivato
     */
    @Override
    public final void packetArrived(final PacketBegin packet) {
        AudioPlayer player;
        Integer musicId;
        try {
            synchronized (playlist) {
                musicId = (int) packet.musicId;
                player = new AudioPlayer(packet.musicId, packet.musicTitle, this);
                playlist.add(musicId);
                players.put(musicId, player);
                audioPlayerExecutors.execute(player);
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
        packetDispatcher.unregisterListener(this);
        audioPlayerExecutors.shutdown();
    }

    /**
     * Interrompe la riproduzione di un brano.
     */
    public final void stop(char musicId) {
        Integer plid = (int) musicId;
        AudioPlayer pl;
        synchronized (playlist) {
            while (playlist.contains(plid)) {
                pl = players.get(plid);
                try {
                    pl.stop();
                    playlist.wait(1000);
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
        Integer musicId = (int) syncMusicId;
        synchronized (playlist) {
            while (playlist.contains(musicId)) {
                try {
                    playlist.wait();
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
    public final void notifyEndOfAudioPlayer(final AudioPlayer pl) {
        Integer plid = (int) pl.getCurrentlyPlayingMusicId();
        synchronized (playlist) {
            players.remove(plid);
            playlist.remove(plid);
            playlist.notifyAll();
        }
    }
}
