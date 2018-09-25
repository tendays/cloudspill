package org.gamboni.cloudspill.domain;

import android.database.Cursor;
import android.util.Log;

import java.util.AbstractList;
import java.util.Iterator;
import java.util.NoSuchElementException;

/** List view of a Cursor.
 *
 * @author tendays
 */
public abstract class CursorList<T> extends AbstractList<T> implements AbstractDomain.CloseableList<T> {

    private static final String TAG = "CloudSpill.DB";
    protected final Cursor cursor;
    private final String description;

    protected CursorList(Cursor cursor) {
        this.description = "<unnamed>";
        this.cursor = cursor;
    }

    protected CursorList(String description, Cursor cursor) {
        this.description = description;
        this.cursor = cursor;
    }

    protected abstract T newEntity();

    public T get(int index) {
        cursor.moveToPosition(index);
        return newEntity();
    }

    private int cachedSize = -1;

    public int size() {
        if (cachedSize == -1) {
            cachedSize = cursor.getCount();
        }
        return cachedSize;
    }

    @Override
    public Iterator<T> iterator() {
        Log.d(TAG, description +" creating iterator from position "+ cursor.getPosition());
        return new Iterator<T>() {
            boolean ready = false;
            T next;

            @Override
            public boolean hasNext() {
                tryNext();
                return next != null;
            }

            @Override
            public T next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                T result = next;
                next = null;
                ready = false;
                return result;
            }

            private void tryNext() {
                if (!ready) {
                    if (cursor.moveToNext()) {
                        next = newEntity();
                    }
                    ready = true;
                }
            }
        };
    }

    @Override
    public void close() {
        cursor.close();
    }
}
