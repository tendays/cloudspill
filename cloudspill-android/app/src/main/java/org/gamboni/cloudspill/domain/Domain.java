package org.gamboni.cloudspill.domain;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.util.Log;

import org.gamboni.cloudspill.file.FileBuilder;
import org.gamboni.cloudspill.ui.SettingsActivity;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/** CloudSpill Android database.
 *
 * @author tendays
 */
public class Domain extends AbstractDomain<Domain> {

    // If you change the database schema, you must increment the database version.
    private static final int DATABASE_VERSION = 8;
    private static final String DATABASE_NAME = "CloudSpill.db";

    private static final ItemSchema itemSchema = new ItemSchema();
    public static class ItemSchema extends Schema<Domain, Item> {
        /* Columns */
        public static final Column<Long> ID = id(1);
        public static final Column<Long> SERVER_ID = longColumn("SERVER_ID", 1);
        public static final Column<String> USER = string("USER", 1);
        /** Folder name. The folder does not need to exist in the "folder" table
         * in case it's an "external" folder.
         */
        public static final Column<String> FOLDER = string("FOLDER", 1);

        public static final Column<String> PATH = string("PATH", 1);
        /** The time the item was last opened in the application.
         * This is used to decide when it may be deleted locally.
         */
        public static final Column<Date> LATEST_ACCESS = date("LATEST_ACCESS", 1);
        /** The time the item was created (for a photo, that would be the time the photo was taken). */
        public static final Column<Date> DATE = date("DATE", 4);
        /** The type of media in this item, as an {@link ItemType}. */
        public static final Column<ItemType> TYPE = enumerated(ItemType.class, ItemType.UNKNOWN, "TYPE", 5);

        public static final Column<String> CHECKSUM = string("CHECKSUM", 8);

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
            return list(ID, SERVER_ID, USER, FOLDER, PATH, LATEST_ACCESS, DATE, TYPE, CHECKSUM);
        }

        @Override
        public List<? extends Column<?>> idColumns() {
            return list(ID);
        }

        @Override
        protected Item newInstance(Domain domain, Cursor cursor) {
            return load(domain.new Item(), cursor);
        }

        public ItemQuery query(Domain domain) {
            return domain.new ItemQuery();
        }
    }

    public class ItemQuery extends EntityQuery<Item> {
        boolean needJoin = false;

        ItemQuery() {
            super(itemSchema);
        }

        /** Only select items having a tag in the given set. Special case:
         * if the set is empty, this call has no effect. If this method is called more than once
         */
        public ItemQuery hasTags(final Set<String> tags) {
            if (!tags.isEmpty()) {
                restrictions.add(new Restriction() {
                    @Override
                    public void append(ColumnRenderer renderer, StringBuilder queryString) {
                        queryString.append("tag.").append(TagSchema.TAG.name).append(" in (");
                        String comma = "";
                        for (String tag : tags) {
                            queryString.append(comma).append("?");
                            comma = ", ";
                            args.add(tag);
                        }
                        queryString.append(")");
                    }
                });
                needJoin = true;
            }
            return this;
        }

        @Override
        public CloseableList<Item> list() {
            if (!needJoin) {
                return super.list();
            } else {
                // TODO don't use listAllColumns - create sql query joining item and item_tags
                StringBuilder queryString = new StringBuilder();
                queryString.append("select ");
                String comma = "";
                int index=0;
                int groupingColumnIndex = -1;
                for (Column<?> column : itemSchema.columns()) {
                    queryString.append(comma).append("item.").append(column.name);
                    comma = ", ";
                    if (column == ItemSchema.ID) {
                        groupingColumnIndex = index;
                    }
                    index++;
                }
                final ColumnRenderer itemColumnRenderer = new ColumnRenderer() {
                    @Override
                    public void render(Column<?> column, StringBuilder target) {
                        target.append("item.").append(column.name);
                    }
                };
                queryString.append(" from ").append(itemSchema.tableName()).append(" item, ");
                queryString.append(tagSchema.tableName()).append(" tag where item.").append(ItemSchema.ID.name)
                .append(" = tag.").append(TagSchema.ITEM.name);

                renderRestrictions(" and ", itemColumnRenderer, queryString);
                renderOrdering(" order by ", itemColumnRenderer, queryString);

                return new GroupingCursorList<Item>(
                        connect().rawQuery(queryString.toString(), restrictionArgs()),
                        ItemSchema.ID,
                        groupingColumnIndex) {
                    @Override
                    protected Item newEntity() {
                        return schema.newInstance(domain(), cursor);
                    }
                };
            }
        }

        @Override
        protected Cursor list(String... columns) {
            if (!needJoin) {
                return super.list(columns);
            } else {
                throw new UnsupportedOperationException();
            }
        }
    }

    private static List<Column<?>> list(Column<?>... columns) {
        return Collections.unmodifiableList(Arrays.asList(columns));
    }

    public class Item extends Entity {

        @Override
        ItemSchema getSchema() {
            return itemSchema;
        }

        private TrackingList<String, Tag> tags;

        public Item() {}

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
            // 1. remove deleted tags
            // TODO dirty tags should be kept as is
            this.getTags().retainAll(that.getTags());
            // 2. add missing tags
            for (String tag : that.getTags()) {
                if (!this.getTags().contains(tag)) {
                    this.getTags().add(tag);
                }
            }
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
                        if (tag.get(TagSchema.ITEM) != null) {
                            tag.insert();
                        }
                    }

                    @Override
                    protected void delete(Tag tag) {
                        if (tag.get(TagSchema.ITEM) != null) {
                            tag.delete();
                        }
                    }

                    protected void flush(Tag tag) {
                        if (tag.getItem() == null && Item.this.getId() != null) {
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

        @Override
        public void update() {
            super.update();
            if (this.tags != null) {
                this.tags.flush();
            }
        }
    }

    /** I don't know how to access the database on my android device so I put my commands here instead. */
    public int hotfix() {
        return 0;
    }

    public enum SyncStatus {
        /** Created locally, must be created in the server. */
        TO_CREATE,
        /** Deleted locally, to be deleted in the server. */
        TO_DELETE,
        /** No action needed. */
        OK
    }
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
            return list(ITEM, TAG, SYNC);
        }

        public static final Column<Long> ITEM = longColumn("ITEM", 6);
        public static final Column<String> TAG = string("TAG", 6);
        public static final Column<SyncStatus> SYNC = enumerated(SyncStatus.class, SyncStatus.TO_CREATE, "SYNC", 7);

        @Override
        public List<? extends Column<?>> idColumns() {
            return list(ITEM, TAG);
        }

        @Override
        protected Tag newInstance(Domain domain, Cursor cursor) {
            return load(domain.new Tag(), cursor);
        }
    }
    public class Tag extends Entity {

        @Override
        TagSchema getSchema() {
            return tagSchema;
        }

        public Item getItem() {
            return selectItems().eq(ItemSchema.ID, this.get(TagSchema.ITEM)).detachedList().get(0);
        }
    }

    public static final FolderSchema folderSchema = new FolderSchema();
    public static class FolderSchema extends Schema<Domain, Folder> {

        public static final Column<Long> ID = id(2);
        public static final Column<String> NAME = string("name", 2);
        public static final Column<String> PATH = string("path", 2);
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
            return load(domain.new Folder(), cursor);
        }
    }

    public class Folder extends Entity {
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

    private static final ServerSchema serverSchema = new ServerSchema();
    public static class ServerSchema extends Schema<Domain, Server> {
        public static final Column<Long> ID = id(3);
        public static final Column<String> NAME = string("name", 3);
        public static final Column<String> URL = string("url", 3);
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
            return load(domain.new Server(), cursor);
        }
    }

    public class Server extends Entity {
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
        newTable(db, oldVersion, newVersion, tagSchema);

        if (oldVersion < 5 && newVersion >= 5) {
            mustPopulateTypes = true;
        }
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

    public ItemQuery selectItems() {
        return itemSchema.query(this);
    }

    public Query<Tag> selectTags() {
        return tagSchema.query(this);
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