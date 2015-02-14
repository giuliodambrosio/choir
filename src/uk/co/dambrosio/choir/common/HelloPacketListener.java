/**
 *
 */
package uk.co.dambrosio.choir.common;

import uk.co.dambrosio.choir.data.packet.datagram.PacketHello;

/**
 * @author Giulio D'Ambrosio
 */
@FunctionalInterface
public interface HelloPacketListener extends PacketListener {

    public void packetArrived(PacketHello packet);

}
