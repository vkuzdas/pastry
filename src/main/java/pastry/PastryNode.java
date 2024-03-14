package pastry;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import io.grpc.*;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.Pastry;
import proto.PastryServiceGrpc;

import java.io.IOException;
import java.util.*;

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
    private List<List<NodeReference>> routingTable;
    /**
     * Closest nodes per metric
     */
    private List<NodeReference> neighborSet;
    /**
     * Closest nodes per numerical distance, larger values
     */
    private List<NodeReference> upLeafs;
    /**
     * Closest nodes per numerical distance, smaller values
     */
    private List<NodeReference> downLeafs;
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
                logger.trace("[{}]  ticked", self);
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

    /**
     * Newly joined node <b>X</b> will prompt <b>bootstrap node A</b> to send <i>'join'</i> request around the network <par>
     * Request is routed to node <b>Z</b> which is closest to <b>X</b> <par>
     * Nodes in the routing path will send the node state to <b>X</b>
     */
    public void joinPastry(NodeReference bootstrap) {
        ManagedChannel channel = ManagedChannelBuilder.forTarget(bootstrap.getAddress()).usePlaintext().build();
        blockingStub = PastryServiceGrpc.newBlockingStub(channel);
        Pastry.JoinRequest.Builder request = Pastry.JoinRequest.newBuilder().setIp(self.ip).setPort(self.port);
        Pastry.JoinResponse resp;

        try {
            resp = blockingStub.join(request.build());
            channel.shutdown();
        } catch (StatusRuntimeException e) {
            logger.error("RPC failed: {}, Wrong bootstrap node specified", e.getStatus());
            return;
        }

        updateNodeState(resp);
    }


    /**
     * X is the joining node (self)
     * A is bootstrap node
     * Z is node onto which join request converges since it has closest nodeId to X
     */
    public void updateNodeState(Pastry.JoinResponse resp) {
        // A usually in proximity to X => A.neighborSet to initialize X.neighborSet (set is updated periodically)
        List<Pastry.NodeReference> A_neighborSet = resp.getNodeState(0).getNeighborSetList();
        updateNeighborSet(A_neighborSet);

        // Z has the closest existing nodeId to X, thus its leaf set is the basis for X’s leaf set.
        int len = resp.getNodeStateCount();
        List<Pastry.NodeReference> Z_leafs = resp.getNodeState(len).getLeafSetList();
        updateLeafSet(Z_leafs);

        // Routing table
        updateRoutingTable(resp);
    }


    /**
     * Let A be the first node encouteredin path of join request <br>
     * Let A<sub>0</sub> be the first row of A's routing table <br>
     * - Nodes in A<sub>0</sub> share no common prefix with A, same is true about X, therefore it can be set as X<sub>0</sub> <br>
     * - Nodes in B<sub>1</sub> share the first digit with X, since B and X share it. B<sub>1</sub> can be therefore set as X<sub>1</sub> <br>
     * @param A_routingTable
     */
    private void updateRoutingTable(Pastry.JoinResponse response) {
        int len = response.getNodeStateCount();
        lock.lock();
        try {
            for (int i = 0; i < len; i++) {
                List<Pastry.NodeReference> entries = response
                        .getNodeState(i) // i-th node
                        .getRoutingTable(i) // i-th row
                        .getRoutingTableEntryList();

                routingTable.add(new ArrayList<>());
                entries.forEach(node -> routingTable.get(0).add(new NodeReference(node.getIp(), node.getPort())));
                // TODO: sort entries by distance to prefer closer nodes
            }
        } finally {
            lock.unlock();
        }
    }

    private void updateLeafSet(List<Pastry.NodeReference> Z_leafs) {
        BigInteger selfId = self.getDecimalId();
        lock.lock();
        try {
            Z_leafs.forEach(node -> {
                NodeReference leaf = new NodeReference(node.getIp(), node.getPort());
                if (leaf.getDecimalId().compareTo(selfId) > 0)
                    upLeafs.add(leaf);
                else
                    downLeafs.add(leaf);
            });
        } finally {
            lock.unlock();
        }
    }

    private void updateNeighborSet(List<Pastry.NodeReference> neighborSet) {
        lock.lock();
        try {
            neighborSet.forEach(node -> {
                NodeReference neigh = new NodeReference(node.getIp(), node.getPort());
                neigh.setDistance(Util.getDistance(neigh.getAddress()));
                this.neighborSet.add(new NodeReference(node.getIp(), node.getPort()));
            });
        } finally {
            lock.unlock();
        }
    }

    public NodeReference route(String id) {
        // if network is empty, leafset is empty


        // TODO: implement pastry routing logic
        return self;
    }

    private Pastry.JoinResponse enrichResponse(Pastry.JoinResponse response) {
        // TODO: enrich response with node state of current node
        return response;
    }


    private class PastryNodeServer extends PastryServiceGrpc.PastryServiceImplBase {
        @Override
        public void join(Pastry.JoinRequest request, StreamObserver<Pastry.JoinResponse> responseObserver) {
            NodeReference newNode = new NodeReference(request.getIp(), request.getPort());

            // find the closest node to the new node
            NodeReference closest = route(Util.getId(newNode.getAddress()));

            // reroute newNode's join request to the closest node
            ManagedChannel channel = ManagedChannelBuilder.forTarget(newNode.getAddress()).usePlaintext().build();
            blockingStub = PastryServiceGrpc.newBlockingStub(channel);
            Pastry.JoinResponse response = blockingStub.join(Pastry.JoinRequest.newBuilder().setIp(request.getIp()).setPort(self.port).build());
            channel.shutdown();

            // enrich the response with the state of the current node
            response = enrichResponse(response);

            responseObserver.onNext(response);
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