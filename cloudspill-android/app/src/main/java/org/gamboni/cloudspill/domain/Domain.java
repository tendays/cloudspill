package org.gamboni.cloudspill.domain;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;

import org.gamboni.cloudspill.file.FileBuilder;
import org.gamboni.cloudspill.ui.SettingsActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Represents an item that exists on the server.
 *
 * @author tendays
 */
public class Domain extends SQLiteOpenHelper {

    private static final String TAG = "CloudSpill.Domain";

    // If you change the database schema, you must increment the database version.
    private static final int DATABASE_VERSION = 3;
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
                        _LATEST_ACCESS + " INTEGER" +
                        ")";

        private static final String SQL_DELETE_ENTRIES =
                "DROP TABLE "+ TABLE_NAME +" IF EXISTS";

        public long id;
        public long serverId;
        public String user;
        public String folder;
        public String path;
        public Date latestAccess;

        public Item() {}

        private Item(Cursor cursor) {
            id = cursor.getLong(0);
            serverId = cursor.getLong(1);
            user = cursor.getString(2);
            folder = cursor.getString(3);
            path = cursor.getString(4);
            latestAccess = new Date(cursor.getLong(5));
        }

        /** Construct an Item from its serialized form as constructed by the server. */
        public Item(String serialisedForm) {
            Splitter splitter = new Splitter(serialisedForm);
            serverId = splitter.getLong();
            user = splitter.getString();
            folder = splitter.getString();
            path = splitter.getString();
        }

        private Boolean local = null;
        public boolean isLocal() {
            if (local == null) {
                local = (user.equals(SettingsActivity.getUser(context)));
                //&&                       folder.equals(SettingsActivity.getFolder(context)));
            }
            return local;
        }

        FileBuilder file = null;
        public FileBuilder getFile() {
            if (file != null) { return file; }
            if (isLocal()) {
                for (Folder folder : selectFolders()) {
                    if (folder.name.equals(this.folder)) {
                        file = folder.getFile().append(path);
                        return file;
                    }
                }
            }

            file = SettingsActivity.getDownloadPath(context).append(user).append(folder).append(path);
            return file;
        }

        private ContentValues getValues() {
            ContentValues result = new ContentValues();
            result.put(_SERVER_ID, serverId);
            result.put(_USER, user);
            result.put(_FOLDER, folder);
            result.put(_PATH, path);
            result.put(_LATEST_ACCESS, latestAccess.getTime());
            return result;
        }

        public long insert() {
            return connect().insert(TABLE_NAME, null, getValues());
        }
    }

    private static final String[] FOLDER_COLUMNS = {
            Folder._ID,
            Folder._NAME,
            Folder._PATH
    };
    public class Folder implements BaseColumns {
        public static final String TABLE_NAME="FOLDER";
        public long id;
        public static final String _NAME = "NAME";
        public String name;
        public static final String _PATH = "PATH";
        public String path;

        private static final String SQL_CREATE_ENTRIES =
                "CREATE TABLE " + TABLE_NAME + " (" +
                        _ID + " INTEGER PRIMARY KEY, " +
                        _NAME + " TEXT, " +
                        _PATH + " TEXT" +
                        ")";

        private static final String SQL_DELETE_ENTRIES =
                "DROP TABLE "+ TABLE_NAME +" IF EXISTS";

        public Folder() {}

        private Folder(Cursor cursor) {
            id = cursor.getLong(0);
            name = cursor.getString(1);
            path = cursor.getString(2);
        }

        private ContentValues getValues() {
            ContentValues result = new ContentValues();
            result.put(_ID, id);
            result.put(_NAME, name);
            result.put(_PATH, path);
            return result;
        }
        public long insert() {
            return connect().insert(TABLE_NAME, null, getValues());
        }

        public FileBuilder getFile() {
            return new FileBuilder(path);
        }
    }


    private static final String[] SERVER_COLUMNS = {
            Server._ID,
            Server._NAME,
            Server._URL
    };
    public class Server implements BaseColumns {
        public static final String TABLE_NAME="SERVER";
        public long id;
        public static final String _NAME = "NAME";
        public String name;
        public static final String _URL = "URL";
        public String url;

        private static final String SQL_CREATE_ENTRIES =
                "CREATE TABLE " + TABLE_NAME + " (" +
                        _ID + " INTEGER PRIMARY KEY, " +
                        _NAME + " TEXT, " +
                        _URL + " TEXT" +
                        ")";

        private static final String SQL_DELETE_ENTRIES =
                "DROP TABLE "+ TABLE_NAME +" IF EXISTS";

        public Server() {}

        private Server(Cursor cursor) {
            id = cursor.getLong(0);
            name = cursor.getString(1);
            url = cursor.getString(2);
        }

        private ContentValues getValues() {
            ContentValues result = new ContentValues();
            result.put(_ID, id);
            result.put(_NAME, name);
            result.put(_URL, url);
            return result;
        }
        public long insert() {
            return connect().insert(TABLE_NAME, null, getValues());
        }
    }

    private final Context context;

    public Domain(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context;
    }
    public void onCreate(SQLiteDatabase db) {
        onUpgrade(db, 0, DATABASE_VERSION);
    }

    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }

    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion, 3, Server.SQL_CREATE_ENTRIES, Server.SQL_DELETE_ENTRIES);
        onUpgrade(db, oldVersion, newVersion, 2, Folder.SQL_CREATE_ENTRIES, Folder.SQL_DELETE_ENTRIES);
        onUpgrade(db, oldVersion, newVersion, 1, Item.SQL_CREATE_ENTRIES, Item.SQL_DELETE_ENTRIES);
    }

    private static void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion, int since, String create, String delete) {
        if (oldVersion < since && newVersion >= since) {
            db.execSQL(create);
        } else if (oldVersion >= since && newVersion < since) {
            db.execSQL(delete);
        }
    }

    public int getItemCount() {
        return (int)DatabaseUtils.queryNumEntries(connect(), Item.TABLE_NAME);
    }

    public int getHighestId() {
        return connect().query(Item.TABLE_NAME, new String[]{max(Item._SERVER_ID)}, null, null, null, null, null)
                .getInt(0);
    }

    public List<Item> selectItems(boolean recentFirst) {
        Cursor cursor = connect().query(
                Item.TABLE_NAME, ITEM_COLUMNS, null, null, null, null,
                Item._LATEST_ACCESS + (recentFirst ? " DESC" : " ASC"),
                null);
        this.cursors.add(cursor);

        return itemList(cursor);
    }

    private List<Item> itemList(final Cursor cursor) {
        return new CursorList<Item>(cursor) {
            @Override
            protected Item newEntity() {
                return new Item(cursor);
            }
        };
    }

    private abstract class Query<T> {
        final String tableName;
        String selection = null;
        private List<String> args = new ArrayList<>();

        Query(String tableName) {
            this.tableName = tableName;
        }

        Query<T> eq(String column, Object value) {
            if (selection == null) {
                selection = column +" = ?";
            } else {
                selection += " and "+ column +" = ?";
            }
            args.add(String.valueOf(value));

            return this;
        }

        protected Cursor list(String[] columns) {
            Cursor cursor = connect().query(
                    tableName, columns, selection,
                    args.toArray(new String[args.size()]), null, null, null);

            cursors.add(cursor);
            return cursor;
        }

        abstract List<T> list();
    }

    private class ItemQuery extends Query<Item> {
        ItemQuery() {
            super(Item.TABLE_NAME);
        }

        List<Item> list() {
            return itemList(list(ITEM_COLUMNS));
        }
    }

    public List<Item> selectItemsByServerId(long serverId) {
        return new ItemQuery()
                .eq(Item._SERVER_ID, serverId)
                .list();
    }

    private List<Folder> folders = null;
    public List<Folder> selectFolders() {
        if (folders == null) {
            Cursor cursor = connect().query(
                    Folder.TABLE_NAME, FOLDER_COLUMNS, null, null, null, null,
                    Folder._NAME + " ASC",
                    null);
            try {
                folders = new ArrayList<>(new CursorList<Folder>(cursor) {
                    @Override
                    protected Folder newEntity() {
                        return new Folder(cursor);
                    }
                });
            } finally {
                cursor.close();
            }
        }
        return folders;
    }

    private List<Server> servers = null;
    public List<Server> selectServers() {
        if (servers == null) {
            Cursor cursor = connect().query(
                    Server.TABLE_NAME, SERVER_COLUMNS, null, null, null, null,
                    Server._NAME + " ASC",
                    null);
            try {
                servers = new ArrayList<>(new CursorList<Server>(cursor) {
                    @Override
                    protected Server newEntity() {
                        return new Server(cursor);
                    }
                });
            } finally {
                cursor.close();
            }
        }
        return servers;
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

    /** Wrap a sql expression in a MAX() aggregate function. */
    protected String max(String column) {
        return "MAX("+ column +")";
    }
}