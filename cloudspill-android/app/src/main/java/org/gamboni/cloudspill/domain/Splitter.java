package org.gamboni.cloudspill.domain;

import java.util.Collection;

/**
 * @author tendays
 */

public class Splitter {
    int left;
    int right = -1;
    final String input;
    final char separator;

    public Splitter(String input, char separator) {
        this.input = input;
        this.separator = separator;
    }

    public String getString() {
        if (right == input.length()) {
            throw new IllegalArgumentException("Not enough components in input");
        }
        left = right+1;
        right = input.indexOf(separator, left);

        if (right == -1) {
            right = input.length();
        }

        return input.substring(left, right);
    }

    public Long getLong() {
        String string = getString();

        return string.isEmpty() ? null : Long.valueOf(string);
    }

    public Collection<String> allRemainingTo(Collection<String> target) {
        while (right < input.length()) {
            target.add(getString());
        }
        return target;
    }
}
