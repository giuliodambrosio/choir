/**
 *
 */
package gd.choir.common;

import gd.choir.proto.packets.PacketHello;

/**
 * @author Giulio D'Ambrosio
 */
@FunctionalInterface
public interface HelloPacketListener {

    public void packetArrived(PacketHello jbdp);

}
