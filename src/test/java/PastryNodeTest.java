import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pastry.*;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static pastry.Constants.BASE_4_IDS;
import static pastry.Constants.LEAF_SET_SIZE_8;


public class PastryNodeTest {

    private final Logger logger = LoggerFactory.getLogger(PastryNodeTest.class);

    private int BASE_PORT = 10_000;


    @Test
    public void testBootstrapJoin_alwaysJoinClosest(TestInfo testInfo) throws IOException {
        logger.warn(System.lineSeparator() + System.lineSeparator()+ "============== {} =============" + System.lineSeparator(), testInfo.getDisplayName());

        PastryNode.setBase(BASE_4_IDS);
        PastryNode.setLeafSize(LEAF_SET_SIZE_8);

        PastryNode bootstrap = new PastryNode("localhost", BASE_PORT++);
        bootstrap.initPastry();
        bootstrap.turnOffStabilization();

        List<PastryNode> nodes = new ArrayList<>();
        nodes.add(bootstrap);
        for (int i = 0; i < 50 ; i++) {
            PastryNode node = new PastryNode("localhost", BASE_PORT++);
            NodeReference closest = node.joinPastry(bootstrap.getNode());
            node.turnOffStabilization();
            assertNumericallyClosestOfAll(node.getNode(), closest, nodes);
            nodes.add(node);
        }
    }

    @Test
    public void testRandomJoin_alwaysJoinClosest(TestInfo testInfo) throws IOException {
        logger.warn(System.lineSeparator() + System.lineSeparator()+ "============== {} =============" + System.lineSeparator(), testInfo.getDisplayName());

        PastryNode.setBase(BASE_4_IDS);
        PastryNode.setLeafSize(LEAF_SET_SIZE_8);

        PastryNode bootstrap = new PastryNode("localhost", BASE_PORT++);
        bootstrap.initPastry();
        bootstrap.turnOffStabilization();

        List<PastryNode> nodes = new ArrayList<>();
        nodes.add(bootstrap);
        for (int i = 0; i < 50; i++) {
            PastryNode node = new PastryNode("localhost", BASE_PORT++);
            NodeReference closest = node.joinPastry(nodes.get(new Random().nextInt(nodes.size())).getNode());
            node.turnOffStabilization();
            assertNumericallyClosestOfAll(node.getNode(), closest, nodes);
            nodes.add(node);
        }
    }

    private void assertNumericallyClosestOfAll(NodeReference node, NodeReference closest, List<PastryNode> nodes) {
        nodes.sort(Comparator.comparing(o -> o.getNode().getDecimalId().subtract(node.getDecimalId()).abs()));
        assertEquals(nodes.get(0).getNode(), closest, "Actual closest to " + node + " is " + nodes.get(0).getNode() + ", not " + closest);
    }
}
