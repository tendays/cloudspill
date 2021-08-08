package org.gamboni.cloudspill.server;

import java.security.SecureRandom;

/**
 * @author tendays
 */
public abstract class Security {
    public static String newRandomString(int length) {
        String chars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        final SecureRandom random = new SecureRandom();
        return random.ints(length).map(n -> chars.charAt(Math.abs(n) % (chars.length())))
                .collect(StringBuilder::new, (builder, chr) -> builder.append((char) chr),
                        (a, b) -> {
                            throw new UnsupportedOperationException();
                        }).toString();
    }
}
