/**
 *
 */
package uk.co.dambrosio.choir.common;

import uk.co.dambrosio.choir.data.packet.datagram.audio.PacketBegin;

/**
 * @author Giulio D'Ambrosio
 */
@FunctionalInterface
public interface AudioBeginPacketListener extends PacketListener {
    public void packetArrived(PacketBegin packet);
}
