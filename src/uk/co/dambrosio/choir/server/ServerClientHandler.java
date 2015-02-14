/**
 *
 */
package uk.co.dambrosio.choir.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;

import uk.co.dambrosio.choir.data.packet.stream.PacketMusic;
import uk.co.dambrosio.choir.data.packet.stream.PacketPlay;
import uk.co.dambrosio.choir.data.packet.exceptions.UnexpectedPacketException;

/**
 * @author Giulio D'Ambrosio
 */
public class ServerClientHandler implements Runnable {

    private static final int HEARTBEAT_INTERVAL_MILLISECONDS = 4 * 1000;

    /**
     * Flag: se false il thread che esegue questa istanza è chiuso o in
     * chiusura.
     */
    private boolean alive = true;

    private InetAddress clientAddress;

    private int clientPort;

    private Socket socket;

    /**
     * Input stream derivato da {@link #socket}.
     */
    private DataInputStream dis;

    /**
     * Brano (eventualmente null) attualmente in streaming presso il client.
     */
    private ServerAudioFile streamingAudioFile;

    /**
     * Istanza del gestore della riproduzione dei brani dei client.
     */
    private final ServerPlaylistStreamingManager serverPlaylistManager;

    /**
     * Thread attualmente in esecuzione associato a questa istanza.
     */
    private Thread runningThread;

    private ArrayList<ServerAudioFile> availableAudioFiles;

    private ArrayList<ServerAudioFile> neverPlayedAudioFiles;

    /**
     * Thread collegato a singola socket tcp di singolo client.
     *
     * @param socket Socket tcp già connessa al client
     * @throws IOException
     */
    public ServerClientHandler(final Socket socket, ServerPlaylistStreamingManager serverPlaylistManager)
            throws IOException {
        this.socket = socket;
        this.serverPlaylistManager = serverPlaylistManager;
        clientAddress = socket.getInetAddress();
        clientPort = socket.getPort();
        dis = new DataInputStream(socket.getInputStream());
        socket.setSoTimeout(HEARTBEAT_INTERVAL_MILLISECONDS * 1000);
        availableAudioFiles = new ArrayList<>();
        neverPlayedAudioFiles = new ArrayList<>();
    }

    /**
     * @return Client internet address and port
     */
    public final String getAddressAsString() {
        return clientAddress + ":" + clientPort;
    }

    public final void requestClientForAudioStreaming(final ServerAudioFile audioFile)
            throws Exception {
        PacketPlay p;

        if (streamingAudioFile != null) {
            throw new Exception("Can't request a new streaming: still streaming: " + streamingAudioFile.getMusicTitle());
        }

        System.err.printf(
                "[ServerMain] asking the client at %s to stream '%s'",
                getAddressAsString(),
                audioFile.getMusicTitle()
        );
        System.err.println();
        p = new PacketPlay(audioFile.getMusicId());
        streamingAudioFile = audioFile;

        p.toStream(new DataOutputStream(socket.getOutputStream()));
    }

    public final void streamingEnded(ServerAudioFile audioFile)  {
        if (audioFile.equals(streamingAudioFile)){
            streamingAudioFile = null;
        }
    }

    public final void start() {
        this.runningThread = new Thread(this);
        this.runningThread.start();
    }

    public final void stop() {
        if (alive) {
            alive = false;
            if (runningThread != null && runningThread.isAlive()) {
                try {
                    runningThread.join();
                } catch (InterruptedException e) {
                    // Just ignore this
                }
            }
        }
    }

    /**
     * Picks a random audio file among the ones that have not been yet played ({@link #neverPlayedAudioFiles})
     */
    @SuppressWarnings("unchecked")
    public final ServerAudioFile pickRandomAudioFile() throws IOException {
        ServerAudioFile res;
        int i;
        if (!alive || availableAudioFiles.size() == 0) {
            return null;
        }
        if (neverPlayedAudioFiles.size() == 0) {
            neverPlayedAudioFiles = (ArrayList<ServerAudioFile>) availableAudioFiles.clone();
        }
        i = (int) Math.round(Math.random() * (neverPlayedAudioFiles.size() - 1));
        res = neverPlayedAudioFiles.get(i);
        neverPlayedAudioFiles.remove(i);
        return res;
    }

    /**
     * @return il socket associato a questo client
     */
    public final Socket getSocket() {
        return socket;
    }

    @Override
    public final void run() {

        while (alive) {
            PacketMusic packetMusic = receiveMusicPacket();
            if (packetMusic != null) {
                addAudioFile(packetMusic.musicId, packetMusic.musicTitle);
            }
        }

        stop();
    }

    private PacketMusic receiveMusicPacket() {
        PacketMusic packetMusic = null;
        try {
            packetMusic = new PacketMusic();
            packetMusic.fromStream(dis);
        } catch (SocketTimeoutException e) {
            // Heartbeat timeout
        } catch (SocketException e) {
            System.err.printf(
                    "[ServerMain] Lost connection with the client from %s. Error: %s",
                    getAddressAsString(),
                    e.getMessage()
            );
            System.err.println();
            alive = false;
        } catch (UnexpectedPacketException e) {
            alive = false;
            System.err.printf(
                    "[ServerMain] Client from %s has sent an unexpected packet. Error: %s",
                    getAddressAsString(),
                    e.getMessage()
            );
            System.err.println();
        } catch (IOException e) {
            e.printStackTrace();
            alive = false;
        }
        return packetMusic;
    }

    private void addAudioFile(char musicId, String musicTitle) {
        ServerAudioFile audioFile;
        audioFile = new ServerAudioFile(musicId, musicTitle, this);
        availableAudioFiles.add(audioFile);
        neverPlayedAudioFiles.add(audioFile);
        System.out.printf(
                "[ServerMain] client from %s added an audio file: '%s'",
                getAddressAsString(),
                audioFile.getMusicTitle()
        );
        System.out.println();
        synchronized (serverPlaylistManager) {
            serverPlaylistManager.notify();
        }
    }
}
