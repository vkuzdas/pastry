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

}
