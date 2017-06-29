package org.gamboni.cloudspill.domain;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/** Represents an item that exists on the server.
 *
 * @author tendays
 */
public class Domain extends SQLiteOpenHelper {

    // If you change the database schema, you must increment the database version.
    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "CloudSpill.db";

    private static final String[] ITEM_COLUMNS = new String[]{
            Item._ID,
            Item._SERVER_ID,
            Item._USER,
            Item._FOLDER,
            Item._PATH,
            Item._LATEST_ACCESS
    };

    public class Item implements BaseColumns {
        private static final String TABLE_NAME = "ITEM";
        /* Columns */
        private static final String _SERVER_ID = "SERVER_ID";
        private static final String _USER = "USER";
        private static final String _FOLDER = "FOLDER";
        private static final String _PATH = "PATH";
        private static final String _LATEST_ACCESS = "LATEST_ACCESS";

        private static final String SQL_CREATE_ENTRIES =
                "CREATE TABLE " + TABLE_NAME + " (" +
                        _ID + " INTEGER PRIMARY KEY, " +
                        _SERVER_ID + " INTEGER, " +
                        _USER + " TEXT, " +
                        _FOLDER + " TEXT, " +
                        _PATH + " TEXT, " +
                        _LATEST_ACCESS + " DATE" +
                        ")";

        private static final String SQL_DELETE_ENTRIES =
                "DROP TABLE "+ TABLE_NAME +" IF EXISTS";

        public long id;
        public long serverId;
        public String user;
        public String folder;
        public String path;
        public Date latestAccess; // TODO how to read/write that in db?

        private ContentValues getValues() {
            ContentValues result = new ContentValues();
            result.put(_SERVER_ID, serverId);
            result.put(_USER, user);
            result.put(_FOLDER, folder);
            result.put(_PATH, path);
//            result.put(_LATEST_ACCESS, latestAccess);
            return result;
        }

        public Item() {}

        private Item(Cursor cursor) {
            id = cursor.getLong(0);
            serverId = cursor.getLong(1);
            user = cursor.getString(2);
            folder = cursor.getString(3);
            path = cursor.getString(4);
        }

        public long insert() {
            return connect().insert(TABLE_NAME, null, getValues());
        }

    }

    public Domain(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(Item.SQL_CREATE_ENTRIES);
    }

    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // This database is only a cache for online data, so its upgrade policy is
        // to simply to discard the data and start over
        db.execSQL(Item.SQL_DELETE_ENTRIES);
        onCreate(db);
    }

    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }

    public int getItemCount() {
        return (int)DatabaseUtils.queryNumEntries(connect(), Item.TABLE_NAME);
    }

    public List<Item> selectItems() {
        final Cursor cursor = connect().query(
                Item.TABLE_NAME, ITEM_COLUMNS, null, null, null, null, null, null);
        this.cursors.add(cursor);

        return new AbstractList<Item>() {

            public Item get(int index) {
                cursor.moveToPosition(index);
                return new Item(cursor);
            }

            public int size() {
                return cursor.getCount();
            }

            @Override
            public Iterator<Item> iterator() {
                return new Iterator<Item>() {
                    boolean ready = false;
                    Item next;
                    @Override
                    public boolean hasNext() {
                        tryNext();
                        return next != null;
                    }

                    @Override
                    public Item next() {
                        if (!hasNext()) {
                            throw new NoSuchElementException();
                        }
                        Item result = next;
                        next = null;
                        ready = false;
                        return result;
                    }

                    private void tryNext() {
                        if (!ready) {
                            if (cursor.moveToNext()) {
                                next = new Item(cursor);
                            }
                            ready = true;
                        }
                    }
                };
            }
        };
    }

    private SQLiteDatabase connection;
    /** All cursors returned by this class, that are still open. */
    private List<Cursor> cursors = new ArrayList<>();

    /** Gets the data repository in write mode */
    private SQLiteDatabase connect() {
        if (connection != null && connection.isOpen() && connection.isReadOnly()) {
            closeCursors();
            closeConnection();
        }
        if (connection == null || !connection.isOpen()) {
            connection = getWritableDatabase();
        }
        return connection;
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
}