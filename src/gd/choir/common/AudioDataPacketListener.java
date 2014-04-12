/**
 *
 */
package gd.choir.common;

import gd.choir.proto.packets.audio.PacketData;

/**
 * @author Giulio D'Ambrosio
 */
@FunctionalInterface
public interface AudioDataPacketListener {

    public void packetArrived(PacketData jbdp);

}