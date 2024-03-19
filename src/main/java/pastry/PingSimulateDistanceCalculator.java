package pastry;

import java.util.Random;

public class PingSimulateDistanceCalculator implements DistanceCalculator {
    @Override
    public long calculateDistance(NodeReference self, NodeReference other) {
        return new Random().nextInt(500) + 500;
    }
}
