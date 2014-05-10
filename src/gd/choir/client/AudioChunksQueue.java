package gd.choir.client;

import com.sun.istack.internal.NotNull;

import java.security.InvalidParameterException;
import java.util.LinkedList;

public class AudioChunksQueue {
    private LinkedList<AudioChunk> chunksList = new LinkedList<>();
    private long nextAvailableOriginOffset = 0;
    private boolean closed = false;

    public void addAudioChunkData(@NotNull byte[] audioChunkData, long audioChunkDataSize) {
        AudioChunk audioChunk = new AudioChunk(audioChunkData, audioChunkDataSize, nextAvailableOriginOffset);
        addAudioChunk(audioChunk);
    }

    synchronized public void addAudioChunk(@NotNull AudioChunk audioChunk) {
        if (audioChunk.startOffsetInOrigin != nextAvailableOriginOffset) {
            throw new InvalidParameterException(
                    String.format(
                            "Invalid audio chunk added: received chunk starting at %d, while expecting chunk for %d",
                            audioChunk.startOffsetInOrigin,
                            nextAvailableOriginOffset
                    )
            );
        }
        chunksList.addLast(audioChunk);
        nextAvailableOriginOffset = audioChunk.endOffsetInOrigin;
        notifyAll();
    }

    synchronized public AudioChunk getOrWaitForChunkContainingOriginOffset(long offset) {
        AudioChunk result = getAudioChunkContainingOriginOffset(offset);
        if (result == null && ! isClosed()) {
            try {
                wait();
                result = getAudioChunkContainingOriginOffset(offset);
            } catch (InterruptedException e) {
                result = null;
            }
        }
        return result;
    }

    private AudioChunk getAudioChunkContainingOriginOffset(long offset) {
        for (AudioChunk audioChunk : chunksList) {
            if (offset >= audioChunk.startOffsetInOrigin && offset < audioChunk.endOffsetInOrigin) {
                return audioChunk;
            }
        }
        return null;
    }

    synchronized public void freeChunksBehindOriginOffset(long offset) {
        while (chunksList.size() > 0 && chunksList.peekLast().endOffsetInOrigin - 1 < offset) {
            chunksList.removeLast();
        }
    }

    public long getNextAvailableOriginOffset() {
        return nextAvailableOriginOffset;
    }

    public boolean isClosed() {
        return closed;
    }

    synchronized public void close() {
        closed = true;
    }
}
