import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pastry.NodeReference;
import pastry.PastryNode;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static pastry.Constants.BASE_4_IDS;
import static pastry.Constants.LEAF_SET_SIZE_8;


public class PastryNodeTest {

    private final Logger logger = LoggerFactory.getLogger(PastryNodeTest.class);

    @BeforeEach
    void printTestNameToConsole(TestInfo testInfo) {
        logger.warn(System.lineSeparator() + System.lineSeparator()
                + "============== " + testInfo.getTestMethod().map(Method::getName).orElse(null)
                + "() =============" + System.lineSeparator());
    }

    private void shutdownNodes(PastryNode ... nodes) {
        for (PastryNode node : nodes) {
            node.shutdownPastryNode();
        }
    }

    @Test
    public void testTwoNodes() throws IOException {
        logger.warn(System.lineSeparator() + System.lineSeparator()
                + "============== " + "testTwoNodes"
                + "() =============" + System.lineSeparator());

        PastryNode.setLocalTesting(true);
        PastryNode.setBase(BASE_4_IDS);
        PastryNode.setLeafSize(LEAF_SET_SIZE_8);

        PastryNode bootstrap = new PastryNode("localhost", 10_000);
        bootstrap.initPastry();

        PastryNode node1 = new PastryNode("localhost", 10_001);
        node1.joinPastry(bootstrap.getNode());

        assertEquals(node1.getNode(), bootstrap.getRoutingTable().get(0).get(0));
        assertEquals(bootstrap.getNode(), node1.getRoutingTable().get(0).get(0));

        assertEquals(1, bootstrap.getUpLeafs().size());
        assertEquals(1, node1.getDownLeafs().size());

        assertEquals(1, bootstrap.getNeighborSet().size());
        assertEquals(1, node1.getNeighborSet().size());

        shutdownNodes(bootstrap, node1);
    }

    @Test
    public void testThreeNodes() throws IOException {
        logger.warn(System.lineSeparator() + System.lineSeparator()
                + "============== " + "testThreeNodes"
                + "() =============" + System.lineSeparator());

        PastryNode.setLocalTesting(true);
        PastryNode.setBase(BASE_4_IDS);
        PastryNode.setLeafSize(LEAF_SET_SIZE_8);

        PastryNode bootstrap = new PastryNode("localhost", 10_000);
        bootstrap.initPastry();

        PastryNode node1 = new PastryNode("localhost", 10_001);
        PastryNode node2 = new PastryNode("localhost", 10_002);

        node1.joinPastry(bootstrap.getNode());

        assertEquals(1, bootstrap.getLeafs().size());
        assertEquals(1, bootstrap.getNeighborSet().size());
        assertEquals(1, getRoutingTableSize(bootstrap.getRoutingTable()));

        assertEquals(1, node1.getLeafs().size());
        assertEquals(1, node1.getNeighborSet().size());
        assertEquals(1, getRoutingTableSize(node1.getRoutingTable()));


        node2.joinPastry(node1.getNode());

        // bootstrap gets node2 contact since node2 Join is routed there (bootstrap is closest to it)
        assertEquals(2, bootstrap.getLeafs().size());
        assertEquals(2, bootstrap.getNeighborSet().size());
        assertEquals(2, getRoutingTableSize(bootstrap.getRoutingTable()));

        // node1 gets node2 contact since node2 Join is routed through it
        assertEquals(2, node1.getLeafs().size());
        assertEquals(2, node1.getNeighborSet().size());
        assertEquals(2, getRoutingTableSize(node1.getRoutingTable()));

        // node2 gets contacts of both nodes since both of them insert their NodeState to the JoinResponse
        assertEquals(2, node2.getLeafs().size());
        assertEquals(2, node2.getNeighborSet().size());
        assertEquals(2, getRoutingTableSize(node2.getRoutingTable()));

        assertNoDuplicates(bootstrap.getLeafs());
        assertNoDuplicates(bootstrap.getNeighborSet());

        assertNoDuplicates(node1.getLeafs());
        assertNoDuplicates(node1.getNeighborSet());

        assertNoDuplicates(node2.getLeafs());
        assertNoDuplicates(node2.getNeighborSet());

        shutdownNodes(bootstrap, node1, node2);
    }

    @Test
    @Disabled
    public void testFullNeighborSetNodes() throws IOException {
        logger.warn(System.lineSeparator() + System.lineSeparator()
                + "============== " + "testFullNeighborSetNodes"
                + "() =============" + System.lineSeparator());

        PastryNode.setLocalTesting(true);
        PastryNode.setBase(BASE_4_IDS);
        PastryNode.setLeafSize(LEAF_SET_SIZE_8);

        PastryNode bootstrap = new PastryNode("localhost", 10_000);
        bootstrap.initPastry();

        for (int i = 1; i <= 2*LEAF_SET_SIZE_8; i++) {
            PastryNode node = new PastryNode("localhost", 10_000 + i);
            node.joinPastry(bootstrap.getNode());
        }
    }

    private void assertNoDuplicates(List<NodeReference> set) {
        assertEquals(set.size(), set.stream().distinct().count());
    }

    private int getRoutingTableSize(List<List<NodeReference>> routingTable) {
        return routingTable.stream().mapToInt(List::size).sum(); // sum of all lists' sizes
    }
}
