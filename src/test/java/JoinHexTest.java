import org.junit.jupiter.api.BeforeEach;
import pastry.PastryNode;

import static pastry.Constants.*;

public class JoinHexTest extends JoinTest {

    @Override
    @BeforeEach
    public void init() {
        PastryNode.setStabiliation(false);
        PastryNode.setBase(BASE_16_IDS);
        PastryNode.setLeafSize(LEAF_SET_SIZE_32);
        MAX_NODES = isRunningInCI() ? 10 : 50;
        MAX_KEYS = 2 * MAX_NODES;
    }

}
