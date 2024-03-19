package pastry;

public class PortDifferenceDistanceCalculator implements DistanceCalculator {
    @Override
    public long calculateDistance(NodeReference node1, NodeReference node2) {
        return Math.abs(node1.getPort() - node2.getPort());
    }
}