package pastry;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import proto.Pastry;
import proto.PastryServiceGrpc;

public final class PingResponseTimeDistanceCalculator implements DistanceCalculator {
    private PastryServiceGrpc.PastryServiceBlockingStub blockingStub;
    private static final Pastry.Empty PING = Pastry.Empty.newBuilder().build();

    public PingResponseTimeDistanceCalculator() {

    }

    @Override
    public long calculateDistance(NodeReference self, NodeReference other) {

        if (blockingStub == null) {
            throw new NullPointerException("Blocking stub is not set. Please set the blocking stub using setBlockingStub method.");
        }

        ManagedChannel channel = ManagedChannelBuilder.forTarget(other.getAddress()).usePlaintext().build();
        blockingStub = PastryServiceGrpc.newBlockingStub(channel);

        long startTime = System.nanoTime();
        blockingStub.ping(PING);
        long endTime = System.nanoTime();

        channel.shutdown();
        return endTime - startTime;
    }

    public void setBlockingStub(PastryServiceGrpc.PastryServiceBlockingStub blockingStub) {
        this.blockingStub = blockingStub;
    }
}