package org.gamboni.cloudspill.domain;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.provider.BaseColumns;
import android.util.Log;

import org.gamboni.cloudspill.file.FileBuilder;
import org.gamboni.cloudspill.ui.SettingsActivity;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** CloudSpill Android database.
 *
 * @author tendays
 */
public class Domain extends AbstractDomain {

    private static final String TAG = "CloudSpill.Domain";

    // If you change the database schema, you must increment the database version.
    private static final int DATABASE_VERSION = 5;
    private static final String DATABASE_NAME = "CloudSpill.db";

    private static final String[] ITEM_COLUMNS = new String[]{
            Item._ID,
            Item._SERVER_ID,
            Item._USER,
            Item._FOLDER,
            Item._PATH,
            Item._LATEST_ACCESS,
            Item._DATE
    };

    public class Item implements BaseColumns {
        private static final String TABLE_NAME = "ITEM";
        /** Table exists since this version. */
        private static final int SINCE = 1;
        /* Columns */
        public static final String _SERVER_ID = "SERVER_ID";
        public static final String _USER = "USER";
        /** Folder name. The folder does not need to exist in the "folder" table
         * in case it's an "external" folder.
         */
        public static final String _FOLDER = "FOLDER";

        public static final String _PATH = "PATH";
        /** The time the item was last opened in the application.
         * This is used to decide when it may be deleted locally.
         */
        public static final String _LATEST_ACCESS = "LATEST_ACCESS";
        /** The time the item was created (for a photo, that would be the time the photo was taken). */
        public static final String _DATE = "DATE";
        /** The type of media in this item, as an {@link ItemType}. */
        public static final String _TYPE = "TYPE";

        private static final String SQL_CREATE_ENTRIES =
                "CREATE TABLE " + TABLE_NAME + " (" +
                        _ID + " INTEGER PRIMARY KEY, " +
                        _SERVER_ID + " INTEGER, " +
                        _USER + " TEXT, " +
                        _FOLDER + " TEXT, " +
                        _PATH + " TEXT, " +
                        _LATEST_ACCESS + " INTEGER," +
                        _DATE +" INTEGER," +
                        _TYPE +" TEXT" +
                        ")";

        private static final String SQL_DELETE_ENTRIES =
                "DROP TABLE "+ TABLE_NAME +" IF EXISTS";

        public long id;
        public long serverId;
        public String user;
        public String folder;
        public String path;
        public Date latestAccess;
        public Date date;
        public ItemType type;

        public Item() {}

        private Item(Cursor cursor) {
            id = cursor.getLong(0);
            serverId = cursor.getLong(1);
            user = cursor.getString(2);
            folder = cursor.getString(3);
            path = cursor.getString(4);
            latestAccess = toDate(cursor.getLong(5));
            date = toDate(cursor.getLong(6));
            type = ItemType.valueOf(cursor.getString(7));
        }

        /** Construct an Item from its serialized form as constructed by the server. */
        public Item(String serialisedForm) {
            Splitter splitter = new Splitter(serialisedForm);
            serverId = splitter.getLong();
            user = splitter.getString();
            folder = splitter.getString();
            path = splitter.getString();
            date = toDate(splitter.getLong()); // TODO this is supposed to be UTC - check!
            type = ItemType.valueOf(splitter.getString());
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
            //Log.d(TAG, "getFile: u "+ this.user +" /f "+ this.folder +" /p "+ this.path);
            if (isLocal()) {
                for (Folder folder : selectFolders()) {
                    if (folder.name.equals(this.folder)) {
                        file = folder.getFile().append(path);
                        //Log.d(TAG, "Creating fileBuilder object "+ folder.getFile() +" -> "+ file);
                        return file;
                    }
                }
            }
            //Log.d(TAG, "(not local)");

            file = SettingsActivity.getDownloadPath(context).append(user).append(folder).append(path);
            return file;
        }

        private ContentValues getValues() {
            ContentValues result = new ContentValues();
            result.put(_SERVER_ID, serverId);
            result.put(_USER, user);
            result.put(_FOLDER, folder);
            result.put(_PATH, path);
            result.put(_LATEST_ACCESS, fromDate(latestAccess));
            result.put(_DATE, fromDate(date));
            return result;
        }

        public void copyFrom(Item that) {
            this.date = that.date;
            this.serverId = that.serverId;
        }

        public long insert() {
            return connect().insert(TABLE_NAME, null, getValues());
        }

        public void update() {
            new ItemQuery().eq(_ID, this.id).update(getValues());
        }
    }

    /** I don't know how to access the database on my android device so I put my commands here instead. */
    public int hotfix() {
        return 0;//new ItemQuery().eq(Item._SERVER_ID, 12179).delete();
    }

    private static final String[] FOLDER_COLUMNS = {
            Folder._ID,
            Folder._NAME,
            Folder._PATH
    };
    public class Folder implements BaseColumns {
        public static final String TABLE_NAME="FOLDER";
        public Long id;
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
            if (id != null) {
                result.put(_ID, id);
            }
            result.put(_NAME, name);
            result.put(_PATH, path);
            return result;
        }
        public long insert() {
            return connect().insert(TABLE_NAME, null, getValues());
        }

        public void save() {
            if (id == null) {
                insert();
            } else {
                connect().update(TABLE_NAME, getValues(), _ID +" = ?", new String[]{String.valueOf(id)});
            }
        }

        public void delete() {
            if (id != null) {
                connect().delete(TABLE_NAME, _ID +" = ?", new String[]{String.valueOf(id)});
            }
        }

        /** @throws java.lang.IllegalArgumentException if the path is not a valid URI. */
        public FileBuilder getFile() {
            return new FileBuilder.Found(context, Uri.parse(path));
        }
    }


    private static final String[] SERVER_COLUMNS = {
            Server._ID,
            Server._NAME,
            Server._URL
    };
    public class Server implements BaseColumns {
        public static final String TABLE_NAME="SERVER";
        public Long id;
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
            if (id != null) {
                result.put(_ID, id);
            }
            result.put(_NAME, name);
            result.put(_URL, url);
            return result;
        }
        public long insert() {
            return connect().insert(TABLE_NAME, null, getValues());
        }
    }

    public Domain(Context context) {
        super(context, DATABASE_NAME, DATABASE_VERSION);
    }

    public void onCreate(SQLiteDatabase db) {
        onUpgrade(db, 0, DATABASE_VERSION);
    }

    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.d(TAG, "Current version: "+ oldVersion +". New version: "+ newVersion);
        newTable(db, oldVersion, newVersion, Item.SINCE, Item.SQL_CREATE_ENTRIES, Item.SQL_DELETE_ENTRIES);
        newTable(db, oldVersion, newVersion, 2, Folder.SQL_CREATE_ENTRIES, Folder.SQL_DELETE_ENTRIES);
        newTable(db, oldVersion, newVersion, 3, Server.SQL_CREATE_ENTRIES, Server.SQL_DELETE_ENTRIES);
        newColumn(db, oldVersion, newVersion, Item.SINCE, 4, Item.TABLE_NAME, Item._DATE, "INTEGER");
        newColumn(db, oldVersion, newVersion, Item.SINCE, 5, Item.TABLE_NAME, Item._TYPE, "TEXT");

        if (oldVersion < 5 && newVersion >= 5) {
            // populate type column
            Query<Item> query = selectItems();
            for (Item i : query.list()) {
                if (i.path.endsWith(".jpg")) {
                    i.type = ItemType.IMAGE;
                } else if (i.path.endsWith(".mp4")) {
                    i.type = ItemType.VIDEO;
                } else {
                    throw new IllegalArgumentException("Unrecognised extension "+ i.path);
                }
                i.update();
            }
            query.close();
        }
    }

    public Query<Item> selectItems() {
        return new ItemQuery();
    }

    private List<Item> itemList(final Cursor cursor) {
        return new CursorList<Item>(cursor) {
            @Override
            protected Item newEntity() {
                return new Item(cursor);
            }
        };
    }

    private class ItemQuery extends Query<Item> {
        ItemQuery() {
            super(Item.TABLE_NAME);
        }

        public List<Item> list() {
            return itemList(list(ITEM_COLUMNS));
        }
    }

    public List<Item> selectItemsByServerId(long serverId) {
        return new ItemQuery()
                .eq(Item._SERVER_ID, serverId)
                .detachedList();
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

    public List<Server> selectServers() {
        Cursor cursor = connect().query(
                Server.TABLE_NAME, SERVER_COLUMNS, null, null, null, null,
                Server._NAME + " ASC",
                null);
        this.cursors.add(cursor);
        return new CursorList<Server>(cursor) {
                    @Override
                    protected Server newEntity() {
                        return new Server(cursor);
                    }
                };
    }
}