import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pastry.*;
import pastry.metric.CoordinateDistanceCalculator;
import pastry.metric.DistanceCalculator;
import proto.Pastry;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static pastry.Constants.BASE_4_IDS;
import static pastry.Constants.LEAF_SET_SIZE_8;

/**
 * Bind exceptions and other instabilities occur when the whole class is run, but not when a test is run individually.
 */
public class PastryNodeTest {

    private final Logger logger = LoggerFactory.getLogger(PastryNodeTest.class);

    private static int BASE_PORT = 10_000;
    private final ArrayList<PastryNode> nodes = new ArrayList<>();

    @BeforeEach
    public void printInfo(TestInfo testInfo) {
        logger.warn(System.lineSeparator() + System.lineSeparator()+ "============== {} =============" + System.lineSeparator(), testInfo.getDisplayName());
    }

    @BeforeEach
    public void init() {
        PastryNode.setBase(BASE_4_IDS);
        PastryNode.setLeafSize(LEAF_SET_SIZE_8);
    }

    @AfterEach
    public void tearDown() {
        for (PastryNode node : nodes) {
            node.shutdownPastryNode();
        }
        nodes.clear();
    }

    public void registerAll(PastryNode ... nodes) {
        this.nodes.addAll(Arrays.asList(nodes));
    }

    @Test
    public void testMetric_routingTableRewritten() throws IOException {
        PastryNode.setDefaultCalculator(new CoordinateDistanceCalculator());

        // 10000:13202231
        PastryNode bootstrap = new PastryNode("localhost", BASE_PORT, 0, 0);
        bootstrap.initPastry();


        //10002:02032101
        PastryNode node1 = new PastryNode("localhost", 10_002, 1654, 1321);
        node1.joinPastry(bootstrap.getNode());
        assertEquals(bootstrap.getRoutingTable().get(0).get(0), node1.getNode(), "Expected node1");

        //10007:00012102 (should overwrite node1)
        PastryNode node2 = new PastryNode("localhost", 10_007, 964, 912);
        node2.joinPastry(bootstrap.getNode());
        assertEquals(bootstrap.getRoutingTable().get(0).get(0), node2.getNode(), "Expected node2 to rewrite node1");



        //10001:33130012
        PastryNode node3 = new PastryNode("localhost", 10_001, 445, 401);
        node3.joinPastry(bootstrap.getNode());
        assertEquals(bootstrap.getRoutingTable().get(0).get(3), node3.getNode(), "Expected node3");


        //10009:33331223 (should overwrite node3)
        PastryNode node4 = new PastryNode("localhost", 10_009, 390, 303);
        node4.joinPastry(bootstrap.getNode());
        assertEquals(bootstrap.getRoutingTable().get(0).get(3), node4.getNode(), "Expected node4 to rewrite node3");


        registerAll(bootstrap, node1, node2, node3, node4);

        DistanceCalculator calculator = new CoordinateDistanceCalculator();

        // verify that distance in nodeState is calculated correctly
        bootstrap.getAllNodes().forEach(computed -> {
            PastryNode constructed = nodes.stream().filter(n -> n.getNode().equals(computed)).findFirst().get();
            assertEquals(calculator.calculateDistance(bootstrap.getNode(), constructed.getNode()), computed.getDistance());
        });
        node1.getAllNodes().forEach(computed -> {
            PastryNode constructed = nodes.stream().filter(n -> n.getNode().equals(computed)).findFirst().get();
            assertEquals(calculator.calculateDistance(node1.getNode(), constructed.getNode()), computed.getDistance());
        });
        node4.getAllNodes().forEach(computed -> {
            PastryNode constructed = nodes.stream().filter(n -> n.getNode().equals(computed)).findFirst().get();
            assertEquals(calculator.calculateDistance(node4.getNode(), constructed.getNode()), computed.getDistance());
        });
    }

    @Test
    @Disabled
    public void testRandomJoin_RandomPutGet() throws IOException {

        long x = 0;
        long y = 0;

        int j = 0;

        PastryNode bootstrap = new PastryNode("localhost", BASE_PORT++, x++, y++);
        bootstrap.initPastry();
        nodes.add(bootstrap);

        for (int i = 0; i < 10 ; i++) {
            PastryNode node = new PastryNode("localhost", BASE_PORT++, x++, y++);
            node.joinPastry(nodes.get(new Random().nextInt(nodes.size())).getNode());
            nodes.add(node);
        }

        // randomly put 50 random keys, assert closest node is owner
        for (int i = 0; i < 50; i++) {
            j++;
            String key = "key" + j;
            PastryNode randomNode = nodes.get(new Random().nextInt(nodes.size()));

            NodeReference owner = randomNode.put(key, "value" + j);

            assertNumericallyClosestOfAll(Util.getId(key), owner, nodes);
        }

        // get 50 keys
        for (int i = 0; i < 50; i++) {
            String key = "key" + j;

            PastryNode randomNode = nodes.get(new Random().nextInt(nodes.size()));

            assertEquals("value" + j, randomNode.get(key));

            j--;
        }

        // remove 50 present keys
        for (int i = 0; i < 50; i++) {
            j++;
            String key = "key" + j;

            PastryNode randomNode = nodes.get(new Random().nextInt(nodes.size()));

            assertEquals(Pastry.ForwardResponse.StatusCode.REMOVED, randomNode.delete(key));
        }

        // remove 10 *not* present keys
        for (int i = 0; i < 10; i++) {
            j++;
            String key = "key" + j;

            PastryNode randomNode = nodes.get(new Random().nextInt(nodes.size()));

            assertEquals(Pastry.ForwardResponse.StatusCode.NOT_FOUND, randomNode.delete(key));
        }
    }


    @Test
    public void testBootstrapJoin_alwaysJoinClosest() throws IOException {
        PastryNode bootstrap = new PastryNode("localhost", BASE_PORT++, 0, 0);
        bootstrap.initPastry();
        nodes.add(bootstrap);

        for (int i = 0; i < 50 ; i++) {
            PastryNode node = new PastryNode("localhost", BASE_PORT++, 0, 0);
            NodeReference closest = node.joinPastry(bootstrap.getNode());
            node.turnOffStabilization();
            assertNumericallyClosestOfAll(node.getNode(), closest, nodes);
            nodes.add(node);
        }
    }

    @Test
    public void testRandomJoin_alwaysJoinClosest() throws IOException {
        PastryNode bootstrap = new PastryNode("localhost", BASE_PORT++, 0, 0);
        bootstrap.initPastry();
        nodes.add(bootstrap);

        for (int i = 0; i < 50; i++) {
            PastryNode node = new PastryNode("localhost", BASE_PORT++, 0, 0);
            NodeReference closest = node.joinPastry(nodes.get(new Random().nextInt(nodes.size())).getNode());
            assertNumericallyClosestOfAll(node.getNode(), closest, nodes);
            nodes.add(node);
        }
    }

    private void assertNumericallyClosestOfAll(NodeReference node, NodeReference closest, List<PastryNode> nodes) {
        nodes.sort(Comparator.comparing(o -> o.getNode().getDecimalId().subtract(node.getDecimalId()).abs()));
        assertEquals(nodes.get(0).getNode(), closest, "Actual closest to " + node + " is " + nodes.get(0).getNode() + ", not " + closest);
    }
    private void assertNumericallyClosestOfAll(String keyHash, NodeReference closest, List<PastryNode> nodes) {
        nodes.sort(Comparator.comparing(o -> o.getNode().getDecimalId().subtract(Util.convertToDecimal(keyHash)).abs()));
        assertEquals(nodes.get(0).getNode(), closest, "Actual closest to " + keyHash + " is " + nodes.get(0).getNode() + ", not " + closest);
    }
}
