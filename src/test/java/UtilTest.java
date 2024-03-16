import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pastry.PastryNode;
import pastry.Util;

import java.lang.reflect.Method;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static pastry.Constants.BASE_4_IDS;

public class UtilTest {

    Logger logger = LoggerFactory.getLogger(UtilTest.class);

    @BeforeEach
    void printTestNameToConsole(TestInfo testInfo) {
        logger.warn(System.lineSeparator() + System.lineSeparator()
                + "============== " + testInfo.getTestMethod().map(Method::getName).orElse(null)
                + "() =============" + System.lineSeparator());
    }

    @Test
    public void testGetId() {
        logger.warn("logger in CI test");
        PastryNode.LOCAL_TESTING = false;
        String seznam = "77.75.77.222:80";
        long id = Util.getDistance(seznam, "localhost:8080");
        assertNotEquals(Long.MAX_VALUE, id);
    }

}
