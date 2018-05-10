package org.gamboni.cloudspill.collect;

import java.util.AbstractList;
import java.util.List;

/**
 * @author tendays
 */

public class ConcatList<T> extends AbstractList<T> {
    final List<T> left, right;

    public ConcatList(List<T> left, List<T> right) {
        this.left = left;
        this.right = right;
    }

    @Override
    public T get(int index) {
        final int leftSize = left.size();
        return (index < leftSize) ? left.get(index) : right.get(index - leftSize);
    }

    @Override
    public int size() {
        return left.size() + right.size();
    }
}
