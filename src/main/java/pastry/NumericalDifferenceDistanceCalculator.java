package pastry;

import java.math.BigInteger;
import java.util.Random;

/**
 * This Calculator should not be used with 16-base nodeIds
 */
public class NumericalDifferenceDistanceCalculator implements DistanceCalculator {
    @Override
    public long calculateDistance(NodeReference self, NodeReference other) {
        BigInteger selfId = self.getDecimalId();
        BigInteger otherId = self.getDecimalId();

        return selfId.subtract(otherId).abs().longValue();
    }

}
