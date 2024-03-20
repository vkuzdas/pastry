package pastry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static pastry.Constants.BASE_16_IDS;

public class Util {

    private static final Logger logger = LoggerFactory.getLogger(Util.class);

//    @Deprecated
//    /**
//     * Returns ping response time from current to destination address
//     */
//    public static long getDistance(String destAddress, String startAddress) {
//        // for testing purposes, return distance between ports
//        // this metric is good simulation of physical node distance since it is constant
//        if (PastryNode.LOCAL_TESTING) {
//            int destPort = Integer.parseInt(destAddress.split(":")[1]);
//            int startPort = Integer.parseInt(startAddress.split(":")[1]);
//            return Math.abs(destPort - startPort);
//        }
//
//        String[] parts = destAddress.split(":");
//        String host = parts[0];
//        int port = Integer.parseInt(parts[1]);
//
//        try {
//            long startTime = System.currentTimeMillis();
//            try (Socket socket = new Socket()) {
//                socket.connect(new InetSocketAddress(host, port), 2000);
//            }
//
//            return System.currentTimeMillis() - startTime;
//
//        } catch (IOException e) {
//            logger.warn("Host " + host + " on port " + port + " is not reachable: " + e.getMessage());
//            return Long.MAX_VALUE;
//        }
//    }

    /**
     * Given node.address, hash it and return the hash as 4/8/16-base string number
     */
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
        int padding = PastryNode.L_PARAMETER - quat_16bit.length();
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

    /**
     * To-Base is determined by {@link PastryNode#B_PARAMETER}
     */
    public static String convertFromDecimal(BigInteger decimalValue) {
        StringBuilder r = new StringBuilder();
        BigInteger baseValue = BigInteger.valueOf((int)Math.pow(2, PastryNode.B_PARAMETER));

        while (decimalValue.compareTo(BigInteger.ZERO) > 0) {
            BigInteger[] quotientAndRemainder = decimalValue.divideAndRemainder(baseValue);
            int remainder = quotientAndRemainder[1].intValue();
            if (remainder < 10) {
                r.insert(0, remainder);
            } else {
                r.insert(0, (char)('A'+remainder-10));
            }
            decimalValue = quotientAndRemainder[0];
        }

        return r.toString();
    }


    /**
     * From-Base is determined by {@link PastryNode#B_PARAMETER}
     */
    public static BigInteger convertToDecimal(String strNumber) {
        BigInteger r = BigInteger.ZERO;
        for (int i = 0; i < strNumber.length(); i++) {
            char dig = strNumber.charAt(i);
            int d,e;
            if (dig <= '9') {
                d = dig - '0';
                e = strNumber.length()-i-1;
            } else {
                d = dig - 'A'+10;
                e = strNumber.length()-i-1;
            }
            BigInteger m = BigInteger.valueOf(2).pow(PastryNode.B_PARAMETER).pow(e);
            r = r.add(BigInteger.valueOf(d).multiply(m));
        }
        return r;
    }
}
