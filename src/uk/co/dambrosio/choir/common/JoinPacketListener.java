/**
 *
 */
package uk.co.dambrosio.choir.common;

import uk.co.dambrosio.choir.data.packet.datagram.DatagramPacket;

/**
 * @author Giulio D'Ambrosio
 */
@FunctionalInterface
public interface JoinPacketListener {
    public void packetArrived(DatagramPacket.PacketJoin packet);
}
