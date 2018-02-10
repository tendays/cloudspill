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
public abstract class CursorList<T> extends AbstractList<T> {

    private static final String TAG = "CloudSpill.DB";
    protected final Cursor cursor;

    public CursorList(Cursor cursor) {
        this.cursor = cursor;
    }

    protected abstract T newEntity();

    public T get(int index) {
    //                Log.d(TAG, "Getting item "+ index);
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
                // Log.d(TAG, "Getting next item");
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
}
