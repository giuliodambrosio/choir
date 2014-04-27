/**
 *
 */
package gd.choir.common;

import gd.choir.proto.packets.PacketJoin;

/**
 * @author Giulio D'Ambrosio
 */
@FunctionalInterface
public interface JoinPacketListener {
    public void packetArrived(PacketJoin packet);
}
