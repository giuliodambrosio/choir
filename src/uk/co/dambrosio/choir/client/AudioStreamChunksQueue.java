package uk.co.dambrosio.choir.client;

import com.sun.istack.internal.NotNull;

import java.security.InvalidParameterException;
import java.util.LinkedList;

public class AudioStreamChunksQueue {
    private LinkedList<AudioStreamChunk> chunksList = new LinkedList<>();
    private long nextAvailableStreamOffset = 0;
    private boolean closed = false;

    public void addAudioChunkData(@NotNull byte[] audioChunkData, long audioChunkDataSize) {
        AudioStreamChunk audioStreamChunk = new AudioStreamChunk(audioChunkData, audioChunkDataSize, nextAvailableStreamOffset);
        addAudioChunk(audioStreamChunk);
    }

    synchronized public void addAudioChunk(@NotNull AudioStreamChunk audioStreamChunk) {
        if (audioStreamChunk.startStreamOffset != nextAvailableStreamOffset) {
            throw new InvalidParameterException(
                    String.format(
                            "Invalid audio chunk added: received chunk starting at %d, while expecting chunk for %d",
                            audioStreamChunk.startStreamOffset,
                            nextAvailableStreamOffset
                    )
            );
        }
        chunksList.addLast(audioStreamChunk);
        nextAvailableStreamOffset = audioStreamChunk.endStreamOffset;
        notifyAll();
    }

    synchronized public AudioStreamChunk getOrWaitForChunkContainingStreamOffset(long offset) {
        AudioStreamChunk result = getAudioChunkContainingStreamOffset(offset);
        if (result == null && ! isClosed()) {
            try {
                wait();
                result = getAudioChunkContainingStreamOffset(offset);
            } catch (InterruptedException e) {
                result = null;
            }
        }
        return result;
    }

    private AudioStreamChunk getAudioChunkContainingStreamOffset(long offset) {
        for (AudioStreamChunk audioStreamChunk : chunksList) {
            if (offset >= audioStreamChunk.startStreamOffset && offset < audioStreamChunk.endStreamOffset) {
                return audioStreamChunk;
            }
        }
        return null;
    }

    synchronized public void freeChunksBehindStreamOffset(long offset) {
        while (chunksList.size() > 0 && chunksList.peekLast().endStreamOffset - 1 < offset) {
            chunksList.removeLast();
        }
    }

    public long getNextAvailableStreamOffset() {
        return nextAvailableStreamOffset;
    }

    public boolean isClosed() {
        return closed;
    }

    synchronized public void close() {
        closed = true;
    }
}
