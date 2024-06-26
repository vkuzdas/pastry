import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import pastry.PastryNode;

import static pastry.Constants.BASE_16_IDS;
import static pastry.Constants.LEAF_SET_SIZE_32;

@Disabled("Works locally but not in CI")
public class DhtApiHexTest extends DhtApiTest {
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
