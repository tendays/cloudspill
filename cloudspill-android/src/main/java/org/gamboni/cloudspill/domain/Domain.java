package org.gamboni.cloudspill.domain;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.util.Log;

import org.gamboni.cloudspill.file.FileBuilder;
import org.gamboni.cloudspill.shared.api.Csv;
import org.gamboni.cloudspill.shared.domain.IsItem;
import org.gamboni.cloudspill.shared.domain.ItemType;
import org.gamboni.cloudspill.shared.util.Splitter;
import org.gamboni.cloudspill.ui.SettingsActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** CloudSpill Android database.
 *
 * @author tendays
 */
public class Domain extends AbstractDomain<Domain> {

    // If you change the database schema, you must increment the database version.
    private static final int DATABASE_VERSION = 9;
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

        public static final Column<String> DESCRIPTION = string("DESCRIPTION", 9);

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
            return list(ID, SERVER_ID, USER, FOLDER, PATH, LATEST_ACCESS, DATE, TYPE, CHECKSUM, DESCRIPTION);
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
        public CloseableList<Item> list(boolean debug) {
            if (!needJoin) {
                return super.list(debug);
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
        protected Cursor list(boolean debug, String... columns) {
            if (!needJoin) {
                return super.list(debug, columns);
            } else {
                throw new UnsupportedOperationException();
            }
        }
    }

    private static List<Column<?>> list(Column<?>... columns) {
        return Collections.unmodifiableList(Arrays.asList(columns));
    }

    private static Csv.Setter<Item> longSetter(final Column<Long> column) {
        return new Csv.Setter<Item>() {
            @Override
            public void set(Item item, String value) {
                item.set(column, Long.parseLong(value));
            }
        };
    }

    private static Csv.Setter<Item> stringSetter(final Column<String> column) {
        return new Csv.Setter<Item>() {
            @Override
            public void set(Item item, String value) {
                item.set(column, value);
            }
        };
    }

    private static Csv.Setter<Item> dateSetter(final Column<Date> column) {
        return new Csv.Setter<Item>() {
            @Override
            public void set(Item item, String value) {
                item.set(column, value.isEmpty() ? null : toDate(Long.parseLong(value))); // TODO this is supposed to be UTC - check!
            }
        };
    }

    private static final Csv.Setter<Item> TYPE_SETTER = new Csv.Setter<Item>() {
        @Override
        public void set(Item item, String value) {
            item.set(ItemSchema.TYPE, ItemType.valueOfOptional(value));
        }
    };

    private static final Csv.Setter<Item> TAGS_SETTER = new Csv.Setter<Item>() {
        @Override
        public void set(Item item, String value) {
            List<String> list = new ArrayList<>();
            new Splitter(value, ',').allRemainingTo(list);
            item.setTagsFromServer(list);
        }
    };

    public static final Csv<Item> ITEM_CSV = new Csv.Impl<Item>()
            .add("id", null, longSetter(ItemSchema.SERVER_ID))
            .add("user", null, stringSetter(ItemSchema.USER))
            .add("folder", null, stringSetter(ItemSchema.FOLDER))
            .add("path", null, stringSetter(ItemSchema.PATH))
            .add("date", null, dateSetter(ItemSchema.DATE))
            .add("type", null, TYPE_SETTER)
            .add("tags", null, TAGS_SETTER)
            .add("checksum", null, stringSetter(ItemSchema.CHECKSUM))
            .add("description", null, stringSetter(ItemSchema.DESCRIPTION));

    public class Item extends Entity implements IsItem {

        @Override
        ItemSchema getSchema() {
            return itemSchema;
        }

        private TrackingList<Tag, Tag> tags;

        public Item() {}

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

        @Override
        public String getChecksum() {
            return get(ItemSchema.CHECKSUM);
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

        public void copyFromServer(Item that) {
            this.values.putAll(that.values);
            final Set<String> thatTags = that.getTags();

            // 1. remove deleted tags
            Iterator<Tag> thisTags = this.getTagList().iterator();
            while (thisTags.hasNext()) {
                Tag t = thisTags.next();
                if (t.get(TagSchema.SYNC) != SyncStatus.TO_CREATE && !thatTags.contains(t.get(TagSchema.TAG))) {
                    thisTags.remove();
                }
            }

            // 2. add missing tags
            for (String tag : thatTags) {
                if (!this.getTags().contains(tag)) {
                    Tag t = new Tag();
                    t.set(TagSchema.TAG, tag);
                    t.set(TagSchema.SYNC, SyncStatus.OK);
                    this.getTagList().add(t);
                }
            }
        }

        public Set<String> getTags() {
            Set<String> tags = new TreeSet<>();
            for (Tag tag : getTagList()) {
                tags.add(tag.get(TagSchema.TAG));
            }
            return Collections.unmodifiableSet(tags);
        }

        public List<Tag> getTagList() {
            if (tags == null) {
                tags = new TrackingList<Tag, Tag>(
                        new EntityQuery<Tag>(tagSchema)
                .eq(TagSchema.ITEM, this.getId()).detachedList()) {
                    @Override
                    protected Tag extract(Tag entity) {
                        return entity;
                    }

                    @Override
                    protected Tag wrap(Tag entity) {
                        entity.set(TagSchema.ITEM, Item.this.getId());
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

        public void setTagsFromServer(List<String> tags) {
            this.getTagList().clear();
            for (String tag : tags) {
                Tag t = new Tag();
                t.set(TagSchema.TAG, tag);
                t.set(TagSchema.SYNC, SyncStatus.OK);
                getTagList().add(t);
            }
        }

        public void setTagsForSync(List<String> newTags) {

            // 1. remove deleted tags
            Iterator<Tag> thisTags = this.getTagList().iterator();
            while (thisTags.hasNext()) {
                Tag t = thisTags.next();
                if (!newTags.contains(t.get(TagSchema.TAG))) {
                    if (t.get(TagSchema.SYNC) == SyncStatus.TO_CREATE) {
                        // cancel creation before it occurs
                        thisTags.remove();
                    } else {
                        // remove from server
                        t.set(TagSchema.SYNC, SyncStatus.TO_DELETE);
                    }
                }
            }

            // 2. add missing tags
            for (String tag : newTags) {
                if (!this.getTags().contains(tag)) {
                    Tag t = new Tag();
                    t.set(TagSchema.TAG, tag);
                    t.set(TagSchema.SYNC, SyncStatus.TO_CREATE);
                    this.getTagList().add(t);
                }
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
            final Long itemId = this.get(TagSchema.ITEM);
            if (itemId == null) {
                return null;
            }
            final List<Item> items = selectItems().eq(ItemSchema.ID, itemId).detachedList();
            return items.get(0);
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
        public FileBuilder getFile() { return new FileBuilder.Found(context, Uri.parse(get(FolderSchema.PATH)));
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
        public long getId() { return get(ServerSchema.ID); }
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