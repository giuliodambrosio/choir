package gd.choir.client;

import com.sun.istack.internal.NotNull;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.*;
import java.util.Calendar;

public class AudioFileStreamingPlan {
    private static final int AUDIO_PACKET_PAYLOAD_TARGET_SIZE = 1000;
    public static final double MILLISECONDS_IN_A_SECOND = 1000.0d;
    public static final int BYTES_IN_A_KILOBYTE = 1024;

    @NotNull
    ClientAudioFile audioFile;
    AudioFormat audioFormat;
    byte[] frameBuffer;
    int frameBufferContentLength;
    int audioFrameSize;
    int targetPacketsPerSecond;
    int targetPauseBetweenPackets;
    int actualPacketPayloadSize;
    int audioFramePacketsSent = 0;
    double adjustmentToPauseBetweenPackets = 0d;
    double targetAudioBytesPerMsec = 0;
    long lastSentPacketTimestamp = 0L; // timestamp dell'ultimo pacchetto inviato
    long lastFrameTimestamp = 0L; // timestamp dell'ultimo frame inviato
    InputStream inputStream;

    public AudioFileStreamingPlan(@NotNull ClientAudioFile audioFile)
            throws UnsupportedAudioFileException, IOException {
        this.audioFile = audioFile;
        extractAudioFormat();
        validateAudioFormat();
        calculateParameters();
        createStreamAndBuffer(audioFile);
    }

    public void fillAudioBuffer() throws IOException {
        frameBufferContentLength = inputStream.read(frameBuffer, 0, frameBuffer.length);
    }

    public byte[] getFrameBuffer() {
        return frameBuffer;
    }

    public int getFrameBufferContentLength() {
        return frameBufferContentLength;
    }

    public void waitForTimeToSendPacket() throws InterruptedException {
        long toSleep = millisecondsToNextPacket();
        if (toSleep > 0) {
            Thread.sleep(toSleep);
        }
        lastSentPacketTimestamp = timeInMillis();
        if (audioFramePacketsSent++ == targetPacketsPerSecond) {
            // After every audio frame has been sent...
            if (lastFrameTimestamp != 0) {
                recalculateAdjustmentToPauseBetweenPackets();
            }
            audioFramePacketsSent = 0;
            lastFrameTimestamp = timeInMillis();
        }
    }

    public void outputAudioFileStreamingInfo() {
        System.err.printf(
                "Starting streaming: '%s' (id=%d)",
                audioFile.getMusicTitle(),
                (int) audioFile.getMusicId()
        );
        System.err.println();
        System.err.printf(
                "\taudio kbps: %f",
                (audioFormat.getChannels() * audioFormat.getSampleSizeInBits() * audioFormat.getSampleRate()) / MILLISECONDS_IN_A_SECOND
        );
        System.err.println();
        System.err.printf(
                "\tframe size: %d. frame rate: %f, frame KBps: %f",
                audioFormat.getFrameSize(),
                audioFormat.getFrameRate(),
                (targetAudioBytesPerMsec * MILLISECONDS_IN_A_SECOND) / BYTES_IN_A_KILOBYTE
        );
        System.err.println();
        System.err.printf(
                "\tpacket rate: %d, payload size: %d",
                targetPacketsPerSecond,
                actualPacketPayloadSize
        );
        System.err.println();
        System.err.printf(
                "\tpacket intv.: %d, packet sync intv.: %.2f",
                targetPauseBetweenPackets,
                adjustmentToPauseBetweenPackets
        );
        System.err.println();
    }

    public void close() {
        if (inputStream != null) {
            try {
                inputStream.close();
                inputStream = null;
            } catch (IOException e) {
                System.err.println("Warning: error while closing audio input stream");
            }
        }
    }

    private long timeInMillis() {
        return Calendar.getInstance().getTimeInMillis();
    }

    private long millisecondsToNextPacket() {
        if (lastSentPacketTimestamp == 0) {
            return 0;
        } else {
            return (int)
                    Math.round(adjustmentToPauseBetweenPackets + targetPauseBetweenPackets - (timeInMillis() - lastSentPacketTimestamp));
        }
    }

    /**
     * Any difference between the target packet rate and the actual one
     * gets adjusted spreading it to the pauses of the next audio frame
     */
    private void recalculateAdjustmentToPauseBetweenPackets() {
        adjustmentToPauseBetweenPackets += (((audioFramePacketsSent * actualPacketPayloadSize - audioFrameSize) / targetAudioBytesPerMsec) + (MILLISECONDS_IN_A_SECOND - (timeInMillis() - lastFrameTimestamp)))
                / targetPacketsPerSecond;
    }

    private void extractAudioFormat() throws UnsupportedAudioFileException, IOException {
        AudioInputStream audioInputStream;
        audioInputStream = AudioSystem.getAudioInputStream(audioFile.getFile());
        audioFormat = audioInputStream.getFormat();
        audioInputStream.close();
    }

    private void validateAudioFormat() throws UnsupportedAudioFileException {
        if (audioFormat.getFrameSize() == AudioSystem.NOT_SPECIFIED) {
            throw new UnsupportedAudioFileException("Frame size is not specified in the audio file");
        }
    }

    private void calculateParameters() throws UnsupportedAudioFileException {
        audioFrameSize = audioFormat.getFrameSize();
        if (audioFormat.getFrameRate() != AudioSystem.NOT_SPECIFIED) {
            audioFrameSize *= (int) audioFormat.getFrameRate();
        }
        targetAudioBytesPerMsec = audioFrameSize / MILLISECONDS_IN_A_SECOND;

        // This is to keep each packet size the same...
        targetPacketsPerSecond = (int) Math.round(((double) audioFrameSize) / AUDIO_PACKET_PAYLOAD_TARGET_SIZE);
        actualPacketPayloadSize = (int) Math.ceil(((double) audioFrameSize) / targetPacketsPerSecond);
        targetPauseBetweenPackets = (int) Math.floor(MILLISECONDS_IN_A_SECOND / targetPacketsPerSecond);
        adjustmentToPauseBetweenPackets = (((targetPacketsPerSecond * actualPacketPayloadSize - audioFrameSize) / targetAudioBytesPerMsec)) / targetPacketsPerSecond;
    }

    private void createStreamAndBuffer(ClientAudioFile audioFile) throws FileNotFoundException {
        inputStream = new BufferedInputStream(new FileInputStream(audioFile.getFile()));
        frameBuffer = new byte[actualPacketPayloadSize];
    }
}
