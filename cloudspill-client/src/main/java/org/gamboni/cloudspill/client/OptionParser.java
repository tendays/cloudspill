package org.gamboni.cloudspill.client;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;

import java.util.Iterator;

/**
 * @author tendays
 */
class OptionParser {
    String next = null;
    final Iterator<String> iterator;

    OptionParser(String... args) {
        this.iterator = Iterators.forArray(args);
    }

    private void ensureNext() {
        if (next == null && iterator.hasNext()) {
            next = iterator.next();
        }
    }

    boolean consume(String option) {
        ensureNext();
        if (option.equals(next)) {
            next = null; // consume
            return true;
        } else {
            return false;
        }
    }

    boolean hasNext() {
        ensureNext();
        return (next != null);
    }

    String next() {
        Preconditions.checkState(hasNext());
        String result = next;
        this.next = null; // consume
        return result;
    }

    String peek() {
        ensureNext();
        return this.next;
    }

    void assertFinished() {
        Preconditions.checkState(!hasNext(), "Unrecognised parameter %s", next);
    }
}
