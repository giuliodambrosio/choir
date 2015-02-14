package uk.co.dambrosio.choir.client;

import java.io.IOException;
import java.net.InetAddress;

import javax.sound.sampled.UnsupportedAudioFileException;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import uk.co.dambrosio.choir.common.PacketDispatcher;
import uk.co.dambrosio.choir.data.packet.datagram.audio.PacketBegin;
import uk.co.dambrosio.choir.data.packet.datagram.audio.PacketDataChunk;
import uk.co.dambrosio.choir.data.packet.datagram.audio.PacketEnd;

/**
 * This class uses a {@link AudioFileStreamingPlan} to split an audio file
 * in small chunks and send them at the right rate through the {@link PacketDispatcher}
 *
 * @author Giulio D'Ambrosio
 */
public class AudioPacketStreamWriter implements Runnable {

    private boolean alive = false;

    private ClientAudioFile audioFile;

    private final AudioFileStreamingPlan streamingPlan;

    private InetAddress multicastGroupAddress;

    private char multicastGroupPort;

    private PacketDispatcher packetDispatcher;

    /**
     * The thread for this runnable
     */
    @Nullable
    private Thread runningThread = null;

    public AudioPacketStreamWriter(
            @NotNull final ClientAudioFile audioFile,
            @NotNull final PacketDispatcher packetDispatcher,
            @NotNull final InetAddress multicastGroupAddress,
            @NotNull final char multicastGroupPort
    ) throws IOException, UnsupportedAudioFileException {
        super();
        this.multicastGroupAddress = multicastGroupAddress;
        this.multicastGroupPort = multicastGroupPort;
        this.audioFile = audioFile;
        this.packetDispatcher = packetDispatcher;
        streamingPlan = new AudioFileStreamingPlan(audioFile);
        streamingPlan.outputAudioFileStreamingInfo();
    }

    /**
     * @return true if the thread for this runnable is currently alive
     */
    public final boolean isAlive() {
        return runningThread != null && runningThread.isAlive();
    }

    /**
     * Creates and starts a thread for this task
     */
    public final void startThread() {
        alive = true;
        runningThread = new Thread(this);
        runningThread.start();
    }

    /**
     * Causes the thread for this runnable to stop
     */
    public final void stopThread() {
        alive = false;
        runningThread.interrupt();
    }

    /**
     * @see java.lang.Runnable#run()
     */
    @Override
    public final void run() {
        PacketDataChunk dataChunk;

        notifyBeginOfStream();

        while (alive) {
            try {
                streamingPlan.fillAudioBuffer();
                if (alive = streamingPlan.getFrameBufferContentLength() >= 0) {
                    streamingPlan.waitForTimeToSendPacket();
                    dataChunk = new PacketDataChunk(
                        audioFile.getMusicId(),
                        streamingPlan.getFrameBuffer(),
                        (char) streamingPlan.getFrameBufferContentLength(),
                        multicastGroupAddress,
                        multicastGroupPort
                    );
                    packetDispatcher.send(dataChunk);
                }
            } catch (IOException e) {
                alive = false;
                e.printStackTrace();
            } catch (InterruptedException e) {
                System.err.println("The audio stream writer has been interrupted");
                break;
            }
        }

        notifyEndOfStream();

        streamingPlan.close();
        System.err.println("The audio stream writer has completed...");
    }


    private void notifyBeginOfStream() {
        try {
            packetDispatcher.send(new PacketBegin(audioFile, multicastGroupAddress, multicastGroupPort));
        } catch (IOException e) {
            alive = false;
            System.err.println("Error occurred while starting stream: " + e.getMessage());
        }
    }

    private void notifyEndOfStream() {
        try {
            packetDispatcher.send((new PacketEnd(audioFile.getMusicId(), multicastGroupAddress, multicastGroupPort)));
        } catch (IOException e) {
            System.err.println("Error occurred while ending audio stream: " + e.getMessage());
        }
    }
}
