import org.junit.jupiter.api.Test;
import pastry.Util;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class UtilTest {

    @Test
    public void testGetId() {
        String seznam = "77.75.77.222:80";
        long id = Util.getDistance(seznam);
        assertNotEquals(Long.MAX_VALUE, id);
    }

    @Test
    public void testGetId_google() {
        String google = "142.251.36.110:80";
        long id = Util.getDistance(google);
        assertNotEquals(Long.MAX_VALUE, id);
    }

}
