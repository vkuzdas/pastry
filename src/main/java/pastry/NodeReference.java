package pastry;

import java.math.BigInteger;

public class NodeReference {
    public final String ip;
    public final int port;
    public final String id;

    public NodeReference(String ip, int port) {
        this.ip = ip;
        this.port = port;
        this.id = Util.getId(this.getAddress());
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
