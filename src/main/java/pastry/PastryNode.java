package pastry;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import com.google.common.annotations.VisibleForTesting;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.Pastry;
import proto.PastryServiceGrpc;

import java.io.IOException;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static pastry.Util.*;

import static pastry.Constants.*;

public class PastryNode {

    private static final Logger logger = LoggerFactory.getLogger(PastryNode.class);
    private final ReentrantLock lock = new ReentrantLock();
    /**
     * Config parameter determining base of id: ids are 2^b based <br>
     * It is only recommended to use 4-based, 8-based and 16-based
     */
    public static int b = BASE_4_IDS;

    /**
     * Config parameter determining size of leaf set
     * It is only recommended to use 16 and 32
     */
    public static int l = LEAF_SET_SIZE_8;

    /**
     * log(base,N) rows, base columns
     */
    private List<List<String>> R;
    /**
     * Closest nodes per metric
     */
    private List<String> neighborSet;
    /**
     * Closest nodes per numerical distance, larger values
     */
    private List<String> upLeafs;
    /**
     * Closest nodes per numerical distance, smaller values
     */
    private List<String> downLeafs;
    private NodeReference self;

    private final Timer stabilizationTimer = new Timer();
    private TimerTask stabilizationTimerTask;
    public static int STABILIZATION_INTERVAL = 2000;

    private final Server server;
    private PastryServiceGrpc.PastryServiceBlockingStub blockingStub;

    public static void setBase(int b) {
        if (b != BASE_4_IDS && b != Constants.BASE_8_IDS && b != Constants.BASE_16_IDS) {
            throw new IllegalArgumentException("b must be 2, 3 or 4");
        }
        PastryNode.b = b;
    }

    public static void setLeafSize(int size) {
        if (size != LEAF_SET_SIZE_8 && size != Constants.LEAF_SET_SIZE_16 && size != Constants.LEAF_SET_SIZE_32) {
            throw new IllegalArgumentException("L must be 8, 16 or 32");
        }
        PastryNode.l = size;
    }

    public PastryNode(String ip, int port) {
        this.self = new NodeReference(ip, port);

        server = ServerBuilder.forPort(port)
                .addService(new PastryNodeServer())
                .build();
    }

    public void initPastry() throws IOException {
        server.start();
        logger.warn("Server started, listening on {}", self.port);

        logger.trace("[{}]  started FIX", self);
        // periodic stabilization
        stabilizationTimerTask = new TimerTask() {
            @Override
            public void run() {
                int rand = new Random().nextInt(3)-1;
                if (rand == 1) {
                    rand = new Random().nextInt(80)+10000;
                    logger.trace("[{}]  ticking onto {}", self, rand);
                    ManagedChannel channel = ManagedChannelBuilder.forTarget("localhost:"+rand).usePlaintext().build();
                    blockingStub = PastryServiceGrpc.newBlockingStub(channel);
                    blockingStub.join(Pastry.JoinRequest.newBuilder().setPort(self.port).build());
                    channel.shutdown();
                }
            }
        };
        stabilizationTimer.schedule(stabilizationTimerTask,1000, STABILIZATION_INTERVAL);

    }

    public void stopServer() {
        if (server != null) {
            server.shutdownNow();
            logger.warn("Server stopped, listening on {}", self.port);
        }
    }

    public void joinPastry() {}


    private class PastryNodeServer extends PastryServiceGrpc.PastryServiceImplBase {
        @Override
        public void join(Pastry.JoinRequest request, StreamObserver<Pastry.Empty> responseObserver) {
            logger.trace("[{}]  registered a tick from {}", self, request.getPort());
            responseObserver.onNext(Pastry.Empty.newBuilder().build());
            responseObserver.onCompleted();
        }
    }

    public static void main(String[] args) throws IOException {
        ArrayList<PastryNode> toShutdown = new ArrayList<>();
        for (int i = 0; i < 80; i++) {
            PastryNode node = new PastryNode("localhost", 10000 + i);
            node.initPastry();
            toShutdown.add(node);
        }
    }
}