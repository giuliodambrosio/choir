/**
 *
 */
package uk.co.dambrosio.choir.common;

import java.net.InetAddress;

/**
 * @author Giulio D'Ambrosio
 */
public class AudioFile {
    protected char musicId = 0;

    protected String musicTitle = "";

    public int hashCode() {
        int result;
        result = musicId;
        return result & 0xffff;
    }

    public static int hashCode(final InetAddress clientAddress, final char musicId) {
        int result = clientAddress.toString().hashCode() + (int) musicId;
        result = (result >> 16) + (result & 0xffff);
        return result & 0xffff;
    }

    public final boolean equals(final AudioFile audioFile) {
        return audioFile != null && hashCode() == audioFile.hashCode();
    }

    public char getMusicId() {
        return musicId;
    }

    public void setMusicId(char musicId) {
        this.musicId = musicId;
    }

    public String getMusicTitle() {
        return musicTitle;
    }

    public void setMusicTitle(String musicTitle) {
        this.musicTitle = musicTitle;
    }
}
