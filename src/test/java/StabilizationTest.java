import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pastry.PastryNode;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class StabilizationTest extends BaseTest {

    private final Logger logger = LoggerFactory.getLogger(DhtApiTest.class);

    @Test
    public void testFailRecovery() throws IOException, InterruptedException {
        PastryNode.setStabiliation(true);
        PastryNode.STABILIZATION_INTERVAL = 2000;

        PastryNode bootstrap = new PastryNode("localhost", BASE_PORT++, 0, 0);
        bootstrap.initPastry();

        PastryNode node1 = new PastryNode("localhost", BASE_PORT++, 50, 50);
        node1.joinPastry(bootstrap.getNode());

        PastryNode node2 = new PastryNode("localhost", BASE_PORT++, 100, 100);
        node2.joinPastry(bootstrap.getNode());

        PastryNode node3 = new PastryNode("localhost", BASE_PORT++, 150, 150);
        node3.joinPastry(bootstrap.getNode());

        registerAll(bootstrap, node1, node2, node3);

        Thread.sleep(PastryNode.STABILIZATION_INTERVAL * 2L);
        node2.shutdownPastryNode();
        Thread.sleep(PastryNode.STABILIZATION_INTERVAL * 2L);

        // assert that node2 was deleted from all NodeStates
        bootstrap.getAllNodes().forEach(node -> assertNotEquals(node2.getNode().getId(), node.getId()));
        node1.getAllNodes().forEach(node -> assertNotEquals(node2.getNode().getId(), node.getId()));
        node3.getAllNodes().forEach(node -> assertNotEquals(node2.getNode().getId(), node.getId()));
    }
}
