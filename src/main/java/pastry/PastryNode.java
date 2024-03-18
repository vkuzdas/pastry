package pastry;

import java.math.BigInteger;
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
import java.util.*;

import static pastry.Constants.*;

public class PastryNode {

    private static final Logger logger = LoggerFactory.getLogger(PastryNode.class);
    /**
     * Whether Pastry is tested locally, meaning same host, different ports. This would imply same metric distance for all nodes. <br>
     * The flag therefore simulates constant metric distance defined as port difference between two nodes. <br>
     * @see Util#getDistance
     */
    public static boolean LOCAL_TESTING = true;
    private final ReentrantLock lock = new ReentrantLock();
    /**
     * Config parameter determining base of id: ids are 2^b based <br>
     * It is only recommended to use 4-based, 8-based and 16-based
     */
    public static int B_PARAMETER = BASE_4_IDS;

    /**
     * Config parameter determining size of leaf set
     * It is only recommended to use 16 and 32
     */
    public static int L_PARAMETER = LEAF_SET_SIZE_8;

    /**
     * log(base,N) rows, base columns
     */
    private final List<List<NodeReference>> routingTable = new ArrayList<>(8); // TODO: set as number of digits in id
    /**
     * Closest nodes per metric
     */
    private final SortedSet<NodeReference> neighborSet = Collections.synchronizedSortedSet(new TreeSet<>(new NodeReference.NodeReferenceDistanceComparator()));
    /**
     * Closest nodes per numerical distance, larger values
     */
    private final SortedSet<NodeReference> upLeafs = Collections.synchronizedSortedSet(new TreeSet<>(new NodeReference.NodeReferenceNumericalComparator()));
    /**
     * Closest nodes per numerical distance, smaller values
     */
    private final SortedSet<NodeReference> downLeafs = Collections.synchronizedSortedSet(new TreeSet<>(new NodeReference.NodeReferenceNumericalComparator()));
    private final NodeReference self;

    private final Timer stabilizationTimer = new Timer();
    public static int STABILIZATION_INTERVAL = 2000;

    private final Server server;
    private PastryServiceGrpc.PastryServiceBlockingStub blockingStub;

    public static void setLocalTesting(boolean localTesting) {
        LOCAL_TESTING = localTesting;
    }

    public static void setBase(int b) {
        if (b != BASE_4_IDS && b != Constants.BASE_8_IDS && b != Constants.BASE_16_IDS) {
            throw new IllegalArgumentException("b must be 2, 3 or 4");
        }
        PastryNode.B_PARAMETER = b;
    }

    public static void setLeafSize(int size) {
        if (size != LEAF_SET_SIZE_8 && size != Constants.LEAF_SET_SIZE_16 && size != Constants.LEAF_SET_SIZE_32) {
            throw new IllegalArgumentException("L must be 8, 16 or 32");
        }
        PastryNode.L_PARAMETER = size;
    }

    public NodeReference getNode() {
        return self;
    }

    @VisibleForTesting
    public SortedSet<NodeReference> getNeighborSet() {
        return neighborSet;
    }

    @VisibleForTesting
    public SortedSet<NodeReference> getUpLeafs() {
        return upLeafs;
    }

    @VisibleForTesting
    public SortedSet<NodeReference> getDownLeafs() {
        return downLeafs;
    }

    @VisibleForTesting
    public List<List<NodeReference>> getRoutingTable() {
        return routingTable;
    }

    public PastryNode(String ip, int port) {
        this.self = new NodeReference(ip, port);

        server = ServerBuilder.forPort(port)
                .addService(new PastryNodeServer())
                .build();

        for (int i = 0; i < 8; i++) {
            routingTable.add(new ArrayList<>());
        }
    }

    public NodeReference syncRoutingTableGet(int row, int col, List<List<NodeReference>> rt) {
        NodeReference r;
        lock.lock();
        try {
            r = rt.get(row).get(col);
        } finally {
            lock.unlock();
        }
        return r;
    }

    public void initPastry() throws IOException {
        startServer();
        logger.trace("[{}]  started FIX", self);
        startStabilizationThread();
    }

    private void startStabilizationThread() {
        // periodic stabilization
        // TODO: implement M periodic update
        // See 3.2 Maintaining the network
        TimerTask stabilizationTimerTask = new TimerTask() {
            @Override
            public void run() {
                logger.trace("[{}]  ticked", self);
                // TODO: implement M periodic update
                // See 3.2 Maintaining the network
            }
        };
        stabilizationTimer.schedule(stabilizationTimerTask,1000, STABILIZATION_INTERVAL);
    }

    public void shutdownPastryNode() {
        stopServer();
        stabilizationTimer.cancel();
    }

    private void startServer() throws IOException {
        server.start();
        logger.warn("Server started, listening on {}", self.port);
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
    public void joinPastry(NodeReference bootstrap) throws IOException {
        startServer();
        ManagedChannel channel = ManagedChannelBuilder.forTarget(bootstrap.getAddress()).usePlaintext().build();
        blockingStub = PastryServiceGrpc.newBlockingStub(channel);
        Pastry.JoinRequest.Builder request = Pastry.JoinRequest.newBuilder().setIp(self.ip).setPort(self.port);
        Pastry.JoinResponse resp;

        try {
            logger.trace("[{}]  Joining using {}", self, bootstrap);
            resp = blockingStub.join(request.build());
            channel.shutdown();
        } catch (StatusRuntimeException e) {
            channel.shutdown();
            logger.error("RPC failed: {}, Wrong bootstrap node specified", e.getStatus());
            return;
        }

        updateNodeState(resp);
        startStabilizationThread();
    }

    /**
     * Return the closest node to the given id (based on prefix or numerically)
     * @param id_base 4/8/16-based id (given by {@link PastryNode#B_PARAMETER})
     */
    public NodeReference route(String id_base) {
        if (neighborSet.isEmpty()) {
            // network is empty, self is closest by definition
            return self;
        }

        if (!downLeafs.isEmpty() && !upLeafs.isEmpty()) {
            BigInteger id_dec = Util.convertToDecimal(id_base);
            BigInteger firstLeaf_dec = downLeafs.first().getDecimalId();
            BigInteger lastLeaf_dec = upLeafs.last().getDecimalId();
            // if id in L range
            if (firstLeaf_dec.compareTo(id_dec) <= 0 &&  id_dec.compareTo(lastLeaf_dec) <= 0) {
                logger.trace("[{}]  Routing {} to numerically closest leaf", self, id_base);
                return getNumericallyClosestLeaf(id_dec);
            }
        }
        // routing table
        else {
            int l = getSharedPrefixLength(id_base, self.getId());
            NodeReference longerMatch;
            try {
                // forward to a node that shares common prefix with the id_base by at least one more digit
                // R[l+1][id[l]+1]
                longerMatch = syncRoutingTableGet(l+1, id_base.charAt(l), routingTable);
                return longerMatch;
            } catch (IndexOutOfBoundsException e) {
                // forward to a node that shares prefix with the key at least as long as the local node
                // and is numerically closer to the key than the present node’s id.
                // TODO: ...or if R[l+1][idD[l]+1] not reachable
                return findSameLengthMatch(id_base, l);
            }
        }
        return self;
    }

    /**
     * Find to a node that shares prefix with the key at least as long as the local node and is numerically closer to the key than the present node’s id.
     */
    private NodeReference findSameLengthMatch(String id_base, int l) {
        lock.lock();
        try {
            // start by searching R[l] entries (same len entries)
            for (int i = l; i < routingTable.size(); i++) {
                List<NodeReference> row = routingTable.get(l);
                for(NodeReference n : row) {
                    if (neighborSet.contains(n) && (upLeafs.contains(n) || downLeafs.contains(n)) ){
                        BigInteger t = n.getDecimalId();
                        BigInteger k = Util.convertToDecimal(id_base);
                        BigInteger s = self.getDecimalId();
                        if (t.subtract(k).abs().compareTo(s.subtract(k).abs()) < 0) {
                            return n;
                        }
                    }
                }
            }
        } finally {
            lock.unlock();
        }
        return self;
    }

    private int getSharedPrefixLength(String idBase, String selfId) {
        int l = 0;
        for (int i = 0; i < idBase.length(); i++) {
            if (idBase.charAt(i) == selfId.charAt(i)) {
                l++;
            } else {
                break;
            }
        }
        return l;
    }

    /**
     * Return the closest leaf to the given id
     */
    private NodeReference getNumericallyClosestLeaf(BigInteger idDec) {
        // Iterating over SynchronizedSortedSet still has to be locked
        lock.lock();
        NodeReference closest = null;
        try {
            BigInteger minDistance = BigInteger.valueOf(Long.MAX_VALUE);
            for (NodeReference leaf : upLeafs) {
                BigInteger distance = leaf.getDecimalId().subtract(idDec).abs();
                if (distance.compareTo(minDistance) < 0) {
                    minDistance = distance;
                    closest = leaf;
                }
            }

            for (NodeReference leaf : downLeafs) {
                BigInteger distance = leaf.getDecimalId().subtract(idDec).abs();
                if (distance.compareTo(minDistance) < 0) {
                    minDistance = distance;
                    closest = leaf;
                }
            }
        } finally {
            lock.unlock();
        }

        return closest;
    }

    /**
     * Insert new NodeState corresponding to NodeState of current node <br>
     * Note that NodeState list is stack-like: Z node is inserted first, A last
     */
    private Pastry.JoinResponse enrichResponse(Pastry.JoinResponse response) {
        Pastry.NodeState.Builder stateBuilder = Pastry.NodeState.newBuilder();
        Pastry.NodeReference.Builder nodeBuilder = Pastry.NodeReference.newBuilder();
        Pastry.RoutingTableRow.Builder rowBuilder = Pastry.RoutingTableRow.newBuilder();


        lock.lock();
        try {
            // WARN: It is imperative that the user manually synchronize on the sorted set when traversing it
            neighborSet.forEach(n ->
                    stateBuilder.addNeighborSet(nodeBuilder.setIp(n.getIp()).setPort(n.getPort()).build())
            );
            downLeafs.forEach(n ->
                    stateBuilder.addLeafSet(nodeBuilder.setIp(n.getIp()).setPort(n.getPort()).build())
            );
            upLeafs.forEach(n ->
                    stateBuilder.addLeafSet(nodeBuilder.setIp(n.getIp()).setPort(n.getPort()).build())
            );
            routingTable.forEach(row -> {
                row.forEach(n ->
                        rowBuilder.addRoutingTableEntry(nodeBuilder.setIp(n.getIp()).setPort(n.getPort()).build())
                );
                stateBuilder.addRoutingTable(rowBuilder.build());
            });
        } finally {
            lock.unlock();
        }

        stateBuilder.setOwner(nodeBuilder.setIp(self.getIp()).setPort(self.getPort()).build());
        return Pastry.JoinResponse.newBuilder(response)
                .addNodeState(stateBuilder.build())
                .build();
    }


    /**
     * X is the joining node (self) <br>
     * A is bootstrap node <br>
     * Z is node onto which join request converges since it has closest nodeId to X <br>
     */
    public void updateNodeState(Pastry.JoinResponse resp) {
        // A usually in proximity to X => A.neighborSet to initialize X.neighborSet (set is updated periodically)
        int len = resp.getNodeStateCount();
        List<Pastry.NodeReference> A_neighborSet = resp.getNodeState(len-1).getNeighborSetList();
        A_neighborSet.forEach(node -> {
            insertIntoNeighborSet(new NodeReference(node.getIp(), node.getPort()));
        });

        // Z has the closest existing nodeId to X, thus its leaf set is the basis for X’s leaf set.
        List<Pastry.NodeReference> Z_leafs = resp.getNodeState(0).getLeafSetList();
        Z_leafs.forEach(node -> {
            insertIntoLeafSet(new NodeReference(node.getIp(), node.getPort()));
        });

        // Routing table
        initRoutingTable(resp);

        for (Pastry.NodeState node : resp.getNodeStateList()) {
            registerNewNode(new NodeReference(node.getOwner().getIp(), node.getOwner().getPort()));
        }
    }


    /**
     * Let A be the first node encoutered in path of join request <br>
     * Let A<sub>0</sub> be the first row of A's routing table <br>
     * - Nodes in A<sub>0</sub> share no common prefix with A, same is true about X, therefore it can be set as X<sub>0</sub> <br>
     * - Nodes in B<sub>1</sub> share the first digit with X, since B and X share it. B<sub>1</sub> can be therefore set as X<sub>1</sub> <br>
     */
    private void initRoutingTable(Pastry.JoinResponse response) {
        int len = response.getNodeStateCount();
        lock.lock();
        try {
            for (int i = 0; i < len-1; i++) {
                List<Pastry.NodeReference> entries = response
                        .getNodeState(i) // i-th node
                        .getRoutingTable(i) // i-th row
                        .getRoutingTableEntryList();

                routingTable.add(new ArrayList<>());
                entries.forEach(node -> routingTable.get(0).add(new NodeReference(node.getIp(), node.getPort())));
                routingTable.get(0).sort(Comparator.comparing(NodeReference::getDistance));
            }
        } finally {
            lock.unlock();
        }
    }


    /**
     * Insert node into neighborSet <br>
     * If the neighborSet is full and newNode is <b>numerically<b/>closer, remove the farthest node and insert newNode
     */
    private void insertIntoNeighborSet(NodeReference newNode) {
        // if full, insert only if newNode is better
        if (neighborSet.size() == L_PARAMETER) {
            if (newNode.getDistance() < neighborSet.last().getDistance()) {
                neighborSet.remove(neighborSet.last());
                neighborSet.add(newNode);
            }
        }
        else {
            neighborSet.add(newNode);
        }
    }

    /**
     * Insert node into <b>appropriate (up/down)</b> leafSet <br>
     * If the leaf set is full and newNode is <b>numerically<b/> closer, remove the last node and insert the new node
     */
    private void insertIntoLeafSet(NodeReference newNode) {
        SortedSet<NodeReference> leafSet = newNode.getDecimalId().compareTo(self.getDecimalId()) < 0 ? downLeafs : upLeafs;

        // if full, insert only if newNode is better
        if (leafSet.size() == L_PARAMETER/2) {
            // farthest nodes are downleafs.first() or upleafs.last()
            NodeReference numericallyFarthest = newNode.getDecimalId().compareTo(self.getDecimalId()) < 0 ? downLeafs.first() : upLeafs.last();
            if( newNode.getDecimalId().compareTo(numericallyFarthest.getDecimalId()) < 0) {
                leafSet.remove(numericallyFarthest);
                leafSet.add(newNode);
            }
        }
        else {
            leafSet.add(newNode);
        }
    }

    private void syncInsertIntoRoutingTable(NodeReference newNode) {
        int l = getSharedPrefixLength(newNode.getId(), self.getId());
        lock.lock();
        try {
            final List<NodeReference> row = routingTable.get(l);
            if (row.size() == Math.pow(2, B_PARAMETER)-1) {
                NodeReference farthest = row.get(row.size()-1);
                if (newNode.getDistance() < farthest.getDistance() && !row.contains(newNode)) {
                    row.remove(farthest);
                    row.add(newNode);
                }
            }
            else {
                if (!row.contains(newNode))
                    row.add(newNode);
            }
            row.sort(Comparator.comparing(NodeReference::getDistance));
        } finally {
            lock.unlock();
        }
    }

    private void registerNewNode(NodeReference newNode) {
        if (newNode.getDistance() == Long.MAX_VALUE) {
            newNode.setDistance(Util.getDistance(newNode.getAddress(), self.getAddress()));
        }
        insertIntoNeighborSet(newNode);
        insertIntoLeafSet(newNode);
        syncInsertIntoRoutingTable(newNode);
    }


    /**
     * Server-side of Pastry node
     */
    private class PastryNodeServer extends PastryServiceGrpc.PastryServiceImplBase {

        /**
         * NodeState in Pastry.JoinResponse.NodeState is stack-like: Z node is inserted first, A last
         */
        @Override
        public void join(Pastry.JoinRequest request, StreamObserver<Pastry.JoinResponse> responseObserver) {
            logger.trace("[{}]  Join request from {}:{}", self, request.getIp(), request.getPort());
            NodeReference newNode = new NodeReference(request.getIp(), request.getPort());

            // find the closest node to the new node
            NodeReference closest = route(Util.getId(newNode.getAddress()));

            // either node is alone or it is the Z node itself
            if (closest.equals(self)) {
                Pastry.JoinResponse response = Pastry.JoinResponse.newBuilder().build();
                response = enrichResponse(response);
                logger.trace("[{}]  My id is the closest to {}, responding back", self, newNode.getId());
                responseObserver.onNext(response);
                responseObserver.onCompleted();
                registerNewNode(newNode);
                return;
            }

            // reroute newNode's join request to the closest node
            logger.trace("[{}]  Forwarding to {}", self, closest.getAddress());
            ManagedChannel channel = ManagedChannelBuilder.forTarget(closest.getAddress()).usePlaintext().build();
            blockingStub = PastryServiceGrpc.newBlockingStub(channel);
            Pastry.JoinResponse response = blockingStub.join(Pastry.JoinRequest.newBuilder()
                    .setIp(request.getIp())
                    .setPort(request.getPort())
                    .build());
            channel.shutdown();

            // enrich the response with the state of the current node
            response = enrichResponse(response);

            responseObserver.onNext(response);
            responseObserver.onCompleted();

            // finally register the node into your nodestate
            registerNewNode(newNode);
        }
    }


    public static void main(String[] args) throws IOException, InterruptedException {
//        ArrayList<PastryNode> toShutdown = new ArrayList<>();
//        for (int i = 0; i < 80; i++) {
//            PastryNode node = new PastryNode("localhost", 10000 + i);
//            node.initPastry();
//            toShutdown.add(node);
//        }

        PastryNode bootstrap = new PastryNode("localhost", 10_000);
        bootstrap.initPastry();

        PastryNode node1 = new PastryNode("localhost", 10_001);
        PastryNode node2 = new PastryNode("localhost", 10_002);

        node1.joinPastry(bootstrap.getNode());
        node2.joinPastry(node1.getNode());
    }
}















