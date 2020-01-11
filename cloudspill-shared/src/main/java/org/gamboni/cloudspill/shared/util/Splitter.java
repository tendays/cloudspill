package org.gamboni.cloudspill.shared.util;

import java.util.Collection;

/**
 * @author tendays
 */

public class Splitter {
    private int left;
    private int right = -1;
    private final String input;
    private final char separator;
    private boolean autoTrim = false;

    public Splitter(String input, char separator) {
        this.input = input;
        this.separator = separator;
    }

    public Splitter trimValues() {
        this.autoTrim = true;
        return this;
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

        final String component = input.substring(left, right);
        return (autoTrim) ? component.trim() : component;
    }

    public Long getLong() {
        String string = getString();

        return string.isEmpty() ? null : Long.valueOf(string);
    }

    public <T extends Collection<String>> T allRemainingTo(T target) {
        while (right < input.length()) {
            final String item = getString();
            if (item.length() > 0) {
                target.add(item);
            }
        }
        return target;
    }
}
