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


    @Test
    public void testBootstrapJoin_alwaysJoinClosest() throws IOException {
        logger.warn(System.lineSeparator() + System.lineSeparator()
                + "============== " + "testBootstrapJoin_alwaysJoinClosest"
                + "() =============" + System.lineSeparator());


        PastryNode.setBase(BASE_4_IDS);
        PastryNode.setLeafSize(LEAF_SET_SIZE_8);

        PastryNode bootstrap = new PastryNode("localhost", 10_700);
        bootstrap.initPastry();
        bootstrap.turnOffStabilization();

        List<PastryNode> nodes = new ArrayList<>();
        nodes.add(bootstrap);
        for (int i = 0; i < 50 ; i++) {
            PastryNode node = new PastryNode("localhost", 10_701 + i);
            NodeReference closest = node.joinPastry(bootstrap.getNode());
            node.turnOffStabilization();
            assertNumericallyClosestOfAll(node.getNode(), closest, nodes);
            nodes.add(node);
        }
    }

    @Test
    public void testRandomJoin_alwaysJoinClosest() throws IOException {
        logger.warn(System.lineSeparator() + System.lineSeparator()
                + "============== " + "testBootstrapJoin_alwaysJoinClosest"
                + "() =============" + System.lineSeparator());


        PastryNode.setBase(BASE_4_IDS);
        PastryNode.setLeafSize(LEAF_SET_SIZE_8);

        PastryNode bootstrap = new PastryNode("localhost", 10_800);
        bootstrap.initPastry();
        bootstrap.turnOffStabilization();

        List<PastryNode> nodes = new ArrayList<>();
        nodes.add(bootstrap);
        for (int i = 0; i < 50; i++) {
            PastryNode node = new PastryNode("localhost", 10_801 + i);
            NodeReference closest = node.joinPastry(nodes.get(new Random().nextInt(nodes.size())).getNode());
            node.turnOffStabilization();
            assertNumericallyClosestOfAll(node.getNode(), closest, nodes);
            nodes.add(node);
        }
    }

    /**
     * Closest in terms of digit match and numerical distance
     */
    private void assertNumericallyClosestOfAll(NodeReference node, NodeReference closest, List<PastryNode> nodes) {
        nodes.sort(Comparator.comparing(o -> o.getNode().getDecimalId().subtract(node.getDecimalId()).abs()));
        assertEquals(nodes.get(0).getNode(), closest, "Actual closest to " + node + " is " + nodes.get(0).getNode() + ", not " + closest);
    }


    private void assertNoDuplicates(List<NodeReference> set) {
        assertEquals(set.size(), set.stream().distinct().count());
    }

    private int getRoutingTableSize(List<List<NodeReference>> routingTable) {
        return routingTable.stream().mapToInt(List::size).sum(); // sum of all lists' sizes
    }
}
