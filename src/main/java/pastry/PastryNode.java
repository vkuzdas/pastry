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
import static proto.Pastry.ForwardRequest.RequestType.GET;
import static proto.Pastry.ForwardRequest.RequestType.PUT;
import static proto.Pastry.ForwardResponse.StatusCode.*;

public class PastryNode {

    private static final Logger logger = LoggerFactory.getLogger(PastryNode.class);
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
    private final NavigableMap<String, String> localData = new TreeMap<>();

    /**
     * log(base,N) rows, base columns
     */
    private final List<List<NodeReference>> routingTable = new ArrayList<>(8); // TODO: set as number of digits in id
    /**
     * Closest nodes per metric
     */
//    private final List<NodeReference> neighborSet = new ArrayList<>();
    /**
     * Numerically closest larger nodeIds
     */
    private final List<NodeReference> upLeafs = new ArrayList<>();
    /**
     * Nodes with numerically closest smaller nodeIds
     */
    private final List<NodeReference> downLeafs = new ArrayList<>();
    private final NodeReference self;

    private final Timer stabilizationTimer = new Timer();
    public static int STABILIZATION_INTERVAL = 5000;
    private static final Pastry.Empty PING = Pastry.Empty.newBuilder().build();

    private final Server server;
    private PastryServiceGrpc.PastryServiceBlockingStub blockingStub;
    private DistanceCalculator distanceCalculator = new PingResponseTimeDistanceCalculator();

    public class PingResponseTimeDistanceCalculator implements DistanceCalculator {
        // TODO: adapter pattern is not congruent: this is the only Calculator that cannot be separated from class
        @Override
        public long calculateDistance(NodeReference self, NodeReference other) {
            ManagedChannel channel = ManagedChannelBuilder.forTarget(other.getAddress()).usePlaintext().build();
            blockingStub = PastryServiceGrpc.newBlockingStub(channel);

            long startTime = System.nanoTime();
            blockingStub.ping(PING);
            long endTime = System.nanoTime();

            channel.shutdown();
            return endTime - startTime;
        }
    }

    public void setDistanceCalculator(DistanceCalculator distanceCalculator) {
        this.distanceCalculator = distanceCalculator;
    }

    public DistanceCalculator getDistanceCalculator() {
        return distanceCalculator;
    }

    public static void setBase(int b) {
        if (b != BASE_4_IDS && b != Constants.BASE_8_IDS && b != Constants.BASE_16_IDS) {
            throw new IllegalArgumentException("B must be 2, 3 or 4");
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
    public List<NodeReference> getNeighborSet() {
        return null;
    }

    @VisibleForTesting
    public List<NodeReference> getUpLeafs() {
        return upLeafs;
    }

    @VisibleForTesting
    public List<NodeReference> getDownLeafs() {
        return downLeafs;
    }

    @VisibleForTesting
    public List<NodeReference> getLeafs() {
        List<NodeReference> leafs = new ArrayList<>();
        leafs.addAll(downLeafs);
        leafs.addAll(upLeafs);
        return leafs;
    }

    @VisibleForTesting
    public List<List<NodeReference>> getRoutingTable() {
        return routingTable;
    }

    @VisibleForTesting
    public void turnOffStabilization() {
        stabilizationTimer.cancel();
    }

    public PastryNode(String ip, int port) {
        this.self = new NodeReference(ip, port);

        server = ServerBuilder.forPort(port)
                .addService(new PastryNodeServer())
                .build();

        for (int i = 0; i < 8; i++) {
            ArrayList<NodeReference> emptyRow = new ArrayList<>();
            for (int j = 0; j < (int)Math.pow(2, B_PARAMETER); j++) {
                emptyRow.add(null);
            }
            routingTable.add(emptyRow);
        }
    }

    public NodeReference syncGet(int index, List<NodeReference> list) {
        NodeReference r;
        lock.lock();
        try {
            r = list.get(index);
        } finally {
            lock.unlock();
        }
        return r;
    }

    public NodeReference syncLastGet(List<NodeReference> list) {
        NodeReference r;
        lock.lock();
        try {
            r = list.get(list.size()-1);
        } finally {
            lock.unlock();
        }
        return r;
    }

    public int syncSizeGet(List<NodeReference> list) {
        int s;
        lock.lock();
        try {
            s = list.size();
        } finally {
            lock.unlock();
        }
        return s;
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
//        startStabilizationThread();
    }

    /**
     * <i>Node attempts to contact each member
     * of the neighborhood set periodically to see if it is still alive. If a member is not responding,
     * the node asks other members for their neighborhood tables, checks the distance of
     * each of the newly discovered nodes, and updates it own neighborhood set accordingly.</i>
     * <p>
     *     There is probably no other way around it then to always send back whole neighbor set. <br>
     *     This is probably pretty costly and therefore the timer should tick less frequently. <br>
     * </p>
     */
    private void startStabilizationThread() {
        TimerTask stabilizationTimerTask = new TimerTask() {
            @Override
            public void run() {
                logger.trace("[{}]  checking neighbors..", self);
                // make copy of current neighbor set
                // iterate over it and request their neighbors
                // fill neighborCandidates
//                List<NodeReference> currentNeigborSet;
//                List<NodeReference> neighborCandidates = new ArrayList<>();
//                List<NodeReference> toRemove = new ArrayList<>();
//
//                lock.lock();
//                try {
//                    currentNeigborSet = new ArrayList<>(neighborSet);
//                } finally {
//                    lock.unlock();
//                }
//
//                // iterate over neighbors, check liveness of each
//                for (NodeReference neighbor : currentNeigborSet) {
//                    ManagedChannel channel = ManagedChannelBuilder.forTarget(neighbor.getAddress()).usePlaintext().build();
//                    blockingStub = PastryServiceGrpc.newBlockingStub(channel);
//                    Pastry.NeighborSetResponse response;
//                    try {
//                        response = blockingStub.getNeighborSet(Pastry.NeighborSetRequest.newBuilder().build());
//                        for (Pastry.NodeReference n : response.getNeighborSetList()) {
//                            neighborCandidates.add(new NodeReference(n.getIp(), n.getPort()));
//                        }
//                    }
//                    catch (StatusRuntimeException e) {
//                        // neighbor is down, remove and find new
//                        channel.shutdown();
//                        logger.error("[{}]  status of [{}] is {}, removing from NodeState", self, neighbor, e.getStatus().getCode());
//                        toRemove.add(neighbor);
//                    }
//                    channel.shutdown();
//                }
//                for(NodeReference n : toRemove) {
//                    syncRemoveFromNodeState(n);
//                }
//                for (NodeReference candidate : neighborCandidates) {
//                    if (!toRemove.contains(candidate)) {
//                        // register has "already-contains" check
//                        registerNewNode(candidate);
//                    }
//                }
            }
        };
//        stabilizationTimer.schedule(stabilizationTimerTask,1000, STABILIZATION_INTERVAL);
    }

//    /**
//     * Return the <b>first</b> neighbor's neighbor that is not in the current neighbor set
//     */
//    private NodeReference getNewNeighbor() {
//        lock.lock();
//        NodeReference newNode = null;
//        try {
//            for (NodeReference neighbor : neighborSet) {
//                ManagedChannel channel = ManagedChannelBuilder.forTarget(neighbor.getAddress()).usePlaintext().build();
//                blockingStub = PastryServiceGrpc.newBlockingStub(channel);
//                Pastry.NeighborSetResponse response = blockingStub.getNeighborSet(Pastry.NeighborSetRequest.newBuilder().build());
//                for (Pastry.NodeReference n : response.getNeighborSetList()) {
//                    newNode = new NodeReference(n.getIp(), n.getPort());
//                    if (!neighborSet.contains(newNode) && !newNode.equals(self)) {
//                        return newNode;
//                    }
//                }
//                channel.shutdown();
//            }
//        } finally {
//            lock.unlock();
//        }
//        return newNode;
//    }

//    public void syncRemoveFromNodeState(NodeReference neighbor) {
//        lock.lock();
//        try {
//            neighborSet.remove(neighbor);
//
//            List<NodeReference> leafSet = neighbor.getDecimalId().compareTo(self.getDecimalId()) < 0 ? downLeafs : upLeafs;
//            leafSet.remove(neighbor);
//
//            int l = getSharedPrefixLength(neighbor.getId(), self.getId());
//            List<NodeReference> row = routingTable.get(l);
//            row.remove(neighbor);
//        } finally {
//            lock.unlock();
//        }
//    }

    public void shutdownPastryNode() {
        stopServer();
        stabilizationTimer.cancel();
    }

    private void startServer() throws IOException {
        server.start();
        logger.warn("[{}]  Server started, listening on {}", self, self.port);
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
    public NodeReference joinPastry(NodeReference bootstrap) throws IOException {
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
            return self;
        }

        Pastry.NodeReference owner = resp.getNodeState(0).getOwner();
        NodeReference closest = new NodeReference(owner.getIp(), owner.getPort());

        updateNodeState(resp);
        notifyAboutMyself(bootstrap);
//        startStabilizationThread();

        // this is not in actual Pastry API, it is however used to verify that we have reached the actual CLOSEST node
        return closest;
    }

    /**
     * <i>Once X has constructed its leaf set and routing table, X sends its contents to all nodes identified in
     * the leaf set and the routing table. The nodes that receive these updates, adjust their own tables to
     * incorporate the node. </i> <br> <br>
     *
     * The joining node will gather all known nodes and notify them about itself and its NodeState <br>
     * The addressed nodes will reflect joining node in its NodeState, <br>
     * as well as send back nodes from its NodeState that have not been sent by the joining node. <br>
     * Joining node will then notify newly discovered nodes about itself and its NodeState <br>
     * Finally, joining node will reflect all newly discovered nodes into its NodeState
     * @see PastryNodeServer#notifyExistence(Pastry.NodeState, StreamObserver)
     */
    private void notifyAboutMyself(NodeReference bootstrap) {
        ArrayList<NodeReference> myNodes = getAllMyNodes();
        myNodes.remove(bootstrap);
        ArrayList<NodeReference> toNotify = new ArrayList<>(myNodes);
        ArrayList<NodeReference> newlyDiscovered = new ArrayList<>();
        ArrayList<NodeReference> notified = new ArrayList<>();

        while (!toNotify.isEmpty()) {
            NodeReference n = toNotify.remove(0);

            ManagedChannel channel = ManagedChannelBuilder.forTarget(n.getAddress()).usePlaintext().build();
            blockingStub = PastryServiceGrpc.newBlockingStub(channel);
            Pastry.NewNodes newNodes = blockingStub.notifyExistence(getMyNodeState());
            channel.shutdown();

            newNodes.getNodesList().forEach(newNode -> {
                NodeReference newRef = new NodeReference(newNode.getIp(), newNode.getPort());

                if (self.equals(newRef)) return;//skip self

                if (!notified.contains(newRef) && !toNotify.contains(newRef)) {
                    toNotify.add(newRef);
                }
                if (!myNodes.contains(newRef) && !newlyDiscovered.contains(newRef)) {
                    newlyDiscovered.add(newRef);
                }
            });

            notified.add(n);
            newlyDiscovered.forEach(node -> {
                if (!toNotify.contains(node) && !notified.contains(node)) {
                    toNotify.add(node);
                }
            });
        }

        newlyDiscovered.forEach(this::registerNewNode);
        logger.trace("[{}]  Notified: {}", self, notified);
        logger.trace("[{}]  Newly discovered: {}", self, newlyDiscovered);
    }

    public ArrayList<NodeReference> getAllMyNodes() {
        ArrayList<NodeReference> allMyNodes = new ArrayList<>();
        lock.lock();
        try {
//            neighborSet.forEach(n -> {
//                if (!allMyNodes.contains(n))
//                    allMyNodes.add(n);
//            });
            downLeafs.forEach(n -> {
                if (!allMyNodes.contains(n))
                    allMyNodes.add(n);
            });
            upLeafs.forEach(n -> {
                if (!allMyNodes.contains(n))
                    allMyNodes.add(n);
            });
            routingTable.forEach(row -> row.forEach(n -> {
                if (n != null && !allMyNodes.contains(n))
                    allMyNodes.add(n);
            }));
        } finally {
            lock.unlock();
        }
        return allMyNodes;
    }

    private Pastry.NodeState getMyNodeState() {
        Pastry.NodeState.Builder stateBuilder = Pastry.NodeState.newBuilder();
        Pastry.NodeReference.Builder nodeBuilder = Pastry.NodeReference.newBuilder();
        Pastry.RoutingTableRow.Builder rowBuilder = Pastry.RoutingTableRow.newBuilder();

        lock.lock();
        try {
//            neighborSet.forEach(n ->
//                    stateBuilder.addNeighborSet(nodeBuilder.setIp(n.getIp()).setPort(n.getPort()).build())
//            );
            downLeafs.forEach(n ->
                    stateBuilder.addLeafSet(nodeBuilder.setIp(n.getIp()).setPort(n.getPort()).build())
            );
            upLeafs.forEach(n ->
                    stateBuilder.addLeafSet(nodeBuilder.setIp(n.getIp()).setPort(n.getPort()).build())
            );
            routingTable.forEach(row -> {
                row.forEach(n -> {
                            if(n != null)
                                rowBuilder.addRoutingTableEntry(nodeBuilder.setIp(n.getIp()).setPort(n.getPort()).build());
                        }
                );
                stateBuilder.addRoutingTable(rowBuilder.build());
            });
        } finally {
            lock.unlock();
        }

        stateBuilder.setOwner(nodeBuilder.setIp(self.getIp()).setPort(self.getPort()).build());
        return stateBuilder.build();
    }

    /**
     * Return the closest node to the given id (based on prefix or numerically)
     * @param id_base 4/8/16-based id (given by {@link PastryNode#B_PARAMETER})
     */
    public NodeReference route(String id_base) {
        printNodeState();
//        if (syncSizeGet(neighborSet) == 0) {
//            // network is empty, self is closest by definition
//            return self;
//        }

        BigInteger id_dec = Util.convertToDecimal(id_base);
        // if id ∈ (leafSet)
        if (syncSizeGet(downLeafs) > 0 && syncSizeGet(upLeafs) > 0
                && syncLastGet(downLeafs).getDecimalId().compareTo(id_dec) <= 0
                &&  id_dec.compareTo(syncLastGet(upLeafs).getDecimalId()) <= 0) {
            NodeReference leaf = getNumericallyClosestLeaf(id_dec);
            logger.trace("[{}]  Routing {} to numerically closest leaf [{}]", self, id_base, leaf);

            return leaf;
        }
        // routing table
        else {
            int l = getSharedPrefixLength(id_base, self.getId());
            int matchDigit = Integer.parseInt(id_base.charAt(l)+"");
            NodeReference r;
            r = syncRoutingTableGet(l, matchDigit, routingTable);// TODO: should I insert self?
            if (r == null) {
                logger.trace("[{}]  Routing {} same len match [{}]", self, id_base, r);
                return findSameLengthMatch(id_base, l);
            }
            logger.trace("[{}]  Routing {} longer prefix match [{}]", self, id_base, r);
            return r;
        }
    }

    private void printNodeState() {
        lock.lock();
        try {
            logger.trace("[{}]  Node state: ", self);
//            logger.trace("  Neighbor set: {}", neighborSet);
            logger.trace("  Down leafs: {}", downLeafs);
            logger.trace("  Up leafs: {}", upLeafs);

            logger.trace("  Routing table: ");
            for (int i = 0; i < routingTable.size(); i++) {
                logger.trace("    Row {}: {}", i, routingTable.get(i));
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * <i> forward to T ∈ (L ∪ R ∪ M), s.th. shl(T, D) >= l && |T - D| < |A - D|</i>
     * Find to a node that shares prefix with the key at least as long as the local node and is numerically closer to the key than the present node’s id.
     */
    private NodeReference findSameLengthMatch(String id_base, int l) {
        lock.lock();
        try {
            // start by searching R[l] entries (same len entries)
            for (int i = l; i < routingTable.size(); i++) {
                List<NodeReference> row = routingTable.get(i);
                for(NodeReference n : row) {
                    if (/*neighborSet.contains(n) || */(upLeafs.contains(n) || downLeafs.contains(n)) ){
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
        logger.trace("[{}]  No same length match found for {}, routing to self", self, id_base);
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
        lock.lock();
        NodeReference closest = self;
        try {
            BigInteger minDistance = self.getDecimalId().subtract(idDec).abs();
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
     * X is the joining node (self) <br>
     * A is bootstrap node <br>
     * Z is node onto which join request converges since it has closest nodeId to X <br>
     */
    public void updateNodeState(Pastry.JoinResponse resp) {
        // statement about joining and copying node state from nodes A and Z does not hold
        // a DHT should be able to handle join no matter the boostrap distance
        // we should therefore copy ALL nodes that we can get from joinResponse
        // note that number of join hops should be log of the number of nodes in the network
        // therefore it should not be expensive to copy all nodes from all NodeStates

        ArrayList<NodeReference> allNodes = new ArrayList<>();
        for (Pastry.NodeState node : resp.getNodeStateList()) {
            if (!allNodes.contains(new NodeReference(node.getOwner().getIp(), node.getOwner().getPort())))
                allNodes.add(new NodeReference(node.getOwner().getIp(), node.getOwner().getPort()));
            for (Pastry.NodeReference n : node.getNeighborSetList()) {
                if (!allNodes.contains(new NodeReference(n.getIp(), n.getPort())))
                    allNodes.add(new NodeReference(n.getIp(), n.getPort()));
            }
            for (Pastry.NodeReference n : node.getLeafSetList()) {
                if (!allNodes.contains(new NodeReference(n.getIp(), n.getPort())))
                    allNodes.add(new NodeReference(n.getIp(), n.getPort()));
            }
            for (Pastry.RoutingTableRow row : node.getRoutingTableList()) {
                for (Pastry.NodeReference n : row.getRoutingTableEntryList()) {
                    if (!allNodes.contains(new NodeReference(n.getIp(), n.getPort())))
                        allNodes.add(new NodeReference(n.getIp(), n.getPort()));
                }
            }
        }
        allNodes.forEach(n -> registerNewNode(n));

        // A usually in proximity to X => A.neighborSet to initialize X.neighborSet (set is updated periodically)
//        int len = resp.getNodeStateCount();
////        List<Pastry.NodeReference> A_neighborSet = resp.getNodeState(len-1).getNeighborSetList();
////        A_neighborSet.forEach(node -> syncInsertIntoNeighborSet(new NodeReference(node.getIp(), node.getPort())));
//
//        // Z has the closest existing nodeId to X, thus its leaf set is the basis for X’s leaf set.
//        List<Pastry.NodeReference> Z_leafs = resp.getNodeState(0).getLeafSetList();
//        Z_leafs.forEach(node -> syncInsertIntoLeafSet(new NodeReference(node.getIp(), node.getPort())));
//
//        // Routing table
//        initRoutingTable(resp);
//
//        // register each forwarding node
//        for (Pastry.NodeState node : resp.getNodeStateList()) {
//            registerNewNode(new NodeReference(node.getOwner().getIp(), node.getOwner().getPort()));
//        }
        printNodeState();
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
            int j = 0;
            for (int i = len-1; i >= 0; i--) { // iterate over nodes A, B, C, ...
                List<NodeReference> jthRow = routingTable.get(j);
                int selfIndex = Integer.parseInt(self.getId().charAt(j)+"");

                for (Pastry.NodeReference n : response.getNodeStateList().get(i).getRoutingTableList().get(j).getRoutingTableEntryList()) {
                    NodeReference newNode = new NodeReference(n.getIp(), n.getPort());
                    int newNodeIndex = Integer.parseInt(newNode.getId().substring(j, j+1));
                    int l = getSharedPrefixLength(newNode.getId(), self.getId());
                    if (selfIndex != newNodeIndex && l == j) { // prefix must match since join could be routed through non-longer matching prefix
                        jthRow.set(newNodeIndex, newNode);
                    }
                }
                j++; // iterate from first row
            }
        } finally {
            lock.unlock();
        }
    }

//    /**
//     * Insert node into neighborSet <br>
//     * If the neighborSet is full and newNode is <b>numerically<b/> closer, remove the farthest node and insert newNode
//     */
//    private void syncInsertIntoNeighborSet(NodeReference newNode) {
//        lock.lock();
//        if (newNode.getDistance() == Long.MAX_VALUE) {
//            newNode.setDistance(distanceCalculator.calculateDistance(self, newNode));
//        }
//        try {// if full, insert only if newNode is better
//            if (syncSizeGet(neighborSet) == L_PARAMETER) {
//                if (newNode.getDistance() < syncLastGet(neighborSet).getDistance() && !neighborSet.contains(newNode)){
//                    neighborSet.remove(syncLastGet(neighborSet));
//                    neighborSet.add(newNode);
//                }
//            } else {
//                if (!neighborSet.contains(newNode))
//                    neighborSet.add(newNode);
//            }
//            neighborSet.sort(Comparator.comparing(NodeReference::getDistance));
//        } finally {
//            lock.unlock();
//        }
//    }

    /**
     * Insert node into <b>appropriate (up/down)</b> leafSet <br>
     */
    private void syncInsertIntoLeafSet(NodeReference newNode) {
        if (newNode.getId().compareTo(self.getId()) > 0) {
            if (!upLeafs.contains(newNode)) {
                int i = 0;
                for (NodeReference curr : upLeafs) {
                    if (curr.getId().compareTo(newNode.getId()) > 0) {
                        break;
                    }
                    i++;
                }
                upLeafs.add(i, newNode);
                if (upLeafs.size() > L_PARAMETER / 2) {
                    upLeafs.remove(upLeafs.size() - 1);
                }
            }
        } else {
            if (!downLeafs.contains(newNode)) {
                int i = 0;
                for (NodeReference curr : downLeafs) {
                    if (curr.getId().compareTo(newNode.getId()) < 0) {
                        break;
                    }
                    i++;
                }
                downLeafs.add(i, newNode);
                if (downLeafs.size() > L_PARAMETER / 2) {
                    downLeafs.remove(downLeafs.size() - 1);
                }
            }
        }
    }

    private void syncInsertIntoRoutingTable(NodeReference newNode) {
        int i = getSharedPrefixLength(newNode.getId(), self.getId());
        int j = Integer.parseInt(newNode.getId().substring(i, i+1));
        lock.lock();
        try {
            List<NodeReference> row = routingTable.get(i);
            // a node corresponding to self is empty in each row
            if (row.get(j) == null) {
                row.set(j, newNode);
            }
//            // nodes with better distance are prefered
//            else if (row.get(j).getDistance() > newNode.getDistance()) {
//                row.set(j, newNode);
//            }
        } finally {
            lock.unlock();
        }
    }

    private void registerNewNode(NodeReference newNode) {
        if (newNode.equals(self)) {
            return;
        }
        if (newNode.getDistance() == Long.MAX_VALUE) {
            newNode.setDistance(distanceCalculator.calculateDistance(self, newNode));
        }
//        syncInsertIntoNeighborSet(newNode);
        syncInsertIntoLeafSet(newNode);
        syncInsertIntoRoutingTable(newNode);
    }

    public NodeReference put(String key, String value) {
        String keyHash = Util.getId(key);

        NodeReference closest = route(keyHash);

        Pastry.ForwardRequest.Builder putReq = Pastry.ForwardRequest.newBuilder()
                .setKey(keyHash)
                .setValue(value)
                .setRequestType(PUT);
        ManagedChannel channel = ManagedChannelBuilder.forTarget(closest.getAddress()).usePlaintext().build();
        blockingStub = PastryServiceGrpc.newBlockingStub(channel);
        Pastry.NodeReference owner = blockingStub.forward(putReq.build()).getOwner();
        channel.shutdown();

        return new NodeReference(owner);
    }

    public String get(String key) {
        String keyHash = Util.getId(key);

        NodeReference closest = route(keyHash);

        Pastry.ForwardRequest.Builder putReq = Pastry.ForwardRequest.newBuilder()
                .setKey(keyHash)
                .setRequestType(GET);
        ManagedChannel channel = ManagedChannelBuilder.forTarget(closest.getAddress()).usePlaintext().build();
        blockingStub = PastryServiceGrpc.newBlockingStub(channel);
        String value = blockingStub.forward(putReq.build()).getValue();
        channel.shutdown();

        return value;
    }

//    public void delete(String key) {
//        String keyHash = Util.getId(key);
//
//        NodeReference storage = getStoringNode(keyHash);
//        logger.trace("[{}]  Storing key {}:{} on {}", self, keyHash, Util.convertToDecimal(keyHash), storage.getAddress());
//
//        Pastry.DeleteRequest.Builder deleteReq = Pastry.DeleteRequest.newBuilder().setKey(keyHash);
//        ManagedChannel channel = ManagedChannelBuilder.forTarget(storage.getAddress()).usePlaintext().build();
//        blockingStub = PastryServiceGrpc.newBlockingStub(channel);
//        blockingStub.delete(deleteReq.build());
//        channel.shutdown();
//    }

//    /**
//     * Principally same as the {@link PastryNode#joinPastry(NodeReference)} but node can be chosen at random <br>
//     * From network, get node that should store the value (its the node with closest id to hashKey)
//     */
//    private NodeReference getStoringNode(String keyHash) {
//        Pastry.ForwardRequest.Builder forwardReq = Pastry.ForwardRequest.newBuilder().setKey(keyHash);
//        NodeReference someNode;
//        lock.lock();
//        try {
//            someNode = neighborSet.get(0);
//        } finally {
//            lock.unlock();
//        }
//        ManagedChannel channel = ManagedChannelBuilder.forTarget(someNode.getAddress()).usePlaintext().build();
//        blockingStub = PastryServiceGrpc.newBlockingStub(channel);
//        Pastry.ForwardResponse response = blockingStub.forward(forwardReq.build());
//        channel.shutdown();
//
//        return new NodeReference(response.getIp(), response.getPort());
//    }


    /**
     * Server-side of Pastry node
     */
    private class PastryNodeServer extends PastryServiceGrpc.PastryServiceImplBase {

        @Override
        public void put(Pastry.PutRequest request, StreamObserver<Pastry.Empty> responseObserver) {
            localData.put(request.getKey(), request.getValue());
            logger.trace("[{}]  saved key {}:{}", self, request.getKey(), Util.convertToDecimal(request.getKey()));
            responseObserver.onNext(Pastry.Empty.newBuilder().build());
            responseObserver.onCompleted();
        }

        @Override
        public void get(Pastry.GetRequest request, StreamObserver<Pastry.GetResponse> responseObserver) {
            String value = localData.get(request.getKey());
            logger.trace("[{}]  retrieved key {}", self, request.getKey());
            responseObserver.onNext(Pastry.GetResponse.newBuilder().setValue(value).build());
            responseObserver.onCompleted();
        }

        @Override
        public void delete(Pastry.DeleteRequest request, StreamObserver<Pastry.Empty> responseObserver) {
            localData.remove(request.getKey());
            logger.trace("[{}]  deleted key {}", self, request.getKey());
            responseObserver.onNext(Pastry.Empty.newBuilder().build());
            responseObserver.onCompleted();
        }

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
                response = Pastry.JoinResponse.newBuilder(response).addNodeState(getMyNodeState()).build();
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
            response = Pastry.JoinResponse.newBuilder(response).addNodeState(getMyNodeState()).build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

            // finally register the node into your nodestate
            registerNewNode(newNode);
        }

        /**
         * Forward to node with closest id to the given id
         */
        @Override
        public void forward(Pastry.ForwardRequest request, StreamObserver<Pastry.ForwardResponse> responseObserver) {
            String keyHash = request.getKey();
            logger.trace("[{}]  {} request, key = {}", self, request.getRequestType(), keyHash);

            NodeReference closest = route(keyHash);


            if (closest.equals(self)) {
                // respond
                Pastry.ForwardResponse.Builder response = Pastry.ForwardResponse.newBuilder().setOwner(self.toProto());

                switch (request.getRequestType()) {
                    case PUT:
                        logger.trace("[{}]  saved key {}", self, keyHash);
                        localData.put(keyHash, request.getValue());
                        response.setStatusCode(SAVED);
                        break;
                    case GET:
                        String value = localData.get(keyHash);
                        logger.trace("[{}]  retrieved key {}", self, keyHash);
                        response.setValue(value).setStatusCode(RETRIEVED);
                        break;
                    case DELETE:
                        String rem = localData.remove(keyHash);
                        Pastry.ForwardResponse.StatusCode status = rem != null ? REMOVED : NOT_FOUND;
                        logger.trace("[{}]  key {} {}", self, keyHash, status);
                        response.setStatusCode(status);
                        break;
                }

                logger.trace("[{}]  My id is the closest to {}, responding back", self, keyHash);
                responseObserver.onNext(response.build());
                responseObserver.onCompleted();
                return;
            }

            // forward request
            logger.trace("[{}]  Forwarding {} request to {}", self, request.getRequestType(), closest.getAddress());
            ManagedChannel channel = ManagedChannelBuilder.forTarget(closest.getAddress()).usePlaintext().build();
            blockingStub = PastryServiceGrpc.newBlockingStub(channel);
            Pastry.ForwardResponse response = blockingStub.forward(request);
            channel.shutdown();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }

            /**
             * This node got notified by requestor about its existence <br>
             * Will reflect requestors existence in its NodeState, as well as send back nodes that did not come from requestor <br>
             */
        @Override
        public void notifyExistence(Pastry.NodeState request, StreamObserver<Pastry.NewNodes> responseObserver) {
            NodeReference newNode = new NodeReference(request.getOwner());
            registerNewNode(newNode);

            ArrayList<NodeReference> newNodes = new ArrayList<>();
            request.getRoutingTableList().forEach(row -> row.getRoutingTableEntryList().forEach(n -> {
                if(!newNodes.contains(new NodeReference(n)))
                    newNodes.add(new NodeReference(n));
            }));
            request.getLeafSetList().forEach(n -> {
                if(!newNodes.contains(new NodeReference(n)))
                    newNodes.add(new NodeReference(n));
            });

            // send back a Set of nodes corresponding to difference between two NodeStates
            Pastry.NewNodes.Builder response = Pastry.NewNodes.newBuilder();
            getAllMyNodes().forEach(n -> {
                if (!newNodes.contains(n)) {
                    response.addNodes(Pastry.NodeReference.newBuilder()
                            .setIp(n.getIp())
                            .setPort(n.getPort())
                            .build());
                }
            });

            responseObserver.onNext(response.build());
            responseObserver.onCompleted();
        }

//        @Override
//        public void getNeighborSet(Pastry.NeighborSetRequest request, StreamObserver<Pastry.NeighborSetResponse> responseObserver) {
//            Pastry.NeighborSetResponse.Builder response = Pastry.NeighborSetResponse.newBuilder();
//            lock.lock();
//            try {
//                neighborSet.forEach(n -> response.addNeighborSet(Pastry.NodeReference.newBuilder()
//                        .setIp(n.getIp())
//                        .setPort(n.getPort())
//                        .build()));
//            } finally {
//                lock.unlock();
//            }
//            logger.trace("[{}]  Sending neighbor set", self);
//            responseObserver.onNext(response.build());
//            responseObserver.onCompleted();
//        }

        @Override
        public void ping(Pastry.Empty request, StreamObserver<Pastry.Empty> responseObserver) {
            responseObserver.onNext(PING);
            responseObserver.onCompleted();
        }
    }



    public static void main(String[] args) throws IOException, InterruptedException {


        PastryNode.setBase(BASE_4_IDS);
        PastryNode.setLeafSize(LEAF_SET_SIZE_8);
        PastryNode.STABILIZATION_INTERVAL = 2000;

        PastryNode bootstrap = new PastryNode("localhost", 10_400);
        bootstrap.initPastry();

        PastryNode node1 = new PastryNode("localhost", 10_401);
        node1.joinPastry(bootstrap.getNode());

        PastryNode node2 = new PastryNode("localhost", 10_402);
        node2.joinPastry(node1.getNode());

        PastryNode node3 = new PastryNode("localhost", 10_403);
        node3.joinPastry(node2.getNode());
        Thread.sleep(10000);
        System.out.println("Shutting down node 3");
    }
}















