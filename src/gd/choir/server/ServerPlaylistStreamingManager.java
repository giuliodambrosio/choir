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
import gd.choir.common.PacketDispatcher;
import gd.choir.data.packet.datagram.audio.PacketBegin;
import gd.choir.data.packet.datagram.audio.PacketDataChunk;
import gd.choir.data.packet.datagram.audio.PacketEnd;

/**
 * @author Giulio D'Ambrosio
 */
public class ServerPlaylistStreamingManager implements AudioBeginPacketListener,
        AudioEndPacketListener, AudioDataPacketListener, Runnable {

    private static final int PAUSE_BETWEEN_REPRODUCTIONS_IN_SECONDS = 2;

    private static final int MAXIMUM_CLIENT_DELAY_TIMEOUT_MILLISECONDS = 1500;

    private boolean alive = true;

    private ServerAudioFile currentlyStreamingAudioFile = null;

    private long lastReceivedAudioPacketTimestamp = 0;

    private InetAddress multicastGroupInetAddress;

    private char multicastGroupPort;

    private PacketDispatcher packetDispatcher;

    private ServerMain mainServer;

    private Thread runningThread = null;

    public ServerPlaylistStreamingManager(final InetAddress multicastGroupInetAddress, final char multicastGroupPort,
                                          final ServerMain mainServer) throws IOException {
        this.multicastGroupInetAddress = multicastGroupInetAddress;
        this.multicastGroupPort = multicastGroupPort;
        this.mainServer = mainServer;
        packetDispatcher = mainServer.getDemultiplexer();
        packetDispatcher.registerListener((AudioBeginPacketListener) this);
        packetDispatcher.registerListener((AudioDataPacketListener) this);
        packetDispatcher.registerListener((AudioEndPacketListener) this);
    }

    /**
     * @return Il thread associato a questa istanza
     */
    public final Thread getRunningThread() {
        return runningThread;
    }

    public void setRunningThread(Thread runningThread) {
        this.runningThread = runningThread;
    }

    /**
     * This method is called by PacketDispatcher when an audio file begins the streaming and the packet
     * marking this event is received
     */
    @Override
    public final void packetArrived(final PacketBegin packet) {
        if (currentlyStreamingAudioFile != null && currentlyStreamingAudioFile.getMusicId() == packet.musicId) {
            currentlyStreamingAudioFile.setBeingStreamed();
            lastReceivedAudioPacketTimestamp = Calendar.getInstance().getTimeInMillis();
        }
    }

    /**
     * This method is called by the PacketDispatcher when an audio packet is received.
     * The server is interested in this kind of packets, for it's its responsibility to check
     * that the interval between packets is maintained below the maximum delay allowed of
     * {@link ServerPlaylistStreamingManager#MAXIMUM_CLIENT_DELAY_TIMEOUT_MILLISECONDS}
     * seconds
     * @param packet Il pacchetto arrivato
     */
    @Override
    public final void packetArrived(final PacketDataChunk packet) {
        if (currentlyStreamingAudioFile != null && currentlyStreamingAudioFile.getMusicId() == packet.musicId) {
            lastReceivedAudioPacketTimestamp = Calendar.getInstance().getTimeInMillis();
        }
    }

    /**
     * This method is called by the PacketDispatcher when a packet that marks
     * the end of the currently streamed audio file is received.
     */
    @Override
    public final void packetArrived(final PacketEnd packet) {
        synchronized (this) {
            if (currentlyStreamingAudioFile != null && currentlyStreamingAudioFile.getMusicId() == packet.musicId) {
                System.out.println("[ServerMain] client from "
                        + currentlyStreamingAudioFile.getOwnerClientHandler().getAddressAsString()
                        + " has finished streaming "
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
     * Main cycle:
     * - Picks an audio file to be streamed
     * - Asks the owner client to start streaming it
     * - Follows the streaming checking that the client is not lagging too much (i.e.: no packets received for
     *   {@link ServerPlaylistStreamingManager#MAXIMUM_CLIENT_DELAY_TIMEOUT_MILLISECONDS} seconds)
     * - If the client is lagging too much, interrupts the streaming by sending the packet
     *   {@link gd.choir.data.packet.datagram.audio.PacketEnd} that marks the end of the streaming for the current audio file
     */
    @Override
    public final void run() {
        char lastMusicId = (char) -1;

        while (alive) {
            try {
                followCurrentAudioFileStreaming(lastMusicId);
                pauseBetweenAudioFiles();
                lastMusicId = manageNextAudioFileSelection();
            } catch (InterruptedException e) {
                alive = false;
            } catch (Exception e) {
                System.err.printf(
                        "Internal error occurred: %s",
                        e.getMessage()
                );
                System.err.println();
                alive = false;
            }
        }
    }

    private void pauseBetweenAudioFiles() throws InterruptedException {
        System.out.printf(
                "[ServerMain] Pausing before next file for: %d seconds ",
                PAUSE_BETWEEN_REPRODUCTIONS_IN_SECONDS
        );
        System.err.println();
        Thread.sleep(PAUSE_BETWEEN_REPRODUCTIONS_IN_SECONDS * 1000);
    }

    private synchronized void followCurrentAudioFileStreaming(char lastMusicId) throws InterruptedException, IOException {
        if ((lastMusicId != (char) -1)) {
            lastReceivedAudioPacketTimestamp = Calendar.getInstance().getTimeInMillis();
            while (currentlyStreamingAudioFile != null) {
                wait(1000);
                if (currentlyStreamingAudioFile != null
                        && Calendar.getInstance().getTimeInMillis()
                        - lastReceivedAudioPacketTimestamp > MAXIMUM_CLIENT_DELAY_TIMEOUT_MILLISECONDS) {
                    System.out.printf(
                            "[ServerMain] client %s is lagging while streaming %s. Interrupting by Sending an end of streaming packet.",
                            currentlyStreamingAudioFile.getOwnerClientHandler().getAddressAsString(),
                            currentlyStreamingAudioFile.getMusicTitle()
                    );
                    System.err.println();
                    packetDispatcher.send(new PacketEnd(currentlyStreamingAudioFile.getMusicId(), multicastGroupInetAddress, multicastGroupPort));
                    mainServer.getLocalClient().getPlaylistStreamingManager().stop(currentlyStreamingAudioFile.getMusicId());
                }
            }
            // Waiting for the local audio player to terminate...
            System.err.println("[ServerMain] waiting for audio player to complete streaming...");
            mainServer.getLocalClient().getPlaylistStreamingManager().waitForEndOf(lastMusicId);
        }
    }

    private synchronized char manageNextAudioFileSelection() throws Exception {
        char result = (char) -1;
        while (alive && currentlyStreamingAudioFile == null) {
            currentlyStreamingAudioFile = pickRandomAudioFile();
            if (currentlyStreamingAudioFile != null) {
                currentlyStreamingAudioFile.requestClientForAudioStreaming();
                continue;
            }
            wait(2000);
        }

        if (!alive) {
            return result;
        }

        result = currentlyStreamingAudioFile.getMusicId();
        lastReceivedAudioPacketTimestamp = Calendar.getInstance().getTimeInMillis();
        System.out.printf(
                "[ServerMain] Next audio file : '%s' from client at %s",
                currentlyStreamingAudioFile.getMusicTitle(),
                currentlyStreamingAudioFile.getOwnerClientHandler().getAddressAsString()
        );
        System.err.println();

        return result;
    }


    /**
     * Chooses pseudo-randomly an audio file to be streamed.
     * A random {@link ServerClientHandler} is chosen by asking the {@link ServerMain} to perform
     * the random choice.
     * The {@link ServerClientHandler} is then asked to perform a random choice of an audio file
     * to be streamed.
     */
    private ServerAudioFile pickRandomAudioFile() throws IOException {
        ServerClientHandler clientHandler = mainServer.pickRandomClient();
        return clientHandler != null ? clientHandler.pickRandomAudioFile() : null;
    }
}
