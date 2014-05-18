/**
 */
package gd.choir.client;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;

import java.io.IOException;
import java.io.InputStream;

/**
 * This class reads packets from an audio stream chunks queue and provides a continuous stream
 * of data to be read
 *
 * @author Giulio D'Ambrosio
 */
public class AudioPacketInputStream extends InputStream {
    public AudioPacketInputStream(AudioStreamChunksQueue audioStreamChunksQueue) {
        chunksQueue = audioStreamChunksQueue;
    }

    private long streamOffset = 0L;

    /** Used to implement {@link AudioPacketInputStream#mark(int)} */
    private @Nullable RollbackMark rollbackMark = null;

    private @NotNull AudioStreamChunksQueue chunksQueue;

    /**
     * @see java.io.InputStream#read()
     */
    @Override
    public int read() throws IOException {
        AudioStreamChunk audioStreamChunk = chunksQueue.getOrWaitForChunkContainingStreamOffset(streamOffset);
        if (audioStreamChunk != null) {
            int result = audioStreamChunk.getByteAtStreamOffset(streamOffset++);
            freeUnreachableAudioChunksInQueue();
            return result;
        } else {
            return -1;
        }
    }

    /**
     * @see java.io.InputStream#read(byte[], int, int)
     */
    @Override
    public int read(@NotNull byte[] buffer, int bufferOffset, int maxRequestedLength) throws IOException {
        int resultLength = 0;
        if (buffer == null) {
            throw new NullPointerException();
        }

        if (bufferOffset < 0 || maxRequestedLength < 0
                || (maxRequestedLength > buffer.length - bufferOffset)) {
            throw new IndexOutOfBoundsException();
        }

        while (bufferOffset < maxRequestedLength) {
            AudioStreamChunk audioStreamChunk = chunksQueue.getOrWaitForChunkContainingStreamOffset(streamOffset);
            if (audioStreamChunk != null) {
                int copiedBytes = audioStreamChunk.copyDataAtStreamOffset(streamOffset, buffer, bufferOffset, maxRequestedLength);
                maxRequestedLength -= copiedBytes;
                resultLength += copiedBytes;
                bufferOffset += copiedBytes;
                streamOffset += copiedBytes;

                freeUnreachableAudioChunksInQueue();
            } else {
                break;
            }
        }

        return resultLength;
    }

    /**
     * @see java.io.InputStream#available()
     */
    public int available() throws IOException {
        return (int) (chunksQueue.getNextAvailableStreamOffset() - streamOffset);
    }

    /**
     * @see java.io.InputStream#close()
     */
    public void close() throws IOException {
        chunksQueue.close();
    }

    /**
     * @see InputStream#mark(int)
     */
    public void mark(int markLimit) {
        rollbackMark = new RollbackMark(streamOffset, markLimit);
    }

    /**
     * @see java.io.InputStream#reset()
     */
    public void reset() throws IOException {
        if (rollbackMark == null || !rollbackMark.isStillReachableAtOffset(streamOffset)) {
            throw new IOException("Resetting to invalid mark");
        }
        streamOffset = rollbackMark.markedOffset;
    }

    /**
     * @see java.io.InputStream#markSupported()
     */
    public boolean markSupported() {
        return true;
    }

    /**
     * Frees all the packet that can't be reached anymore because already read
     * and behind the optional limit previously set by calling {@link AudioPacketInputStream#mark(int)}
     */
    private void freeUnreachableAudioChunksInQueue() {
        if (rollbackMark == null || ! rollbackMark.isStillReachableAtOffset(streamOffset)) {
            chunksQueue.freeChunksBehindStreamOffset(streamOffset);
        } else {
            chunksQueue.freeChunksBehindStreamOffset(rollbackMark.markedOffset - 1);
        }
    }

    /**
     * This class is used to mark a rollback position in this input stream.
     * The rollback is available until the current position in the stream exceeds the maximum distance allowed
     * from the rollback position.
     */
    private class RollbackMark {
        public long markedOffset;
        public long maxReachableDistanceFromMarkedOffset;
        public RollbackMark(long markedOffset, long maxReachableDistanceFromMarkedOffset) {
            this.markedOffset = markedOffset;
            this.maxReachableDistanceFromMarkedOffset = maxReachableDistanceFromMarkedOffset;
        }

        public boolean isStillReachableAtOffset(long offset) {
            return markedOffset + maxReachableDistanceFromMarkedOffset >= offset;
        }
    }
}
