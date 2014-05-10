/**
 *
 */
package gd.choir.common;

import gd.choir.data.packet.datagram.audio.PacketDataChunk;

/**
 * @author Giulio D'Ambrosio
 */
@FunctionalInterface
public interface AudioDataPacketListener extends PacketListener {
    public void packetArrived(PacketDataChunk packet);
}