/**
 *
 */
package gd.choir.common;

import gd.choir.proto.packets.audio.PacketEnd;

/**
 * @author Giulio D'Ambrosio
 */
@FunctionalInterface
public interface AudioEndPacketListener extends PacketListener {
    public void packetArrived(PacketEnd packet);
}
