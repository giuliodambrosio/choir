/**
 *
 */
package uk.co.dambrosio.choir.common;

import java.io.IOException;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ConcurrentLinkedQueue;

import uk.co.dambrosio.choir.data.packet.Packet;
import uk.co.dambrosio.choir.data.packet.datagram.audio.PacketBegin;
import uk.co.dambrosio.choir.data.packet.datagram.audio.PacketDataChunk;
import uk.co.dambrosio.choir.data.packet.datagram.audio.PacketEnd;
import uk.co.dambrosio.choir.data.packet.datagram.DatagramPacket;
import uk.co.dambrosio.choir.data.packet.datagram.PacketHello;

/**
 * This class deals with receiving and sending packets using a multicast group.
 * The incoming packets are notified using the observer pattern.
 *
 * @author Giulio D'Ambrosio
 */
public class PacketDispatcher implements Runnable {

    /**
     * Every time a packet arrives, or this number of milliseconds
     * goes has passed, the thread executing this task will check to
     * be still {@link #alive}.
     */
    private static final int HEARTBEAT_INTERVAL_MILLISECONDS = 10 * 1000;

    private MulticastSocket multicastSocket;

    private InetAddress multicastGroupAddress;

    /**
     * Flag: se false il thread è chiuso o in chiusura
     */
    boolean alive = true;

    /**
     * The only thread executing this task
     */
    private Thread runningThread;

    final private ConcurrentLinkedQueue<AudioBeginPacketListener> audioBeginListeners;

    final private ConcurrentLinkedQueue<AudioDataPacketListener> audioDataListeners;

    final private ConcurrentLinkedQueue<AudioEndPacketListener> audioEndListeners;

    final private ConcurrentLinkedQueue<HelloPacketListener> helloListeners;

    final private ConcurrentLinkedQueue<JoinPacketListener> joinListeners;

    public PacketDispatcher(InetAddress multicastGroupAddress,
                            char multicastGroupPort) throws IOException {
        this(multicastGroupAddress, multicastGroupPort, null);
    }

    public PacketDispatcher(InetAddress multicastGroupAddress,
                            char multicastGroupPort, MulticastSocket multicastSocket) throws IOException {
        super();
        this.multicastGroupAddress = multicastGroupAddress;
        if (multicastSocket == null) {
            multicastSocket = new MulticastSocket(multicastGroupPort);
            multicastSocket.joinGroup(multicastGroupAddress);
            // multicastSocket.setLoopbackMode(true);
        }
        multicastSocket.setSoTimeout(HEARTBEAT_INTERVAL_MILLISECONDS);
        this.multicastSocket = multicastSocket;

        audioBeginListeners = new ConcurrentLinkedQueue<>();
        audioDataListeners = new ConcurrentLinkedQueue<>();
        audioEndListeners = new ConcurrentLinkedQueue<>();
        helloListeners = new ConcurrentLinkedQueue<>();
        joinListeners = new ConcurrentLinkedQueue<>();
    }

    protected void finalize() throws Throwable {
        super.finalize();
        if (multicastSocket != null) {
            multicastSocket.leaveGroup(multicastGroupAddress);
        }
    }

    /**
     * The thread waits for an incoming packet and notifies it to all the registered
     * listeners
     */
    @Override
    public void run() {
        byte[] buf = new byte[Packet.MAX_PACKET_PAYLOAD_SIZE];
        java.net.DatagramPacket dp = new java.net.DatagramPacket(buf, buf.length);
        DatagramPacket packet;

        while (alive) try {
            multicastSocket.receive(dp);
            packet = DatagramPacket.fromDatagram(dp);
            notifyAvailablePacket(packet);
        } catch (SocketTimeoutException e) {
            // This is the heartbeat: a chance to check that the thread is still alive
            // even if no packet are received
        } catch (IOException e) {
            e.printStackTrace();
            alive = false;
        }
    }

    /**
     * Spedisce un pacchetto verso il gruppo multicast
     *
     * @param p Il pacchetto da spedire
     * @throws IOException
     */
    public synchronized void send(DatagramPacket p) throws IOException {
        multicastSocket.send(p.getRawPacket());
    }

    /**
     * Creates and starts the only thread running this task
     */
    public synchronized void start() {
        if (runningThread == null) {
            runningThread = new Thread(this);
            runningThread.start();
        }
    }

    /**
     * Attende il termine dell'esecuzione del thread associato a questa istanza.
     *
     * @throws InterruptedException
     */
    public void stop() throws InterruptedException {
        runningThread.join();
    }

    /**
     * Interrompe il thread associato a questa istanza entro {@link #HEARTBEAT_INTERVAL_MILLISECONDS}
     * secondi.
     * Il metodo ritorna quando il thread è sicuramente terminato.
     *
     * @throws InterruptedException
     */
    public void stopNow() throws InterruptedException {
        alive = false;
        stop();
    }

    public void registerListener(AudioBeginPacketListener pl) {
        synchronized (audioBeginListeners) {
            audioBeginListeners.add(pl);
        }
    }

    public void unregisterListener(AudioBeginPacketListener pl) {
        synchronized (audioBeginListeners) {
            audioBeginListeners.remove(pl);
        }
    }

    public void registerListener(AudioDataPacketListener pl) {
        synchronized (audioDataListeners) {
            audioDataListeners.add(pl);
        }
    }

    public void unregisterListener(AudioDataPacketListener pl) {
        synchronized (audioDataListeners) {
            audioDataListeners.remove(pl);
        }
    }

    public void registerListener(AudioEndPacketListener pl) {
        synchronized (audioEndListeners) {
            audioEndListeners.add(pl);
        }
    }

    public void unregisterListener(AudioEndPacketListener pl) {
        synchronized (audioEndListeners) {
            audioEndListeners.remove(pl);
        }
    }

    public void registerListener(JoinPacketListener pl) {
        synchronized (joinListeners) {
            joinListeners.add(pl);
        }
    }

    public void unregisterListener(JoinPacketListener pl) {
        synchronized (joinListeners) {
            joinListeners.remove(pl);
        }
    }

    public void registerListener(HelloPacketListener pl) {
        synchronized (helloListeners) {
            helloListeners.add(pl);
        }
    }

    public void unregisterListener(HelloPacketListener pl) {
        synchronized (helloListeners) {
            helloListeners.remove(pl);
        }
    }

    private void notifyAvailablePacket(DatagramPacket packet) {

        if (packet instanceof PacketDataChunk) {
            notifyAvailablePacket((PacketDataChunk) packet);
        } else if (packet instanceof PacketBegin) {
            notifyAvailablePacket((PacketBegin) packet);
        } else if (packet instanceof PacketEnd) {
            notifyAvailablePacket((PacketEnd) packet);
        } else if (packet instanceof DatagramPacket.PacketJoin) {
            notifyAvailablePacket((DatagramPacket.PacketJoin) packet);
        } else if (packet instanceof PacketHello) {
            notifyAvailablePacket((PacketHello) packet);
        }
    }

    private void notifyAvailablePacket(PacketDataChunk packet) {
        synchronized (audioDataListeners) {
            for (AudioDataPacketListener l : audioDataListeners) {
                l.packetArrived(packet);
            }
        }
    }

    private void notifyAvailablePacket(PacketBegin packet) {
        synchronized (audioBeginListeners) {
            for (AudioBeginPacketListener l : audioBeginListeners) {
                l.packetArrived(packet);
            }
        }
    }

    private void notifyAvailablePacket(PacketEnd packet) {
        synchronized (audioEndListeners) {
            for (AudioEndPacketListener l : audioEndListeners) {
                l.packetArrived(packet);
            }
        }
    }

    private void notifyAvailablePacket(DatagramPacket.PacketJoin packet) {
        synchronized (joinListeners) {
            for (JoinPacketListener l : joinListeners) {
                l.packetArrived(packet);
            }
        }
    }

    private void notifyAvailablePacket(PacketHello packet) {
        synchronized (helloListeners) {
            for (HelloPacketListener l : helloListeners) {
                l.packetArrived(packet);
            }
        }
    }
}
