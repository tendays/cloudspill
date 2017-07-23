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
        left = right+1;
        right = input.indexOf(left, ';');

        if (right == -1) {
            throw new IllegalArgumentException("Not enough components in input");
        }

        return input.substring(left, right - 1);
    }

    public long getLong() {
        return Long.valueOf(getString());
    }
}
