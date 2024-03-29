import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pastry.PastryNode;

import java.util.ArrayList;
import java.util.Arrays;

import static pastry.Constants.BASE_4_IDS;
import static pastry.Constants.LEAF_SET_SIZE_8;

public class BaseTest {

    protected final Logger logger = LoggerFactory.getLogger(BaseTest.class);
    protected static int BASE_PORT = 10_000;
    protected int MAX_NODES;
    protected int MAX_KEYS;
    protected final ArrayList<PastryNode> nodes = new ArrayList<>();

    @BeforeEach
    public void printInfo(TestInfo testInfo) {
        logger.warn(System.lineSeparator() + System.lineSeparator()+ "============== {} =============" + System.lineSeparator(), testInfo.getDisplayName());
    }

    public static boolean isRunningInCI() {
        String ci = System.getenv("CI");
        return "true".equals(ci);
    }

    @BeforeEach
    public void init() {
        PastryNode.setStabiliation(false);
        PastryNode.setBase(BASE_4_IDS);
        PastryNode.setLeafSize(LEAF_SET_SIZE_8);
        MAX_NODES = isRunningInCI() ? 10 : 50;
        MAX_KEYS = isRunningInCI() ? 15 : 50;
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


}
