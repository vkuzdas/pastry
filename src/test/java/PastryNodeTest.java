import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pastry.PastryNode;
import pastry.Util;
import proto.Pastry;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static pastry.Constants.BASE_4_IDS;
import static pastry.Constants.LEAF_SET_SIZE_8;


public class PastryNodeTest {

    Logger logger = LoggerFactory.getLogger(PastryNodeTest.class);

    @BeforeEach
    void printTestNameToConsole(TestInfo testInfo) {
        logger.warn(System.lineSeparator() + System.lineSeparator()
                + "============== " + testInfo.getTestMethod().map(Method::getName).orElse(null)
                + "() =============" + System.lineSeparator());
    }

    @Test
    public void testTwoNodes() throws IOException {
        logger.warn(System.lineSeparator() + System.lineSeparator()
                + "============== " + "testTwoNodes"
                + "() =============" + System.lineSeparator());
        PastryNode.LOCAL_TESTING = true;
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

        bootstrap.shutdownPastryNode();
        node1.shutdownPastryNode();
    }

    @Test
    public void testThreeNodes() throws IOException {
        logger.warn(System.lineSeparator() + System.lineSeparator()
                + "============== " + "testTwoNodes"
                + "() =============" + System.lineSeparator());
        PastryNode.LOCAL_TESTING = true;
        PastryNode.setBase(BASE_4_IDS);
        PastryNode.setLeafSize(LEAF_SET_SIZE_8);

        PastryNode bootstrap = new PastryNode("localhost", 10_000);
        bootstrap.initPastry();

        PastryNode node1 = new PastryNode("localhost", 10_001);
        PastryNode node2 = new PastryNode("localhost", 10_002);

        node1.joinPastry(bootstrap.getNode());
        node2.joinPastry(node1.getNode());

        // TODO: asserts

        bootstrap.shutdownPastryNode();
        node1.shutdownPastryNode();
        node2.shutdownPastryNode();
    }
}
