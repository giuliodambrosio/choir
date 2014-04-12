/**
 *
 */
package gd.choir.common;

import java.net.InetAddress;

/**
 * @author Giulio D'Ambrosio
 */
public class AudioFile {
    /**
     * Tune id.
     */
    protected char musicId = 0;

    /**
     * Tune title
     */
    protected String musicTitle = "";

    protected AudioFile() {
        super();
    }

    /**
     * Costruttore.
     *
     * @param musicId id del brano
     */
    public AudioFile(final char musicId) {
        this.musicId = musicId;
    }

    /**
     * @return codice hash associato a questa istanza
     */
    public int hashCode() {
        int result;
        result = musicId;
        return result & 0xffff;
    }

    /**
     * @param clientAddress Indirizzo del client che possiede il brano
     * @param musicId       Id del brano
     * @return codice hash associato ad una istanza con gli elementi passati come parametro
     */
    public static int hashCode(final InetAddress clientAddress, final char musicId) {
        int result = clientAddress.toString().hashCode() + (int) musicId;
        result = (result >> 16) + (result & 0xffff);
        return result & 0xffff;
    }

    /**
     * @param audioFile file audio con cui confrontare questa istanza
     * @return true se questa istanza equivale quella passata come parametro
     */
    public final boolean equals(final AudioFile audioFile) {
        return audioFile != null && hashCode() == audioFile.hashCode();
    }

    /**
     * @param musicId the musicId to set
     */
    public final void setMusicId(final char musicId) {
        this.musicId = musicId;
    }

    /**
     * @return the musicId
     */
    public final char getMusicId() {
        return musicId;
    }

    /**
     * @param musicTitle the musicTitle to set
     */
    public final void setMusicTitle(String musicTitle) {
        this.musicTitle = musicTitle;
    }

    /**
     * @return the musicTitle
     */
    public final String getMusicTitle() {
        return musicTitle;
    }

}
