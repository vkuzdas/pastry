package pastry;

public interface DistanceCalculator {
    long calculateDistance(NodeReference self, NodeReference other);
}