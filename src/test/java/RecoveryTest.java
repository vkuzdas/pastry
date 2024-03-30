import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pastry.PastryNode;
import pastry.Util;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class RecoveryTest extends BaseTest {

    private final Logger logger = LoggerFactory.getLogger(DhtApiTest.class);


    @Test
    public void testLeaveStabilize_MoveKeys() throws IOException, InterruptedException {
        // TODO: scale-up
        PastryNode.setStabiliation(true);
        PastryNode.STABILIZATION_INTERVAL = 1500;
        PastryNode bootstrap = new PastryNode("localhost", BASE_PORT++, 0, 0);
        bootstrap.initPastry();
        runningNodes.add(bootstrap);

        for (int i = 0; i < 10; i++) {
            PastryNode node = new PastryNode("localhost", BASE_PORT++, 0, 0);
            node.joinPastry(bootstrap.getNode());
            runningNodes.add(node);
        }

        List<BigInteger> keys = new ArrayList<>();

        for (int i = 0; i < 20; i++) {
            bootstrap.put("key" + i, "value");
            keys.add(Util.convertToDecimal(Util.getId("key" + i)));
        }


        for (int i = 0; i < 5; i++) {
            Thread.sleep(PastryNode.STABILIZATION_INTERVAL * 2L);
            PastryNode node = runningNodes.get(runningNodes.size()-1);
            node.leavePastry();
            runningNodes.remove(node);
        }


        for (BigInteger key : keys) {
            PastryNode closest = runningNodes.stream().min(Comparator.comparing(n -> n.getNode().getDecimalId().subtract(key).abs())).get();
            assertNotNull(closest.getLocalData().get(key), "Expected " + key + " to be in closest node " + closest.getNode());
        }

    }



    @Test
    public void testLeave_MoveKeys() throws IOException {
        PastryNode bootstrap = new PastryNode("localhost", BASE_PORT++, 0, 0);
        bootstrap.initPastry();
        runningNodes.add(bootstrap);

        List<BigInteger> keys = new ArrayList<>();


        PastryNode node1 = new PastryNode("localhost", BASE_PORT++, 0, 0);
        node1.joinPastry(bootstrap.getNode());
        runningNodes.add(node1);

        for (int i = 0; i < 10; i++) {
            bootstrap.put("key" + i, "value");
            keys.add(Util.convertToDecimal(Util.getId("key" + i)));
        }

        for (BigInteger key : keys) {
            PastryNode closest = runningNodes.stream().min(Comparator.comparing(n -> n.getNode().getDecimalId().subtract(key).abs())).get();
            assertNotNull(closest.getLocalData().get(key), "Expected key to be in closest node");
        }

        bootstrap.leavePastry();
        runningNodes.remove(bootstrap);

        for (BigInteger key : keys) {
            PastryNode closest = runningNodes.stream().min(Comparator.comparing(n -> n.getNode().getDecimalId().subtract(key).abs())).get();
            assertNotNull(closest.getLocalData().get(key), "Expected key to be in closest node");
        }

    }
}
