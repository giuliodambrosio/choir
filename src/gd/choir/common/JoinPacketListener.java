/**
 *
 */
package gd.choir.common;

import gd.choir.data.packet.datagram.DatagramPacket;

/**
 * @author Giulio D'Ambrosio
 */
@FunctionalInterface
public interface JoinPacketListener {
    public void packetArrived(DatagramPacket.PacketJoin packet);
}
