package pastry;

import java.math.BigInteger;
import java.util.Comparator;

public class NodeReference {
    public final String ip;
    public final int port;
    public long distance;
    public final String id;

    public NodeReference(String ip, int port) {
        this.ip = ip;
        this.port = port;
        this.id = Util.getId(this.getAddress());
        this.distance = Integer.MAX_VALUE;
    }

    public NodeReference(String ip, int port, long distance) {
        this.ip = ip;
        this.port = port;
        this.id = Util.getId(this.getAddress());
        this.distance = distance;
    }

    public void setDistance(long distance) {
        this.distance = distance;
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    public long getDistance() {
        // TODO: should be "getMetric" instead and should support at least one other metric
        // WARNING: frequent sort according to distance will be slow
        // TODO: think about saving the distance instead recomputing
        return distance;
    }

    public String getId() {
        return id;
    }

    public BigInteger getDecimalId() {
        return Util.convertToDecimal(id);
    }

    public String getAddress() {
        return ip + ":" + port;
    }

    @Override
    public String toString() {
        return ip + ":" + port + ":" + id;
    }

    @Override
    public boolean equals(Object obj) {
        NodeReference other = (NodeReference) obj;
        return this.port == other.port && this.ip.equals(other.ip);
    }

    public static class NodeReferenceDistanceComparator implements Comparator<NodeReference> {
        @Override
        public int compare(NodeReference o1, NodeReference o2) {
            return Long.compare(o1.getDistance(), o2.getDistance());
        }
    }
    public static class NodeReferenceNumericalComparator implements Comparator<NodeReference> {
        @Override
        public int compare(NodeReference o1, NodeReference o2) {
            return o1.getDecimalId().compareTo(o2.getDecimalId());
        }
    }
}
