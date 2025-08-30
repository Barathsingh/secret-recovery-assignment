package org.example;

import org.json.JSONObject;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

    public class SecretRecover {

    // Decode value with given base to BigInteger
    private static BigInteger decodeY(int base, String value) {
        return new BigInteger(value, base);
    }

    // Extract shares (first k shares sorted by x)
    private static List<int[]> parseShares(JSONObject json) {
        JSONObject keys = json.getJSONObject("keys");
        int k = keys.getInt("k");

        List<int[]> shares = new ArrayList<>();
        for (String key : json.keySet()) {
            if (!key.equals("keys")) {
                int x = Integer.parseInt(key);
                JSONObject obj = json.getJSONObject(key);
                int base = obj.getInt("base");
                String value = obj.getString("value");
                BigInteger y = decodeY(base, value);
                shares.add(new int[]{x, y.intValue()});
            }
        }

        shares.sort(Comparator.comparingInt(a -> a[0]));
        return shares.subList(0, k);
    }

    // Lagrange Interpolation at x = 0
    private static BigInteger lagrange(List<int[]> shares) {
        int k = shares.size();
        BigInteger secret = BigInteger.ZERO;

        for (int i = 0; i < k; i++) {
            int xi = shares.get(i)[0];
            BigInteger yi = BigInteger.valueOf(shares.get(i)[1]);

            BigInteger numerator = BigInteger.ONE;
            BigInteger denominator = BigInteger.ONE;

            for (int j = 0; j < k; j++) {
                if (i != j) {
                    int xj = shares.get(j)[0];
                    numerator = numerator.multiply(BigInteger.valueOf(-xj));
                    denominator = denominator.multiply(BigInteger.valueOf(xi - xj));
                }
            }

            BigInteger term = yi.multiply(numerator).divide(denominator);
            secret = secret.add(term);
        }

        // Always positive
        if (secret.signum() < 0) {
            secret = secret.negate();
        }
        return secret;
    }

    // Verify shares against reconstructed polynomial
    private static void verifyShares(BigInteger secret, List<int[]> shares) {
        for (int[] share : shares) {
            int x = share[0];
            BigInteger y = BigInteger.valueOf(share[1]);

            // Recalculate polynomial f(x) using Lagrange for this x
            BigInteger fx = BigInteger.ZERO;
            int k = shares.size();

            for (int i = 0; i < k; i++) {
                int xi = shares.get(i)[0];
                BigInteger yi = BigInteger.valueOf(shares.get(i)[1]);

                BigInteger numerator = BigInteger.ONE;
                BigInteger denominator = BigInteger.ONE;

                for (int j = 0; j < k; j++) {
                    if (i != j) {
                        int xj = shares.get(j)[0];
                        numerator = numerator.multiply(BigInteger.valueOf(x - xj));
                        denominator = denominator.multiply(BigInteger.valueOf(xi - xj));
                    }
                }
                BigInteger term = yi.multiply(numerator).divide(denominator);
                fx = fx.add(term);
            }

            // If mismatch → this share is wrong
            if (!fx.equals(y)) {
                System.out.println("⚠ Wrong share detected from participant with x = " + x);
                throw new RuntimeException("Invalid share detected. Secret cannot be recovered safely!");
            }
        }
    }

    // Process one file
    private static BigInteger processFile(String filename) throws IOException {
        String content = new String(Files.readAllBytes(Paths.get(filename)));
        JSONObject json = new JSONObject(content);
        List<int[]> shares = parseShares(json);

        BigInteger secret = lagrange(shares);

        // ✅ Verify before returning
        verifyShares(secret, shares);

        return secret;
    }

    public static void main(String[] args) throws IOException {
        System.out.println("Recovering secrets...");

        try {
            BigInteger secret1 = processFile("firstcase.json");
            System.out.println("Recovered secret from case1: " + secret1);
        } catch (RuntimeException e) {
            System.out.println("Case1 failed: " + e.getMessage());
        }

        try {
            BigInteger secret2 = processFile("second.json");
            System.out.println("Recovered secret from case2: " + secret2);
        } catch (RuntimeException e) {
            System.out.println("Case2 failed: " + e.getMessage());
        }
    }
}
