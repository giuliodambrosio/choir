/**
 *
 */
package gd.choir.common;

import gd.choir.data.packet.datagram.PacketHello;

/**
 * @author Giulio D'Ambrosio
 */
@FunctionalInterface
public interface HelloPacketListener extends PacketListener {

    public void packetArrived(PacketHello packet);

}
