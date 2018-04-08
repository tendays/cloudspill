package org.gamboni.cloudspill.domain;

import android.database.Cursor;
import android.support.annotation.NonNull;

import java.util.AbstractSequentialList;
import java.util.ListIterator;

/**
 * @author tendays
 */

public abstract class GroupingCursorList<T> extends AbstractSequentialList<T> implements AbstractDomain.CloseableList<T> {

    private static final String TAG = "CloudSpill.DB";
    protected final Cursor cursor;
    protected final AbstractDomain.Column<?> groupingColumn;
    private final int groupingColumnIndex;
    /** this.get(n) maps to cursor at position cursorIndex[n]. */
    private int[] cursorIndex = new int[30];
    /** cursorIndex[n] is defined for 0 ≤ n < cursorIndexSize. */
    private int cursorIndexSize = 0;
    /** cursor.size(), or -1 if not known. */
    private int cachedBaseSize = -1;
    private boolean touchedEnd = false;

    protected GroupingCursorList(Cursor cursor, AbstractDomain.Column<?> groupingColumn, int groupingColumnIndex) {
        this.cursor = cursor;
        this.groupingColumn = groupingColumn;
        this.groupingColumnIndex = groupingColumnIndex;
    }

    protected abstract T newEntity();

    private static boolean eq(Object l, Object r) {
        return (l == null) ? (r == null) : l.equals(r);
    }

    @Override
    public int size() {
        ensureIndexed(Integer.MAX_VALUE);
        return cursorIndexSize;
    }

    /** Increase cursorIndexSize if necessary to be larger than index. cursorIndexSize
     * may still be less than or equal to index after this method returns in case index
     * exceeds this.size(). */
    private void ensureIndexed(final int index) {
        if (touchedEnd) { return; }
        int pointer;
        Object currentId;
        if (cursorIndexSize == 0) {
            pointer = -1;
            currentId = null;
        } else {
            pointer = cursorIndex[cursorIndexSize - 1];
            cursor.moveToPosition(pointer);
            currentId = groupingColumn.getFrom(cursor, groupingColumnIndex);
        }
        /* Loop invariant: the grouping column at position 'pointer' has id 'currentId'. */
        while (cursorIndexSize < index && pointer+1 < getBaseSize()) {
            pointer++;
            cursor.moveToPosition(pointer);
            Object nextId = groupingColumn.getFrom(cursor, groupingColumnIndex);
            if (!eq(currentId, nextId)) {
                setCursorIndex(cursorIndexSize, pointer);
                currentId = nextId;
            }
        }
        /* We either leave the loop if cursorIndexSize has reached index,
         * or if pointer has reached the end of cursor data, i.e. cursorIndex is full.
         */
        if (cursorIndexSize < index) { touchedEnd = true; }
    }

    /** Sets {@code cursorIndex[index]} to {@code value}, first enlarging the array if necessary. */
    private void setCursorIndex(int index, int value) {
        if (cursorIndex.length <= index) {
            int[] newArray = new int[(int)(index * 1.2)];
            System.arraycopy(cursorIndex, 0, newArray, 0, cursorIndex.length);
            cursorIndex = newArray;
        }
        cursorIndex[index] = value;
        if (cursorIndexSize <= index) {
            cursorIndexSize = index+1;
        }
    }

    private int getBaseSize() {
        if (cachedBaseSize == -1) {
            cachedBaseSize = cursor.getCount();
        }
        return cachedBaseSize;
    }

    public int approximateSize() {
        return (cursorIndexSize > 0) ? (cursorIndexSize + 1) * getBaseSize() / (cursorIndex[cursorIndexSize - 1] + 1) :
                getBaseSize();
    }

    @Override
    public @NonNull ListIterator<T> listIterator(final int initialIndex) {
        return new ListIterator<T>() {
            // next() should return this value, previous() should return index-1
            int index = initialIndex;

            @Override
            public boolean hasNext() {
                ensureIndexed(index);
                return (cursorIndexSize > index);
            }

            @Override
            public T next() {
                ensureIndexed(index);
                cursor.moveToPosition(cursorIndex[index]);
                index++;
                return newEntity();
            }

            @Override
            public boolean hasPrevious() {
                return index > 0;
            }

            @Override
            public T previous() {
                index--;
                cursor.moveToPosition(cursorIndex[index]);
                return newEntity();
            }

            @Override
            public int nextIndex() {
                return index;
            }

            @Override
            public int previousIndex() {
                return index - 1;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException("Modifying grouping cursor lists is currently not supported");
            }

            @Override
            public void set(T t) {
                throw new UnsupportedOperationException("Modifying grouping cursor lists is currently not supported");
            }

            @Override
            public void add(T t) {
                throw new UnsupportedOperationException("Modifying grouping cursor lists is currently not supported");
            }
        };
    }

    @Override
    public void close() {
        this.cursor.close();
    }
}
