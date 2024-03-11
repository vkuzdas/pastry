package pastry;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Util {

    public static String getId(String input) {
        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace(System.err);
            return BigInteger.ONE.negate().toString();
        }

        BigInteger decimal_128bit = new BigInteger(1, md.digest(input.getBytes(StandardCharsets.UTF_8)));

        // TODO: parametrize according to L and b
        BigInteger decimal_16bit = decimal_128bit.mod(BigInteger.valueOf(2L).pow(16));

        String quat_16bit = decToQuat(decimal_16bit);
        int padding = PastryNode.l - quat_16bit.length();
        if (padding > 0) {
            StringBuilder paddingBuilder = new StringBuilder();
            for (int i = 0; i < padding; i++) {
                paddingBuilder.append("0");
            }
            quat_16bit = paddingBuilder + quat_16bit;
        }
        return quat_16bit;
    }

    public static String decToQuat(BigInteger decimalNumber) {
        StringBuilder quaternaryNumber = new StringBuilder();

        while (decimalNumber.compareTo(BigInteger.ZERO) > 0) {
            BigInteger[] quotientAndRemainder = decimalNumber.divideAndRemainder(BigInteger.valueOf(4));
            BigInteger remainder = quotientAndRemainder[1];

            quaternaryNumber.insert(0, remainder);

            decimalNumber = quotientAndRemainder[0];
        }

        if (quaternaryNumber.length() == 0) {
            return "0";
        }

        return quaternaryNumber.toString();
    }
}
