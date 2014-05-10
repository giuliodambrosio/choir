/**
 *
 */
package gd.choir.data.packet.exceptions;

import java.io.IOException;

/**
 * @author Giulio D'Ambrosio
 */
public class UnexpectedPacketException extends IOException {
    /**
     *
     */
    private static final long serialVersionUID = 7671301236182494944L;

    public UnexpectedPacketException(String expectedCode, String actualCode) {
        super("Unexpected packet received while expecting: "
                + expectedCode
                + ". Received code is: " + actualCode);
    }
}
