import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pastry.*;
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

    @AfterEach
    public void tearDown() {
        for (PastryNode node : nodes) {
            node.shutdownPastryNode();
        }
        nodes.clear();
    }

    @Test
    @Disabled
    public void testRandomJoin_RandomPutGet() throws IOException {

        long x = 0;
        long y = 0;

        PastryNode.setBase(BASE_4_IDS);
        PastryNode.setLeafSize(LEAF_SET_SIZE_8);
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

        long x = 0;
        long y = 0;

        PastryNode.setBase(BASE_4_IDS);
        PastryNode.setLeafSize(LEAF_SET_SIZE_8);

        PastryNode bootstrap = new PastryNode("localhost", BASE_PORT++, x++, y++);
        bootstrap.initPastry();
        nodes.add(bootstrap);

        for (int i = 0; i < 50 ; i++) {
            PastryNode node = new PastryNode("localhost", BASE_PORT++, x++, y++);
            NodeReference closest = node.joinPastry(bootstrap.getNode());
            node.turnOffStabilization();
            assertNumericallyClosestOfAll(node.getNode(), closest, nodes);
            nodes.add(node);
        }
    }

    @Test
    public void testRandomJoin_alwaysJoinClosest() throws IOException {

        long x = 0;
        long y = 0;

        PastryNode.setBase(BASE_4_IDS);
        PastryNode.setLeafSize(LEAF_SET_SIZE_8);

        PastryNode bootstrap = new PastryNode("localhost", BASE_PORT++, x++, y++);
        bootstrap.initPastry();
        nodes.add(bootstrap);

        for (int i = 0; i < 50; i++) {
            PastryNode node = new PastryNode("localhost", BASE_PORT++, x++, y++);
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
