import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import pastry.Util;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class UtilTest {

    @BeforeEach
    void printTestNameToConsole(TestInfo testInfo) {
        System.out.println(System.lineSeparator() + System.lineSeparator()
                + "============== " + testInfo.getTestMethod().map(Method::getName).orElse(null)
                + "() =============" + System.lineSeparator());
    }

    @Test
    public void testGetId() {
        String seznam = "77.75.77.222:80";
        long id = Util.getDistance(seznam);
        assertNotEquals(Long.MAX_VALUE, id);
    }

}
