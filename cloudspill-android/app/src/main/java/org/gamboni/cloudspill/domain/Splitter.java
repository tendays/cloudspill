package org.gamboni.cloudspill.domain;

/**
 * @author tendays
 */

public class Splitter {
    int left;
    int right = -1;
    final String input;

    public Splitter(String input) {
        this.input = input;
    }

    public String getString() {
        if (right == input.length()) {
            throw new IllegalArgumentException("Not enough components in input");
        }
        left = right+1;
        right = input.indexOf(';', left);

        if (right == -1) {
            right = input.length();
        }

        return input.substring(left, right);
    }

    public long getLong() {
        return Long.valueOf(getString());
    }
}
