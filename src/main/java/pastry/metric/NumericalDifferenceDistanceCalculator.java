package pastry.metric;

import pastry.NodeReference;
import proto.Pastry;

import java.math.BigInteger;

/**
 * This Calculator should not be used with 16-base nodeIds (those are too big for longs)
 */
public class NumericalDifferenceDistanceCalculator implements DistanceCalculator {
    @Override
    public long calculateDistance(NodeReference self, NodeReference other) {
        BigInteger selfId = self.getDecimalId();
        BigInteger otherId = self.getDecimalId();
        return selfId.subtract(otherId).abs().longValue();
    }

    @Override
    public long calculateDistance(NodeReference self, Pastry.NodeReference other) {
        BigInteger selfId = self.getDecimalId();
        BigInteger otherId = self.getDecimalId();
        return selfId.subtract(otherId).abs().longValue();
    }
}
