package gd.choir.client;

import com.sun.istack.internal.NotNull;

public class AudioChunk {
    long startOffsetInOrigin;
    long endOffsetInOrigin;
    byte[] data;
    long dataLength;

    public AudioChunk(@NotNull byte[] data, long dataLength, long startOffsetInOrigin) {
        if (data == null) {
            throw new NullPointerException();
        }

        this.data = data;
        this.dataLength = dataLength;
        this.startOffsetInOrigin = startOffsetInOrigin;
        this.endOffsetInOrigin = startOffsetInOrigin + dataLength;
    }

    public int getByteAtOriginOffset(long originOffset) {
        int result;
        int localOffset = (int) (originOffset - startOffsetInOrigin);
        result = (int) data[localOffset];
        result &= 0xff;

        return result;
    }

    /**
     * @return the number of bytes actually copied in the given destinationBuffer
     */
    public int copyDataAtOriginOffset(long originOffset, @NotNull byte[] destinationBuffer, int destinationBufferOffset, int maxRequestedLength) {
        int localOffset = originOffsetToLocalOffset(originOffset);
        int copiedBytes = Math.min((int) dataLength - localOffset, maxRequestedLength);
        System.arraycopy(data, localOffset, destinationBuffer, destinationBufferOffset, copiedBytes);
        return copiedBytes;
    }

    private int originOffsetToLocalOffset(long originOffset) {
        return (int) (originOffset - startOffsetInOrigin);
    }
}
