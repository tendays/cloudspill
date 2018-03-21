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
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** CloudSpill Android database.
 *
 * @author tendays
 */
public class Domain extends AbstractDomain<Domain> {

    // If you change the database schema, you must increment the database version.
    private static final int DATABASE_VERSION = 6;
    private static final String DATABASE_NAME = "CloudSpill.db";

    private static final ItemSchema itemSchema = new ItemSchema();
    public static class ItemSchema extends Schema<Domain, Item> {
        /* Columns */
        public static final Column<Long> ID = id();
        public static final Column<Long> SERVER_ID = longColumn("SERVER_ID");
        public static final Column<String> USER = string("USER");
        /** Folder name. The folder does not need to exist in the "folder" table
         * in case it's an "external" folder.
         */
        public static final Column<String> FOLDER = string("FOLDER");

        public static final Column<String> PATH = string("PATH");
        /** The time the item was last opened in the application.
         * This is used to decide when it may be deleted locally.
         */
        public static final Column<Date> LATEST_ACCESS = date("LATEST_ACCESS");
        /** The time the item was created (for a photo, that would be the time the photo was taken). */
        public static final Column<Date> DATE = date("DATE");
        /** The type of media in this item, as an {@link ItemType}. */
        public static final Column<ItemType> TYPE = enumerated(ItemType.class, ItemType.UNKNOWN, "TYPE");

        @Override
        public int since() {
            return 1;
        }

        @Override
        public String tableName() {
            return "ITEM";
        }

        @Override
        public List<? extends Column<?>> columns() {
            return list(ID, SERVER_ID, USER, FOLDER, PATH, LATEST_ACCESS, DATE, TYPE);
        }

        @Override
        public List<? extends Column<?>> idColumns() {
            return list(ID);
        }

        @Override
        protected Item newInstance(Domain domain, Cursor cursor) {
            return domain.new Item(cursor);
        }
    }

    private static List<Column<?>> list(Column<?>... columns) {
        return Collections.unmodifiableList(Arrays.asList(columns));
    }

    public class Item extends Entity {

        @Override
        Schema getSchema() {
            return itemSchema;
        }

        private TrackingList<String, Tag> tags;

        public Item() {
        }

        private Item(Cursor cursor) {
            super(cursor);
        }

        /** Construct an Item from its serialized form as constructed by the server. */
        public Item(String serialisedForm) {
            Splitter splitter = new Splitter(serialisedForm, ';');
            set(ItemSchema.SERVER_ID, splitter.getLong());
            set(ItemSchema.USER, splitter.getString());
            set(ItemSchema.FOLDER, splitter.getString());
            set(ItemSchema.PATH, splitter.getString());
            set(ItemSchema.DATE, toDate(splitter.getLong())); // TODO this is supposed to be UTC - check!
            set(ItemSchema.TYPE, ItemType.valueOfOptional(splitter.getString()));
            new Splitter(splitter.getString(), ',').allRemainingTo(getTags());
        }

        private Boolean local = null;
        public boolean isLocal() {
            if (local == null) {
                local = (getUser().equals(SettingsActivity.getUser(context)));
                //&&                       folder.equals(SettingsActivity.getFolder(context)));
            }
            return local;
        }

        public Long getId() {
            return get(ItemSchema.ID);
        }

        public Long getServerId() {
            return get(ItemSchema.SERVER_ID);
        }

        public String getUser() {
            return get(ItemSchema.USER);
        }

        public Date getDate() {
            return get(ItemSchema.DATE);
        }

        public String getFolder() {
            return get(ItemSchema.FOLDER);
        }

        public String getPath() {
            return get(ItemSchema.PATH);
        }

        public ItemType getType() {
            return get(ItemSchema.TYPE);
        }

        FileBuilder file = null;
        public FileBuilder getFile() {
            if (file != null) { return file; }
            //Log.d(TAG, "getFile: u "+ this.user +" /f "+ this.folder +" /p "+ this.path);
            if (isLocal()) {
                for (Folder folder : selectFolders()) {
                    if (folder.get(FolderSchema.NAME).equals(this.getFolder())) {
                        file = folder.getFile().append(getPath());
                        //Log.d(TAG, "Creating fileBuilder object "+ folder.getFile() +" -> "+ file);
                        return file;
                    }
                }
            }
            //Log.d(TAG, "(not local)");

            file = SettingsActivity.getDownloadPath(context).append(getUser()).append(getFolder()).append(getPath());
            return file;
        }

        public void copyFrom(Item that) {
            this.values.putAll(that.values);
        }

        public List<String> getTags() {
            if (tags == null) {
                tags = new TrackingList<String, Tag>(
                        new EntityQuery<>(tagSchema)
                .eq(TagSchema.ITEM, this.getId()).detachedList()) {
                    @Override
                    protected String extract(Tag entity) {
                        return entity.get(TagSchema.TAG);
                    }

                    @Override
                    protected Tag wrap(String tag) {
                        Tag entity = new Tag();
                        entity.set(TagSchema.ITEM, Item.this.getId());
                        entity.set(TagSchema.TAG, tag);
                        return entity;
                    }

                    @Override
                    protected void insert(Tag tag) {
                        if (tag.get(TagSchema.ITEM) != 0) {
                            tag.insert();
                        }
                    }

                    @Override
                    protected void delete(Tag tag) {
                        if (tag.get(TagSchema.ITEM) != 0) {
                            tag.delete();
                        }
                    }

                    protected void flush(Tag tag) {
                        if (tag.getItem() == 0 && Item.this.getId() != 0) {
                            tag.set(TagSchema.ITEM, Item.this.getId());
                            insert(tag);
                        }
                    }
                };
            }
            return tags;
        }

        public long insert() {
            set(ItemSchema.ID, super.insert());
            if (this.tags != null) {
                this.tags.flush();
            }
            return get(ItemSchema.ID);
        }
    }

    /** I don't know how to access the database on my android device so I put my commands here instead. */
    public int hotfix() {
        return 0;
    }

    private static final Column<?>[] TAG_COLUMNS = new Column<?>[]{
            TagSchema.ITEM,
            TagSchema.TAG};

    public static final TagSchema tagSchema = new TagSchema();
    public static class TagSchema extends Schema<Domain, Tag> {
        @Override
        public int since() {
            return 6;
        }

        @Override
        public String tableName() {
            return "TAG";
        }

        @Override
        public List<? extends Column<?>> columns() {
            return list(ITEM, TAG, DIRTY);
        }

        public static final Column<Long> ITEM = longColumn("ITEM");
        public static final Column<String> TAG = string("TAG");
        public static final Column<String> DIRTY = string("DIRTY");

        @Override
        public List<? extends Column<?>> idColumns() {
            return list(ITEM, TAG);
        }

        @Override
        protected Tag newInstance(Domain domain, Cursor cursor) {
            return domain.new Tag(cursor);
        }
    }
    public class Tag extends Entity {

        public Tag() {
        }

        private Tag(Cursor cursor) {
            super(cursor);
        }

        @Override
        Schema getSchema() {
            return tagSchema;
        }

        public Long getItem() {
            return get(TagSchema.ITEM);
        }
    }

    public static FolderSchema folderSchema = new FolderSchema();
    public static class FolderSchema extends Schema<Domain, Folder> {

        public static final Column<Long> ID = id();
        public static final Column<String> NAME = string("name");
        public static final Column<String> PATH = string("path");
        @Override
        public int since() {
            return 2;
        }

        @Override
        public String tableName() {
            return "FOLDER";
        }

        @Override
        public List<? extends Column<?>> columns() {
            return list(ID,
                    NAME,
                    PATH);
        }

        @Override
        public List<? extends Column<?>> idColumns() {
            return list(ID);
        }

        @Override
        protected Folder newInstance(Domain domain, Cursor cursor) {
            return domain.new Folder(cursor);
        }
    }

    public class Folder extends Entity {
        public Folder() {
        }

        private Folder(Cursor cursor) {
            super(cursor);
        }

        @Override
        Schema<Domain, ?> getSchema() {
            return folderSchema;
        }

        public Long getId() {
            return get(FolderSchema.ID);
        }

        public void save() {
            if (getId() == null) {
                insert();
            } else {
                update();
            }
        }

        public void delete() {
            if (getId() != null) {
                super.delete();
            }
        }

        /** @throws java.lang.IllegalArgumentException if the path is not a valid URI. */
        public FileBuilder getFile() {
            return new FileBuilder.Found(context, Uri.parse(get(FolderSchema.PATH)));
        }
    }

    public static final ServerSchema serverSchema = new ServerSchema();
    public static class ServerSchema extends Schema<Domain, Server> {
        public static final Column<Long> ID = id();
        public static final Column<String> NAME = string("name");
        public static final Column<String> URL = string("url");
        @Override
        public int since() {
            return 3;
        }

        @Override
        public String tableName() {
            return "SERVER";
        }

        @Override
        public List<? extends Column<?>> columns() {
            return list(ID, NAME, URL);
        }

        @Override
        public List<? extends Column<?>> idColumns() {
            return list(ID);
        }

        @Override
        protected Server newInstance(Domain domain, Cursor cursor) {
            return domain.new Server(cursor);
        }
    }

    public class Server extends Entity {

        public Server() {
        }

        private Server(Cursor cursor) {
            super(cursor);
        }

        @Override
        Schema<Domain, ?> getSchema() {
            return serverSchema;
        }

        public String getUrl() {
            return get(ServerSchema.URL);
        }

        public String getName() {
            return get(ServerSchema.NAME);
        }
    }

    public Domain(Context context) {
        super(context, DATABASE_NAME, DATABASE_VERSION);
    }

    public void onCreate(SQLiteDatabase db) {
        onUpgrade(db, 0, DATABASE_VERSION);
    }
    private boolean mustPopulateTypes = false;

    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.d(TAG, "Current version: " + oldVersion + ". New version: " + newVersion);

        newTable(db, oldVersion, newVersion, itemSchema);
        newTable(db, oldVersion, newVersion, folderSchema);
        newTable(db, oldVersion, newVersion, serverSchema);
        newColumn(db, oldVersion, newVersion, itemSchema, 4, ItemSchema.DATE);
        newColumn(db, oldVersion, newVersion, itemSchema, 5, ItemSchema.TYPE);

        if (oldVersion < 5 && newVersion >= 5) {
            mustPopulateTypes = true;
        }

        newTable(db, oldVersion, newVersion, new TagSchema());
    }

    protected void afterConnect() {
        if (mustPopulateTypes) {
            mustPopulateTypes = false;
            Log.d(TAG, "Upgrading database...");
            Query<Item> query = selectItems();
            for (Item i : query.list()) {
                if (i.getType() != ItemType.UNKNOWN) { continue; }
                if (i.getPath().toLowerCase().endsWith(".jpg")) {
                    i.set(ItemSchema.TYPE, ItemType.IMAGE);
                } else if (i.getPath().toLowerCase().endsWith(".mp4")) {
                    i.set(ItemSchema.TYPE, ItemType.VIDEO);
                } else {
                    throw new IllegalArgumentException("Unrecognised extension "+ i.getPath());
                }
                i.update();
            }
            query.close();
            Log.d(TAG, "Upgrading database complete.");
        }
    }

    public Query<Item> selectItems() {
        return itemSchema.query(this);
    }

    public List<Item> selectItemsByServerId(long serverId) {
        return selectItems()
                .eq(ItemSchema.SERVER_ID, serverId)
                .detachedList();
    }

    private List<Folder> folders = null;
    public List<Folder> selectFolders() {
        if (folders == null) {
            folders = folderSchema.query(this).detachedList();
        }
        return folders;
    }

    public List<Server> selectServers() {
        return serverSchema.query(this).detachedList();
    }
}