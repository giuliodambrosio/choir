/**
 *
 */
package gd.choir.common;

import gd.choir.proto.packets.audio.PacketBegin;

/**
 * @author Giulio D'Ambrosio
 */
@FunctionalInterface
public interface AudioBeginPacketListener extends PacketListener {
    public void packetArrived(PacketBegin packet);
}
