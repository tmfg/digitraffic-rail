package fi.livi.rata.avoindata.common.xray;

import com.amazonaws.xray.config.DaemonConfiguration;
import com.amazonaws.xray.emitters.Emitter;
import com.amazonaws.xray.entities.Segment;
import com.amazonaws.xray.entities.Subsegment;
import com.google.common.net.InetAddresses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;

/**
 * Overrides UDPEmitter implementation. Elastic refer that the emiter will resolve XRAY address always via Java internal services
 * and obey dns TTL times.
 */
public class ElasticUDPEmitter extends Emitter {

    private static Logger log = LoggerFactory.getLogger(ElasticUDPEmitter.class);

    private final DatagramSocket daemonSocket;
    private final DaemonConfiguration config;
    private byte[] sendBuffer = new byte[DAEMON_BUF_RECEIVE_SIZE];

    private volatile String prevAddress = null;
    private final URI uri;
    private final boolean enableDNSResolver;

    public ElasticUDPEmitter() {
        config = new DaemonConfiguration();
        try {
            daemonSocket = new DatagramSocket();
            uri = new URI("any://" + config.getUDPAddress());
        } catch (SocketException | URISyntaxException e) {
            log.error("Exception while instantiating daemon socket.", e);
            throw new RuntimeException(e);
        }

        enableDNSResolver = isHostPortConfiguredAndHostIsDNSName(uri.getHost(), uri.getPort());
        log.info("UDP XRAY-daemon " + config.getUDPAddress() + " and DNS resolving is set to " + enableDNSResolver);
    }

    /**
     * {@inheritDoc}
     *
     * @see Emitter#sendSegment(Segment)
     */
    public boolean sendSegment(Segment segment) {
        if (log.isTraceEnabled()) {
            log.trace(segment.prettySerialize());
        }
        return sendData((PROTOCOL_HEADER + PROTOCOL_DELIMITER + segment.serialize()).getBytes());
    }

    /**
     * {@inheritDoc}
     *
     * @see Emitter#sendSubsegment(Subsegment)
     */
    public boolean sendSubsegment(Subsegment subsegment) {
        if (log.isTraceEnabled()) {
            log.trace(subsegment.prettyStreamSerialize());
        }
        return sendData((PROTOCOL_HEADER + PROTOCOL_DELIMITER + subsegment.streamSerialize()).getBytes());
    }

    private boolean sendData(byte[] data) {
        DatagramPacket packet = new DatagramPacket(sendBuffer, DAEMON_BUF_RECEIVE_SIZE, resolveCurrentAddress());

        packet.setData(data);
        try {
            log.trace("Sending UDP packet.");
            daemonSocket.send(packet);
        } catch (IOException e) {
            log.error("Exception while sending segment over UDP.", e);
            return false;
        }

        return true;
    }

    private InetSocketAddress resolveCurrentAddress() {
        InetSocketAddress socketAddress;

        if (enableDNSResolver) {
            socketAddress = resolveAddress(uri);
        } else {
            // Using default configuration of JDK XRAY
            socketAddress = config.getAddressForEmitter();
        }

        return socketAddress;
    }

    private InetSocketAddress resolveAddress(URI uri) {
        String resolvedAddress;

        try {
            resolvedAddress = InetAddress.getByName(uri.getHost()).getHostAddress();
        } catch (UnknownHostException e) {
            resolvedAddress = prevAddress;
            log.warn("Error resolving XRAY host. Using previous address.", e);
        }

        logIfChanged(resolvedAddress);

        return new InetSocketAddress(resolvedAddress, uri.getPort());
    }

    private synchronized void logIfChanged(String resolvedAddress) {
        if(!resolvedAddress.equals(prevAddress)) {
            log.info("XRAY address changed: " + prevAddress + " -> " + resolvedAddress);
            prevAddress = resolvedAddress;
        }
    }

    private static boolean isHostPortConfiguredAndHostIsDNSName(String host, int port) {
        return port > 0 && host != null && !InetAddresses.isInetAddress(host);
    }
}
