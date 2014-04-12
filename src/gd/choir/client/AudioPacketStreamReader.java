/**
 */
package gd.choir.client;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.ListIterator;

/**
 * Serve i dati come un input stream, mentre li riceve come pacchetti I
 * pacchetti già letti e non marcati (markPosition, markLimit), vengono rimossi
 * dal vettore dei pacchetti, permettendo al garbage collector di liberare la
 * memoria. I pacchetti vengono copiati in una coda di buffer di dimensione
 * parametrica.
 *
 * @author Giulio D'Ambrosio
 */
public class AudioPacketStreamReader extends InputStream {
    private class JBPacketData {
        long startOffset;
        long endOffset;
        byte[] data;
        long dataSize;

        public JBPacketData(byte[] data, int dataSize, long startOffset) {
            if (data == null) {
                throw new NullPointerException();
            }

            this.data = data;
            this.dataSize = dataSize;
            this.startOffset = startOffset;
            this.endOffset = startOffset + dataSize;
        }
    }

    /**
     * Coda dei packets
     */
    private LinkedList<JBPacketData> packets;

    /**
     * Se true, lo stream è arrivato completamente
     */
    private boolean completed = false;

    /**
     * Posizione nello stream
     */
    private long offset = 0L;

    /**
     * Dimensione complessiva dello stream
     */
    private long totalSize = 0L;

    /**
     * Posizione memorizzata per rollback
     */
    private long markPosition = -1L;

    /**
     * Allontanamento dalla posizione marcata per rollback, da mantenere in
     * memoria
     */
    private long markLimit = 0L;

    /**
     * Costruttore.
     */
    public AudioPacketStreamReader() {
        packets = new LinkedList<>();
        this.totalSize = 0;
    }

    /**
     * Legge un singolo byte dallo stream
     *
     * @see java.io.InputStream#read()
     */
    @Override
    public synchronized int read() throws IOException {
        int res, off;
        JBPacketData p;

        if (offset >= totalSize) {
            if (completed) {
                return -1;
            }
            // Si blocca in attesa del prossimo pacchetto dati
            try {
                wait();
            } catch (InterruptedException e) {
                // E' richiesta l'interruzione della lettura
            }
            if (offset >= totalSize) {
                // sbloccato, ma ancora vuoto:stream finito
                return completed ? -1 : 0;
            }
        }

        p = getPacket();
        off = (int) (offset - p.startOffset);
        res = (int) p.data[off];
        res &= 0xff;
        offset++;

        freePastBuffers();
        return res;
    }

    /**
     * Legge in un buffer fornito dall'utente, al più bufferLength bytes dallo
     * stream. Se non sono immediatamente disponibili, il metodo si blocca
     * (tramite wait) in attesa di un nuovo pacchetto.
     *
     * @see java.io.InputStream#read()
     */
    @Override
    public synchronized int read(byte[] buffer, int bufferOffset,
                                 int bufferLength) throws IOException {
        JBPacketData p;

        long off, len;

        if (buffer == null) {
            throw new NullPointerException();
        }

        if (bufferOffset < 0 || bufferLength < 0
                || (bufferLength > buffer.length - bufferOffset)) {
            throw new IndexOutOfBoundsException();
        }

        if (bufferLength == 0) {
            return 0;
        }

        if (offset >= totalSize) {
            while (!completed && offset >= totalSize) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    // richiesta l'interruzione della lettura
                }
            }
            if (completed && offset >= totalSize) {
                // Stream terminato
                return -1;
            }
        }
        if (offset + bufferLength > totalSize) {
            // Se l'offset e/o la lunghezza dei dati richiesti superano i dati
            // disponibili,
            /*
			 * altrimenti si blocca in attesa del prossimo pacchetto dati
			 */
            while (!completed && offset + bufferLength > totalSize) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    // richiesta l'interruzione della lettura
                }
            }
            if (offset + bufferLength > totalSize) {
                bufferLength -= (offset + bufferLength) - totalSize;
            }
        }

        if (bufferLength == 0) {
            return completed ? -1 : 0;
        }

        // Legge i dati richiesti da tutti i pacchetti necessari
        long curbuflen = bufferLength, curbufoff = bufferOffset;
        ListIterator<JBPacketData> i;
        i = packets.listIterator();
        while (true) {
            if (!i.hasNext()) {
                throw new IOException("out of packets error");
            }
            p = i.next();
            if (offset >= p.startOffset && offset < p.endOffset) {
                break;
            }
        }
        while (curbuflen > 0) {
            off = (int) (offset - p.startOffset);
            len = Math.min(p.dataSize - off, curbuflen);
            System.arraycopy(p.data, (int) off, buffer, (int) curbufoff, (int) len);

            offset += len;
            curbufoff += len;
            curbuflen -= len;
            if (!i.hasNext()) {
                break;
            } else {
                p = i.next();
            }
        }
        // Libera eventualmente i pacchetti che non verranno più richiesti
        freePastBuffers();
        return (int) (bufferLength - curbuflen);
    }

    /**
     * Libera tutti i pacchetti che non possono essere più richiesti in quanto
     * letti, ed oltre l'eventuale limite precedentemente marcato tramite
     * chiamata al metodo mark.
     */
    private synchronized void freePastBuffers() {
        if (markPosition < 0
                || (markPosition >= 0 && markPosition + markLimit < offset)) {
            // libera la memoria per i packet già letti e non marcati
            while (packets.size() > 1
                    && packets.getFirst().endOffset - 1 < offset) {
                packets.removeFirst();
            }
        }
    }

    /**
     * Restituisce il buffer che contiene il byte all'offset corrente.
     *
     * @return il buffer corrente
     */
    private synchronized JBPacketData getPacket() {
        if (offset < 0 || offset >= totalSize || packets.size() == 0) {
            throw new IndexOutOfBoundsException();
        }
        JBPacketData res = null;
        for (JBPacketData packet : packets) {
            res = packet;
            if (offset >= res.startOffset && offset < res.endOffset) {
                break;
            }
        }
        return res;
    }

    /**
     * Aggiunge al buffer in coda un nuovo pacchetto di dati
     *
     * @param packetData Buffer dei dati da aggiungere
     * @param packetSize Numero di bytes da copiare
     */
    public synchronized void addPacket(byte[] packetData, int packetSize)
            throws IOException {
        if (this.completed) {
            throw new IOException("file chiuso: impossibile aggiungere un nuovo pacchetto");
        }
        if (packetData == null || packetSize <= 0) {
            throw new IOException("null packet data");
        }
        packets.addLast(new JBPacketData(packetData, packetSize, totalSize));
        totalSize += packetSize;
        notify();
    }

    /**
     * Restituisce il numero di bytes disponibili prima che una read causi un
     * blocco in attesa di dati non ancora arrivati.
     */
    public synchronized int available() throws IOException {
        return (int) (totalSize - offset);
    }

    /**
     * Chiude lo stream, provocando anche lo sblocco dell'eventuale wait
     * dell'oggetto durante una read bloccante.
     */
    public synchronized void close() throws IOException {
        totalSize = 0;
        offset = 0;
        setCompleted();
    }

    /**
     * Memorizza la posizione attuale: alla successiva chiamata del metodo
     * reset, verrà ripristinata la posizione in cui si trovava offset al
     * momento di questa chiamata. Se durante la lettura saranno stati letti
     * markLimit bytes, senza che la posizione sia stata resettata, il limite
     * andrà perso, così come i dati precedenti all'offset corrente.
     *
     * @param markLimit Numero di bytes oltrepassati i quali, la posizione marcata
     *                  sarà persa
     */
    public synchronized void mark(int markLimit) {
        markPosition = offset;
        this.markLimit = markLimit;
    }

    /**
     */
    public synchronized void reset() throws IOException {
        if (markPosition < 0) {
            throw new IOException("Resetting to invalid mark");
        }
        offset = markPosition;
    }

    /**
     * Indica che questo stream supporta reset e mark.
     */
    public boolean markSupported() {
        return true;
    }

    /**
     * Marca questo stream come completo: sono disponibili senza rischio di
     * blocchi tutti i bytes fra la posizione attuale e la fine del file. Se si
     * tentasse di aggiungere un pacchetto dopo la chiamata a questo metodo,
     * addPacket lancerebbe un'eccezione.
     */
    public synchronized void setCompleted() {
        completed = true;
        notify();
    }

    /**
     * Controlla se lo stream è completo (@see setComplete)
     */
    public synchronized boolean isCompleted() {
        return completed;
    }

}
