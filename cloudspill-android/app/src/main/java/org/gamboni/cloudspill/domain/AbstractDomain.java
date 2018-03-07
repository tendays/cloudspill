package org.gamboni.cloudspill.domain;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.AbstractList;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/** Base class for database data model.
 *
 * @author tendays
 */
public abstract class AbstractDomain extends SQLiteOpenHelper {

    protected static final String TAG = "CloudSpill.Domain";

    protected final Context context;

    protected AbstractDomain(Context context, String dbName, int dbVersion) {
        super(context, dbName, null, dbVersion);
        this.context = context;
    }

    protected static void newColumn(SQLiteDatabase db, int oldVersion, int newVersion, int tableSince, int since, String table, String column, String type) {
        // If table did not exist in oldVersion then newTable created it with all required columns
        // so we don't need to add the column here
        if (oldVersion < since && newVersion >= since && oldVersion >= tableSince) {
            db.execSQL("ALTER TABLE "+ table +" ADD COLUMN "+ column +" "+ type);
        } // TODO remove column on downgrade
    }

    protected static void newTable(SQLiteDatabase db, int oldVersion, int newVersion, int since, String create, String delete) {
        if (oldVersion < since && newVersion >= since) {
            db.execSQL(create);
        } else if (oldVersion >= since && newVersion < since) {
            db.execSQL(delete);
        }
    }

    private class ColumnQuery<U> extends Query<U> {
        final String selection;
        final Class<U> columnType;

        ColumnQuery(Query<?> base, String selection, Class<U> columnType) {
            super(base.tableName);
            this.restriction = base.restriction;
            this.ordering = base.ordering;
            this.args.addAll(base.args);

            this.selection = selection;
            this.columnType = columnType;
        }

        @Override
        public List<U> list() {
            return new CursorList<U>(list(selection)) {
                @Override
                protected U newEntity() {
                    if (columnType == String.class) {
                        return (U) cursor.getString(0);
                    } else {
                        throw new UnsupportedOperationException("Selecting type "+ columnType.getSimpleName() +" not supported");
                    }
                }
            };
        }
    }

    public abstract class Query<T> {
        final String tableName;
        String restriction = null;
        String ordering = null;
        Cursor cursor = null;
        protected final List<String> args = new ArrayList<>();

        Query(String tableName) {
            this.tableName = tableName;
        }

        /** Add an equality condition. {@code value} may be null, in which case an "is null" restriction
         * is generated.
         */
        public Query<T> eq(String column, Object value) {
            return (value == null) ? restriction(column +" is null") : restriction(column +" = ?", value);
        }

        /** Add a "greater-than-or-equal" restriction. */
        public Query<T> ge(String column, Object value) {
            return restriction(column +" >= ?", preprocess(value));
        }

        /** Add a "less-than-or-equl" restriction. */
        public Query<T> le(String column, Object value) {
            return restriction(column +" <= ?", preprocess(value));
        }

        public Query<T> like(String column, String pattern) {
            return restriction(column +" like ?", pattern);
        }

        protected Query<T> restriction(String sql, Object arg) {
            restriction(sql);
            args.add(String.valueOf(arg));

            return this;
        }

        protected Query<T> restriction(String sql) {
            if (restriction == null) {
                restriction = sql;
            } else {
                restriction += " and "+ sql;
            }
            return this;
        }

        public <U> Query<U> selectDistinct(String column, Class<U> columnType) {
            return new ColumnQuery<>(this, column, columnType);
        }

        public Query<T> orderAsc(String column) {
            ordering = (ordering == null) ? "" : (ordering +", ");
            ordering += column;
            return this;
        }

        public Query<T> orderDesc(String column) {
            ordering = (ordering == null) ? "" : (ordering +", ");
            ordering += column +" DESC";
            return this;
        }

        protected Cursor list(String... columns) {
            cursor = connect().query(
                    tableName, columns, restriction,
                    restrictionArgs(), null, null, ordering);

            cursors.add(cursor);
            return cursor;
        }

        /** Return a List of results that dynamically read through the Cursor.
         * You must close() this object when you are done accessing the list.
         */
        public abstract List<T> list();

        /** Return a List of result that is entirely copied in memory. You
         * must not close() this object when you are done.
         */
        public List<T> detachedList() {
            List<T> result = new ArrayList<>(list());
            close();
            return result;
        }

        public int update(ContentValues values) {
            return connect().update(tableName, values, restriction, restrictionArgs());
        }

        /** Delete all rows corresponding to this query.
         *
         * @return the number of affected rows.
         */
        public int delete() {
            return connect().delete(tableName, restriction, restrictionArgs());
        }

        private String[] restrictionArgs() {
            return args.toArray(new String[args.size()]);
        }

        public void close() {
            cursor.close();
        }
    }

    protected abstract class TrackingList<T, E> extends AbstractList<T> {
        private final List<E> delegate;
        public TrackingList(List<E> delegate) {
            this.delegate = delegate;
        }
        @Override
        public void add(int index, T item) {
            E entity = wrap(item);
            insert(entity);
            delegate.add(index, entity);
        }

        public T get(int index) {
            return extract(delegate.get(index));
        }

        public T set(int index, T item) {
            E entity = wrap(item);
            E oldEntity = delegate.set(index, entity);
            delete(oldEntity);
            insert(entity);
            return extract(oldEntity);
        }

        public T remove(int index) {
            E oldEntity = delegate.remove(index);
            delete(oldEntity);
            return extract(oldEntity);
        }

        @Override
        public int size() {
            return delegate.size();
        }

        protected abstract T extract(E entity);

        protected abstract E wrap(T item);

        protected abstract void insert(E entity);

        protected abstract void delete(E entity);

        protected abstract void flush(E entity);

        public void flush() {
            for (int i=0; i<delegate.size(); i++) {
                flush(delegate.get(i));
            }
        }
    }

    private SQLiteDatabase connection;
    /** All cursors returned by this class, that are still open. */
    protected List<Cursor> cursors = new ArrayList<>();

    /** Gets the data repository in write mode */
    protected SQLiteDatabase connect() {
        if (connection != null && connection.isOpen() && connection.isReadOnly()) {
            closeCursors();
            closeConnection();
        }
        if (connection == null || !connection.isOpen()) {
            connection = getWritableDatabase();
            afterConnect();
        }
        return connection;
    }

    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }

    protected void afterConnect() {

    }

    public void close() {
        closeCursors();
        super.close();
    }

    public void closeConnection() {
        if (connection != null) {
            connection.close();
            connection = null;
        }
    }

    public void closeCursors() {
        List<Cursor> copy = new ArrayList<>(cursors);
        cursors.clear();
        for (Cursor c : cursors) {
            c.close();
        }
    }

    /** Wrap a sql expression in a MAX() aggregate function. */
    protected static String max(String column) {
        return "MAX("+ column +")";
    }

    /** Convert a nullable number coming from database to a Date going to the corresponding entity object. */
    protected static Date toDate(Long value) {
        return (value == null) ? null : new Date(value);
    }

    /** Convert a nullable Date coming from an entity object to a number suitable for storing in the database. */
    protected static Long fromDate(Date date) {
        return (date == null) ? null : date.getTime();
    }

    private static Object preprocess(Object value) {
        if (value instanceof Date) {
            return fromDate((Date)value);
        } else {
            return value;
        }
    }
}
