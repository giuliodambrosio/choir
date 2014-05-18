package gd.choir.client;

import com.sun.istack.internal.NotNull;

public class AudioStreamChunk {
    @NotNull
    byte[] data;
    long dataLength;
    long startStreamOffset;
    long endStreamOffset;

    public AudioStreamChunk(@NotNull byte[] data, long dataLength, long startStreamOffset) {
        super();
        this.data = data;
        this.dataLength = dataLength;
        this.startStreamOffset = startStreamOffset;
        this.endStreamOffset = startStreamOffset + dataLength;
    }

    /**
     * @param streamOffset Offset relative to the audio stream
     * @return 1 byte of data
     */
    public int getByteAtStreamOffset(long streamOffset) {
        return getByteAtChunkOffset(streamOffsetToChunkOffset(streamOffset));
    }

    /**
     * @param chunkOffset Offset in this chunk data
     * @return 1 byte of data
     */
    public int getByteAtChunkOffset(int chunkOffset) {
        int result = (int) data[chunkOffset];
        result &= 0xff;
        return result;
    }

    /**
     * @return the number of bytes actually copied in the given destinationBuffer
     */
    public int copyDataAtStreamOffset(long streamOffset, @NotNull byte[] destinationBuffer, int destinationBufferOffset, int maxRequestedLength) {
        return copyDataAtChunkOffset(streamOffsetToChunkOffset(streamOffset), destinationBuffer, destinationBufferOffset, maxRequestedLength);
    }

    /**
     * @return the number of bytes actually copied in the given destinationBuffer
     */
    public int copyDataAtChunkOffset(int chunkOffset, @NotNull byte[] destinationBuffer, int destinationBufferOffset, int maxRequestedLength) {
        int copiedBytes = Math.min((int) dataLength - chunkOffset, maxRequestedLength);
        System.arraycopy(data, chunkOffset, destinationBuffer, destinationBufferOffset, copiedBytes);
        return copiedBytes;
    }

    private int streamOffsetToChunkOffset(long originOffset) {
        return (int) (originOffset - startStreamOffset);
    }
}
