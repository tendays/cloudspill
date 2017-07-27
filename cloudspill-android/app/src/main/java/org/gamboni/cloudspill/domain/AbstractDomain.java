package org.gamboni.cloudspill.domain;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Base class for database data model.
 *
 * @author tendays
 */
public abstract class AbstractDomain extends SQLiteOpenHelper {

    protected final Context context;


    protected AbstractDomain(Context context, String dbName, int dbVersion) {
        super(context, dbName, null, dbVersion);
        this.context = context;
    }

    protected static void newColumn(SQLiteDatabase db, int oldVersion, int newVersion, int since, String table, String column, String type) {
        if (oldVersion < since && newVersion >= since) {
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

    public abstract class Query<T> {
        final String tableName;
        String selection = null;
        String ordering = null;
        Cursor cursor = null;
        private List<String> args = new ArrayList<>();

        Query(String tableName) {
            this.tableName = tableName;
        }

        public Query<T> eq(String column, Object value) {
            if (selection == null) {
                selection = column +" = ?";
            } else {
                selection += " and "+ column +" = ?";
            }
            args.add(String.valueOf(value));

            return this;
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

        protected Cursor list(String[] columns) {
            cursor = connect().query(
                    tableName, columns, selection,
                    selectionArgs(), null, null, ordering);

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

        void update(ContentValues values) {
            connect().update(tableName, values, selection, selectionArgs());
        }

        private String[] selectionArgs() {
            return args.toArray(new String[args.size()]);
        }

        public void close() {
            cursor.close();
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
        }
        return connection;
    }

    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
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
}
