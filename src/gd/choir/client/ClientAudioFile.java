/**
 *
 */
package gd.choir.client;

import java.io.File;

import gd.choir.common.AudioFile;

/**
 * Rappresentazione di una risorsa audio (lato client).
 *
 * @author Giulio D'Ambrosio
 */
public class ClientAudioFile extends AudioFile {
    /**
     * File del brano.
     */
    private File file = null;

    public ClientAudioFile(final File audioFile) {
        super();
        setFile(audioFile);
        setMusicId((char) hashCode());
        setMusicTitle(getFile().getName());
    }

    /**
     * Generates the hash code for the audio file.
     * If no file is associated to this instance,
     * Genera il codice hash per questa istanza. Il codice coincide con quello
     * del {@link #file} se presente, altrimenti coincide con {@link #musicId}
     *
     * @return il codice hash
     */
    public final int hashCode() {
        int result;
        if (getFile() != null) {
            result = getFile().hashCode();
            result = (result >> 16) + (result & 0xffff);
        } else {
            return super.hashCode();
        }
        return result & 0xffff;
    }

    /**
     * @param file the file to set
     */
    public final void setFile(File file) {
        this.file = file;
    }

    /**
     * @return the file
     */
    public final File getFile() {
        return file;
    }

}
