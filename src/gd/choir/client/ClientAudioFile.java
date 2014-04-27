/**
 *
 */
package gd.choir.client;

import java.io.File;

import gd.choir.common.AudioFile;

/**
 * Audio file representation for a client
 *
 * @author Giulio D'Ambrosio
 */
public class ClientAudioFile extends AudioFile {
    private File file = null;

    public ClientAudioFile(final File audioFile) {
        super();
        setFile(audioFile);
        setMusicId((char) hashCode());
        setMusicTitle(getFile().getName());
    }

    /**
     * Generates the hash code for the audio file.
     * If a {@link #file} is set in this instance, the hashcode will be the file hashcode,
     * otherwise the hash code will be its {@link #musicId},
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

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }
}
