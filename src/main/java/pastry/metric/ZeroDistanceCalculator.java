package pastry.metric;

import pastry.NodeReference;
import proto.Pastry;

import java.math.BigInteger;

public class ZeroDistanceCalculator implements DistanceCalculator {
    @Override
    public long calculateDistance(NodeReference self, NodeReference other) {
        return 0L;
    }

    @Override
    public long calculateDistance(NodeReference self, Pastry.NodeReference other) {
        return 0L;
    }
}
