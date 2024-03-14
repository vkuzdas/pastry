package pastry;

import java.math.BigInteger;

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

}
