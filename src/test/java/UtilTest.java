import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pastry.Util;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class UtilTest {

    Logger logger = LoggerFactory.getLogger(UtilTest.class);

    @BeforeEach
    void printTestNameToConsole(TestInfo testInfo) {
        System.out.println(System.lineSeparator() + System.lineSeparator()
                + "============== " + testInfo.getTestMethod().map(Method::getName).orElse(null)
                + "() =============" + System.lineSeparator());
    }

    @Test
    public void testGetId() {
        logger.warn("logger in CI test");
        String seznam = "77.75.77.222:80";
        long id = Util.getDistance(seznam);
        assertNotEquals(Long.MAX_VALUE, id);
    }

}
