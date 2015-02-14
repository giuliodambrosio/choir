/**
 *
 */
package uk.co.dambrosio.choir.common;

import uk.co.dambrosio.choir.data.packet.datagram.audio.PacketEnd;

/**
 * @author Giulio D'Ambrosio
 */
@FunctionalInterface
public interface AudioEndPacketListener extends PacketListener {
    public void packetArrived(PacketEnd packet);
}
