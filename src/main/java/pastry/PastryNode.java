package pastry;

import com.google.common.annotations.VisibleForTesting;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pastry.metric.DistanceCalculator;
import pastry.metric.NumericalDifferenceDistanceCalculator;
import proto.Pastry;
import proto.PastryServiceGrpc;

import java.io.IOException;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

import static pastry.Constants.*;
import static proto.Pastry.ForwardRequest.RequestType.*;
import static proto.Pastry.ForwardResponse.StatusCode.*;

public class PastryNode {

    private static final Logger logger = LoggerFactory.getLogger(PastryNode.class);
    /**
     * Config parameter determining base of id: ids are 2^b based <br>
     * It is only recommended to use 4-based, 8-based and 16-based
     */
    public static int B_PARAMETER = BASE_16_IDS;

    /**
     * Config parameter determining size of leaf set
     * It is only recommended to use 16 and 32
     */
    public static int L_PARAMETER = LEAF_SET_SIZE_8;
    /**
     * Map lock
     */
    private final ReentrantLock lock = new ReentrantLock();
    /**
     * Each PastryNode stores keys in range [closestDownLeaf, closestUpleaf) since keys are routed to the closest ID,
     */
    // TODO: move to NodeState
    private final NavigableMap<BigInteger, String> localData = new TreeMap<>();

    private final NodeState state;

    private final NodeReference self;

    private final Timer stabilizationTimer = new Timer();
    public static int STABILIZATION_INTERVAL = 5000;

    private final Server server;
    private PastryServiceGrpc.PastryServiceBlockingStub blockingStub;

    private static DistanceCalculator defaultCalculator = null;
    private static boolean stabilization = true;


    public PastryNode(String ip, int port, long x, long y) {

        server = ServerBuilder.forPort(port)
                .addService(new PastryNodeServer())
                .build();

        if(defaultCalculator == null) {
            // fallback to Numerical
            state = new NodeState(B_PARAMETER, L_PARAMETER, L_PARAMETER, ip, port, x, y, new NumericalDifferenceDistanceCalculator());
        } else {
            state = new NodeState(B_PARAMETER, L_PARAMETER, L_PARAMETER, ip, port, x, y, defaultCalculator);
        }

        self = state.getSelf();
    }

    public DistanceCalculator getDistanceCalculator() {
        return state.getDistanceCalculator();
    }

    public static void setStabiliation(boolean stabilize) {
        stabilization = stabilize;
    }

    public static void setDefaultCalculator(DistanceCalculator calculator) {
        defaultCalculator = calculator;
    }

//    public void setDistanceCalculator(DistanceCalculator distanceCalculator) {
//        if (distanceCalculator instanceof PingResponseTimeDistanceCalculator) {
//            ((PingResponseTimeDistanceCalculator) distanceCalculator).setBlockingStub(blockingStub);
//        }
//        this.distanceCalculator = distanceCalculator;
//    }

//    public DistanceCalculator getDistanceCalculator() {
//        return distanceCalculator;
//    }

    public static void setBase(int b) {
        if (b != BASE_4_IDS && b != Constants.BASE_16_IDS) {
            throw new IllegalArgumentException("B must be 2, 3 or 4");
        }
        PastryNode.B_PARAMETER = b;
    }

    public static void setLeafSize(int size) {
        if (size != LEAF_SET_SIZE_4 && size != LEAF_SET_SIZE_8 && size != Constants.LEAF_SET_SIZE_16 && size != Constants.LEAF_SET_SIZE_32) {
            throw new IllegalArgumentException("L must be 8, 16 or 32");
        }
        PastryNode.L_PARAMETER = size;
    }

    public NodeReference getNode() {
        return self;
    }

    // for correct metric validation
    @VisibleForTesting
    public ArrayList<NodeReference> getNeighborSet() {
        return state.getNeighborsCopy();
    }

    // for correct metric validation
    @VisibleForTesting
    public ArrayList<ArrayList<NodeReference>> getRoutingTable() {
        return state.getRoutingTableCopy();
    }

    @VisibleForTesting
    public ArrayList<NodeReference> getAllNodes() {
        return state.getAllNodes();
    }

    @VisibleForTesting
    public NavigableMap<BigInteger, String> getLocalData() {
        lock.lock();
        try {
            return new TreeMap<>(localData);
        } finally {
            lock.unlock();
        }
    }



    public void initPastry() throws IOException {
        startServer();
        logger.trace("[{}]  started FIX", self);

        if(stabilization) {
            startStabilizationThread();
        }
    }

    /**
     * <i>Node attempts to contact each member
     * of the neighborhood set periodically to see if it is still alive. If a member is not responding,
     * the node asks other members for their neighborhood tables, checks the distance of
     * each of the newly discovered nodes, and updates it own neighborhood set accordingly.</i>
     */
    private void startStabilizationThread() {
        TimerTask stabilizationTimerTask = new TimerTask() {
            @Override
            public void run() {
                logger.trace("[{}]  checking neighbors..", self);
                // make copy of current neighbor set
                // iterate over it and request their neighbors
                // fill neighborCandidates
                List<NodeReference> neighborCandidates = new ArrayList<>();
                List<NodeReference> toRemove = new ArrayList<>();

                // iterate over neighbors, check liveness of each
                for (NodeReference neighbor : state.getNeighborsCopy()) {
                    ManagedChannel channel = ManagedChannelBuilder.forTarget(neighbor.getAddress()).usePlaintext().build();
                    blockingStub = PastryServiceGrpc.newBlockingStub(channel);

                    Pastry.NeighborSetResponse response;

                    try {
                        response = blockingStub.getNeighborSet(Pastry.NeighborSetRequest.newBuilder().build());
                        for (Pastry.NodeReference n : response.getNeighborSetList()) {
                            if(!neighborCandidates.contains(new NodeReference(n))){
                                neighborCandidates.add(new NodeReference(n));
                            }
                        }
                    }
                    catch (StatusRuntimeException e) {
                        // neighbor is down, remove and find new
                        channel.shutdown();
                        logger.error("[{}]  status of [{}] is {}, removing from NodeState", self, neighbor, e.getStatus().getCode());
                        toRemove.add(neighbor);
                    }
                    channel.shutdown();
                }

                for(NodeReference n : toRemove) {
                    state.unregisterFailedNode(n);
                }

                for (NodeReference candidate : neighborCandidates) {
                    if (!toRemove.contains(candidate)) {
                        state.registerNewNode(candidate);
                    }
                }
            }
        };
        stabilizationTimer.schedule(stabilizationTimerTask,1000, STABILIZATION_INTERVAL);
    }

    public void leavePastry() {
        // local data should be moved before leaving

        boolean keysSent = false;
        while(!keysSent) {
            NodeReference currSuccessor = state.getClosestUpleaf();
            NodeReference currPredecessor = state.getClosestDownleaf();

            TreeMap<BigInteger, String> successorKeys = new TreeMap<>();
            TreeMap<BigInteger, String> predecessorKeys = new TreeMap<>();

            if (currPredecessor != null && currSuccessor != null) {
                // keys are split according to closeness
                localData.forEach((key, value) -> {
                    BigInteger distToPredecessor = currPredecessor.getDecimalId().subtract(key).abs();
                    BigInteger distToSuccessor = currSuccessor.getDecimalId().subtract(key).abs();

                    if (distToPredecessor.compareTo(distToSuccessor) < 0) {
                        predecessorKeys.put(key, value);
                    } else {
                        successorKeys.put(key, value);
                    }
                });
            }
            if (currPredecessor != null && currSuccessor == null) {
                predecessorKeys.putAll(localData);
            }
            if (currPredecessor == null && currSuccessor != null) {
                successorKeys.putAll(localData);
            }

            try {
                if (!successorKeys.isEmpty()) {
                    sendKeysTo(currSuccessor, successorKeys);
                }
                if (!predecessorKeys.isEmpty()) {
                    sendKeysTo(currPredecessor, predecessorKeys);
                }
                keysSent = true;
            } catch (StatusRuntimeException e) {
                logger.error("[{}]  status of [{}] is {}, retrying", self, currSuccessor, e.getStatus().getCode());
            }
        }

        localData.clear();

        shutdownPastryNode();
    }

    private void sendKeysTo(NodeReference destination, TreeMap<BigInteger, String> successorKeys) {
        Pastry.MoveKeysRequest.Builder moveKeysToSuccessor = Pastry.MoveKeysRequest.newBuilder();
        successorKeys.forEach((key, value) -> moveKeysToSuccessor.addDhtEntries(Pastry.DHTEntry.newBuilder()
                .setKey(key.toString())
                .setValue(value)
                .build()));

        ManagedChannel channel = ManagedChannelBuilder.forTarget(destination.getAddress()).usePlaintext().build();
        blockingStub = PastryServiceGrpc.newBlockingStub(channel);

        try {
            blockingStub.moveKeys(moveKeysToSuccessor.build());
        } catch (StatusRuntimeException e) {
            logger.error("[{}]  status of [{}] is {}, retrying", self, destination, e.getStatus().getCode());
            state.unregisterFailedNode(destination);
            throw e; // will be caught in previous method
        } finally {
            channel.shutdown();
        }
    }

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
        Pastry.JoinRequest.Builder request = Pastry.JoinRequest.newBuilder().setSender(self.toProto());
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

        // add all transferred keys
        lock.lock();
        try {
            resp.getNodeStateList().forEach(nodeState -> {
                nodeState.getDhtEntriesList().forEach(entry -> {
                    localData.put(new BigInteger(entry.getKey()), entry.getValue());
                });
            });
        } finally {
            lock.unlock();
        }

        Pastry.NodeReference owner = resp.getNodeState(0).getOwner();
        NodeReference closest = new NodeReference(owner);

        state.updateNodeState(resp);
        notifyAboutMyself();

        if(stabilization) {
            startStabilizationThread();
        }

        // this is not in actual Pastry API, it is however used to verify that we have reached the actual CLOSEST node
        return closest;
    }

    /**
     * Notify all nodes about new node and reflect all nodes in new node's NodeState. Notified nodes may return moved keys. <br> <br>
     *
     * The joining node will gather all known nodes and notify them about itself and its NodeState <br>
     * The addressed nodes will reflect joining node in its NodeState, <br>
     * as well as send back nodes from its NodeState that have not been sent by the joining node. <br>
     * Joining node will then notify newly discovered nodes about itself and its NodeState <br>
     * Finally, joining node will reflect all newly discovered nodes into its NodeState
     * @see PastryNodeServer#notifyExistence(Pastry.NodeState, StreamObserver)
     */
    private void notifyAboutMyself() {
        ArrayList<NodeReference> myNodes = state.getAllNodes();
        ArrayList<NodeReference> toNotify = new ArrayList<>(myNodes);
        ArrayList<NodeReference> newlyDiscovered = new ArrayList<>();
        ArrayList<NodeReference> notified = new ArrayList<>();

        while (!toNotify.isEmpty()) {
            NodeReference n = toNotify.remove(0);

            ManagedChannel channel = ManagedChannelBuilder.forTarget(n.getAddress()).usePlaintext().build();
            blockingStub = PastryServiceGrpc.newBlockingStub(channel);
            Pastry.NewNodes newNodes = blockingStub.notifyExistence(state.toProto());
            channel.shutdown();

            // DHT entries may come in notify response
            newNodes.getDhtEntriesList().forEach(entry -> {
                lock.lock();
                try {
                    localData.put(new BigInteger(entry.getKey()), entry.getValue());
                } finally {
                    lock.unlock();
                }
            });

            newNodes.getNodesList().forEach(newNode -> {
                NodeReference newRef = new NodeReference(newNode);

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

        newlyDiscovered.forEach(state::registerNewNode);
        logger.trace("[{}]  Notified: {}", self, notified);
        logger.trace("[{}]  Newly discovered: {}", self, newlyDiscovered);
    }




    /**
     * Return the closest node to the given id (based on prefix or numerically)
     * @param id_base 4/8/16-based id (given by {@link PastryNode#B_PARAMETER})
     */
    public NodeReference route(String id_base) {
        state.printNodeState();
//        if (syncSizeGet(neighborSet) == 0) {
//            // network is empty, self is closest by definition
//            return self;
//        }

        if (state.inLeafRange(id_base)) {
            NodeReference leaf = state.getNumericallyClosestLeaf(id_base);
            logger.trace("[{}]  Routing {} to numerically closest leaf [{}]", self, id_base, leaf);
            return leaf;
        }
        // routing table
        else {
            int l = Util.getSharedPrefixLength(id_base, self.getId());
            int matchDigit = Util.parseDigit(id_base.charAt(l));

            NodeReference r;
            r = state.routingTableGet(l, matchDigit);

            if (r == null) {
                r = state.findSameLengthMatch(id_base, l);
                logger.trace("[{}]  Routing {} to same len match (l={}) [{}]", self, id_base, l, r);
                return r;
            }

            logger.trace("[{}]  Routing {} to longer prefix match (l={}) [{}]", self, id_base, l, r);
            return r;
        }
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

        Pastry.ForwardRequest.Builder getRequest = Pastry.ForwardRequest.newBuilder()
                .setKey(keyHash)
                .setRequestType(GET);
        ManagedChannel channel = ManagedChannelBuilder.forTarget(closest.getAddress()).usePlaintext().build();
        blockingStub = PastryServiceGrpc.newBlockingStub(channel);
        String value = blockingStub.forward(getRequest.build()).getValue();
        channel.shutdown();

        return value;
    }

    public Pastry.ForwardResponse.StatusCode delete(String key) {
        String keyHash = Util.getId(key);

        NodeReference closest = route(keyHash);

        Pastry.ForwardRequest.Builder deleteRequest = Pastry.ForwardRequest.newBuilder()
                .setKey(keyHash)
                .setRequestType(DELETE);
        ManagedChannel channel = ManagedChannelBuilder.forTarget(closest.getAddress()).usePlaintext().build();
        blockingStub = PastryServiceGrpc.newBlockingStub(channel);
        Pastry.ForwardResponse.StatusCode status = blockingStub.forward(deleteRequest.build()).getStatusCode();
        channel.shutdown();

        return status;
    }



    /**
     * Server-side of Pastry node
     */
    private class PastryNodeServer extends PastryServiceGrpc.PastryServiceImplBase {

        /**
         * NodeState in Pastry.JoinResponse.NodeState is stack-like: Z node is inserted first, A last
         */
        @SuppressWarnings("Duplicates")
        @Override
        public void join(Pastry.JoinRequest request, StreamObserver<Pastry.JoinResponse> responseObserver) {
            Pastry.NodeReference sender = request.getSender();
            logger.trace("[{}]  Join request from {}:{}", self, sender.getIp(), sender.getPort());
            NodeReference newNode = new NodeReference(sender);


            // find the closest node to the new node
            NodeReference closest = route(Util.getId(newNode.getAddress()));

            // either node is alone or it is the Z node itself
            if (closest.equals(self)) {

                // check keys
                NodeReference currSuccessor = state.getClosestUpleaf();
                NodeReference currPredecessor = state.getClosestDownleaf();

                state.registerNewNode(newNode);

                SortedMap<BigInteger, String> transferEntries = new TreeMap<>();

                if(state.getClosestDownleaf() != currPredecessor) {
                    transferEntries = getTransferKeys(state.getClosestDownleaf().getDecimalId());
                }
                else if (state.getClosestUpleaf() != currSuccessor) {
                    transferEntries = getTransferKeys(state.getClosestUpleaf().getDecimalId());
                }

                Pastry.NodeState.Builder myState = Pastry.NodeState.newBuilder(state.toProto());
                for (Map.Entry<BigInteger, String> entry : transferEntries.entrySet()) {
                    myState.addDhtEntries(Pastry.DHTEntry.newBuilder()
                            .setKey(entry.getKey().toString())
                            .setValue(entry.getValue())
                            .build());
                }

                // create JoinResponse, add current node state
                Pastry.JoinResponse response = Pastry.JoinResponse.newBuilder().addNodeState(myState).build();
                logger.trace("[{}]  My id is the closest to {}, responding back", self, newNode.getId());

                responseObserver.onNext(response);
                responseObserver.onCompleted();
            }
            else {

                // check keys
                NodeReference currSuccessor = state.getClosestUpleaf();
                NodeReference currPredecessor = state.getClosestDownleaf();

                state.registerNewNode(newNode);

                SortedMap<BigInteger, String> transferEntries = new TreeMap<>();

                if(state.getClosestDownleaf() != currPredecessor) {
                    transferEntries = getTransferKeys(state.getClosestDownleaf().getDecimalId());
                }
                else if (state.getClosestUpleaf() != currSuccessor) {
                    transferEntries = getTransferKeys(state.getClosestUpleaf().getDecimalId());
                }

                Pastry.NodeState.Builder myState = Pastry.NodeState.newBuilder(state.toProto());
                for (Map.Entry<BigInteger, String> entry : transferEntries.entrySet()) {
                    myState.addDhtEntries(Pastry.DHTEntry.newBuilder()
                            .setKey(entry.getKey().toString())
                            .setValue(entry.getValue())
                            .build());
                }


                // reroute newNode's join request to the closest node
                logger.trace("[{}]  Forwarding to {}", self, closest.getAddress());
                ManagedChannel channel = ManagedChannelBuilder.forTarget(closest.getAddress()).usePlaintext().build();
                blockingStub = PastryServiceGrpc.newBlockingStub(channel);

                // once the response has arrived, enrich it with the state of the current node
                Pastry.JoinResponse response = blockingStub.join(Pastry.JoinRequest.newBuilder()
                        .setSender(sender)
                        .build());
                channel.shutdown();

                // and response to the prior request
                response = Pastry.JoinResponse.newBuilder(response).addNodeState(myState).build();

                responseObserver.onNext(response);
                responseObserver.onCompleted();
            }
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
                        lock.lock();
                        try {
                            localData.put(Util.convertToDecimal(keyHash), request.getValue());
                        } finally {
                            lock.unlock();
                        }
                        response.setStatusCode(SAVED);
                        break;
                    case GET:
                        String value;
                        lock.lock();
                        try {
                            value = localData.get(Util.convertToDecimal(keyHash));
                        } finally {
                            lock.unlock();
                        }
                        logger.trace("[{}]  retrieved key {}", self, keyHash);
                        response.setValue(value).setStatusCode(RETRIEVED);
                        break;
                    case DELETE:
                        String rem;
                        lock.lock();
                        try {
                            rem = localData.remove(Util.convertToDecimal(keyHash));
                        } finally {
                            lock.unlock();
                        }
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

            // check data, split if necessary
            NodeReference currSuccessor = state.getClosestUpleaf();
            NodeReference currPredecessor = state.getClosestDownleaf();

            state.registerNewNode(newNode);

            SortedMap<BigInteger, String> transferEntries = new TreeMap<>();

            if(state.getClosestDownleaf() != currPredecessor) {
                transferEntries = getTransferKeys(state.getClosestDownleaf().getDecimalId());
            }
            else if (state.getClosestUpleaf() != currSuccessor) {
                transferEntries = getTransferKeys(state.getClosestUpleaf().getDecimalId());
            }

            Pastry.NewNodes.Builder response = Pastry.NewNodes.newBuilder();
            for (Map.Entry<BigInteger, String> entry : transferEntries.entrySet()) {
                response.addDhtEntries(Pastry.DHTEntry.newBuilder()
                        .setKey(entry.getKey().toString())
                        .setValue(entry.getValue())
                        .build());
            }

            ArrayList<NodeReference> notifiersNodes = new ArrayList<>();
            request.getRoutingTableList().forEach(row -> row.getRoutingTableEntryList().forEach(n -> {
                if(!notifiersNodes.contains(new NodeReference(n)))
                    notifiersNodes.add(new NodeReference(n));
            }));
            request.getLeafSetList().forEach(n -> {
                if(!notifiersNodes.contains(new NodeReference(n)))
                    notifiersNodes.add(new NodeReference(n));
            });
            request.getNeighborSetList().forEach(n -> {
                if(!notifiersNodes.contains(new NodeReference(n)))
                    notifiersNodes.add(new NodeReference(n));
            });

            // send back a Set of nodes corresponding to difference between two NodeStates
            state.getAllNodes().forEach(n -> {
                if (!notifiersNodes.contains(n)) {
                    response.addNodes(Pastry.NodeReference.newBuilder()
                            .setIp(n.getIp())
                            .setPort(n.getPort())
                            .build());
                }
            });

            responseObserver.onNext(response.build());
            responseObserver.onCompleted();
        }

        @Override
        public void moveKeys(Pastry.MoveKeysRequest request, StreamObserver<Pastry.Empty> responseObserver) {
            lock.lock();
            try {
                request.getDhtEntriesList().forEach(entry -> localData.put(new BigInteger(entry.getKey()), entry.getValue()));
            } finally {
                lock.unlock();
            }
            responseObserver.onNext(Pastry.Empty.newBuilder().build());
            responseObserver.onCompleted();
        }

        @Override
        public void getNeighborSet(Pastry.NeighborSetRequest request, StreamObserver<Pastry.NeighborSetResponse> responseObserver) {
            logger.trace("[{}]  Sending neighbor set", self);
            responseObserver.onNext(state.neighborsToProto());
            responseObserver.onCompleted();
        }

        @Override
        public void ping(Pastry.Empty request, StreamObserver<Pastry.Empty> responseObserver) {
            responseObserver.onNext(Pastry.Empty.newBuilder().build());
            responseObserver.onCompleted();
        }
    }

    /**
     * Keys that are numerically closer to the closest leaf are transferred
     */
    // TODO: move to NodeState
    private SortedMap<BigInteger, String> getTransferKeys(BigInteger closestLeaf) {
        SortedMap<BigInteger, String> keysToTransfer = new TreeMap<>();
        lock.lock();
        try {
            localData.forEach((key, value) -> {
                BigInteger distToCurrent = self.getDecimalId().subtract(key).abs();
                BigInteger distToClosestLeaf = closestLeaf.subtract(key).abs();
                if (distToClosestLeaf.compareTo(distToCurrent) < 0) {
                    keysToTransfer.put(key, value);
                }
            });
            keysToTransfer.forEach((key, value) -> localData.remove(key));
        } finally {
            lock.unlock();
        }
        return keysToTransfer;
    }
}















