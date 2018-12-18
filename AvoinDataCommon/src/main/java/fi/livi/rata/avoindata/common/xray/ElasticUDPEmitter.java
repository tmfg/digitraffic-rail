package fi.livi.rata.avoindata.common.xray;

import com.amazonaws.xray.config.DaemonConfiguration;
import com.amazonaws.xray.emitters.Emitter;
import com.amazonaws.xray.entities.Segment;
import com.amazonaws.xray.entities.Subsegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;

/**
 * Overrides UDPEmitter implementation that does not resolve the dns name after starting the program.
 */
public class ElasticUDPEmitter extends Emitter {

    private static Logger log = LoggerFactory.getLogger(ElasticUDPEmitter.class);

    private DatagramSocket daemonSocket;
    private DaemonConfiguration config;
    private byte[] sendBuffer = new byte[DAEMON_BUF_RECEIVE_SIZE];

    private String prevAddress = null;

    /**
     * Constructs a UDPEmitter. Sets the daemon address to the value of the {@code AWS_XRAY_DAEMON_ADDRESS} environment variable or {@code com.amazonaws.xray.emitters.daemonAddress} system property, if either are set
     * to a non-empty value. Otherwise, points to {@code InetAddress.getLoopbackAddress()} at port {@code 2000}.
     *
     * @throws SocketException
     *             if an error occurs while instantiating a {@code DatagramSocket}.
     *
     */
    public ElasticUDPEmitter() {
        config = new DaemonConfiguration();
        try {
            daemonSocket = new DatagramSocket();
        } catch (SocketException e) {
            log.error("Exception while instantiating daemon socket.", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @see Emitter#sendSegment(Segment)
     */
    public boolean sendSegment(Segment segment) {
        if (log.isDebugEnabled()) {
            log.debug(segment.prettySerialize());
        }
        return sendData((PROTOCOL_HEADER + PROTOCOL_DELIMITER + segment.serialize()).getBytes());
    }

    /**
     * {@inheritDoc}
     *
     * @see Emitter#sendSubsegment(Subsegment)
     */
    public boolean sendSubsegment(Subsegment subsegment) {
        if (log.isDebugEnabled()) {
            log.debug(subsegment.prettyStreamSerialize());
        }
        return sendData((PROTOCOL_HEADER + PROTOCOL_DELIMITER + subsegment.streamSerialize()).getBytes());
    }

    private boolean sendData(byte[] data) {
        URI uri = null;
        try {
            uri = new URI(config.getUDPAddress());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        final String host = uri.getHost();
        final int port = uri.getPort();

        InetSocketAddress socketAddress;
        if (port == -1 || host == null) {
            socketAddress = config.getAddressForEmitter();
        } else {
            socketAddress =  InetSocketAddress.createUnresolved(host, port);
        }

        String hostAddress = socketAddress.getAddress().getHostAddress();
        if(!hostAddress.equals(prevAddress)) {
            prevAddress = hostAddress;
            log.info("Xray address changed: " + prevAddress);
        }

        // To force resolving ip address via Java TTL time.
        DatagramPacket packet = new DatagramPacket(sendBuffer, DAEMON_BUF_RECEIVE_SIZE, socketAddress);

        packet.setData(data);
        try {
            log.debug("Sending UDP packet.");
            daemonSocket.send(packet);
        } catch (IOException e) {
            log.error("Exception while sending segment over UDP.", e);
            return false;
        }
        return true;
    }

}
