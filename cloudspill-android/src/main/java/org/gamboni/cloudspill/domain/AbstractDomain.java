package org.gamboni.cloudspill.domain;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;
import android.util.Log;

import org.gamboni.cloudspill.collect.ConcatList;

import java.lang.ref.WeakReference;
import java.util.AbstractList;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Base class for database data model.
 *
 * @author tendays
 */
public abstract class AbstractDomain<SELF extends AbstractDomain<SELF>> extends SQLiteOpenHelper {

    protected static final String TAG = "CloudSpill.Domain";

    protected final Context context;

    /** java.util.function.Consumer replacement, the real one is not available at my API level. */
    public interface Consumer<T> {
        void accept(T object);
    }

    public static abstract class Schema<D extends AbstractDomain<D>, E extends AbstractDomain<D>.Entity> {
        public abstract int since();
        public abstract String tableName();
        public abstract List<? extends Column<?>> columns();
        public abstract List<? extends Column<?>> idColumns();
        protected abstract E newInstance(D domain, Cursor cursor);
        public AbstractDomain<D>.Query<E> query(AbstractDomain<D> domain) {
            return domain.new EntityQuery<E>(this);
        }

        private List<WeakReference<Consumer<E>>> watchers = new ArrayList<>();

        private String[] columnNames = null;
        public synchronized String[] columnNames() {
            if (columnNames == null) {
                columnNames = new String[columns().size()];
                int index = 0;
                for (Column<?> column : columns()) {
                    columnNames[index++] = column.name;
                }
            }
            return columnNames;
        }

        public void watch(Consumer<E> watcher) {
            watchers.add(new WeakReference<Consumer<E>>(watcher));
        }

        public void unwatch(Consumer<E> watcher) {
            Iterator<WeakReference<Consumer<E>>> iterator = watchers.iterator();
            while (iterator.hasNext()) {
                Consumer<E> consumer = iterator.next().get();
                if (consumer == null || consumer == watcher) {
                    iterator.remove();
                }
            }
        }

        public void notifyNewItem(Object item) {
            Iterator<WeakReference<Consumer<E>>> iterator = watchers.iterator();
            while (iterator.hasNext()) {
                Consumer<E> consumer = iterator.next().get();
                if (consumer == null) {
                    iterator.remove();
                } else {
                    consumer.accept((E)item);
                }
            }
        }
    }

    protected SELF domain() {
        return (SELF)this;
    }

    public class EntityQuery<T extends Entity> extends Query<T> {
        protected final Schema<SELF, T> schema;
        public EntityQuery(Schema<SELF, T> schema) {
            super(schema);
            this.schema = schema;
        }

        public CloseableList<T> list(boolean debug) {
            return new CursorList<T>(schema.tableName() +" cursor", listAllColumns(debug)) {
                @Override
                protected T newEntity() {
                    return schema.newInstance(domain(), cursor);
                }

                @Override
                public int indexOf(Object object) {
                    T element = (T) object;
                    // TODO add restrictions: select count(*) from ... where ... AND orderColumn < :element.orderColumn
                    StringBuilder sqlQuery = new StringBuilder("select count(*) from "+ schema.tableName() +" where (");
                    List<String> countArgs = new ArrayList<>();
                    String outerKeyword = "";
                    for (int i = 0; i < orderingPlusId.size(); i++) {
                        sqlQuery.append(outerKeyword).append('(');
                        String innerKeyword = "";
                        for (int j = 0; j<i; j++) {
                            Order r = orderingPlusId.get(j);

                            final Column<?> column = r.getColumn();
                            sqlQuery.append(innerKeyword).append(copyValue(countArgs, column, element, " = ?"));
                            innerKeyword = " and ";
                        }
                        final Order orderI = orderingPlusId.get(i);
                        sqlQuery.append(innerKeyword).append(
                                copyValue(countArgs, orderI.getColumn(), element,
                                (orderI.isAsc() ? " < ? " : " > ? ")
                        ));

                        sqlQuery.append(')');
                        outerKeyword = " or ";
                    }
                    sqlQuery.append(')');

                    countArgs.addAll(EntityQuery.this.args);

                    final ColumnRenderer renderer = getColumnRenderer();
                    final String sql = renderQueryFragment(" and ", " and ", renderer, sqlQuery, restrictions).toString();
                    Log.d(TAG, "indexOf query: "+ sql);
                    try (Cursor cursor = connect().rawQuery(sql,
                            countArgs.toArray(new String[countArgs.size()]))) {
                        cursor.moveToFirst();
                        /* If the item is in the list, it would be at this position. */
                        int candidateIndex = cursor.getInt(0);

                        /* Now check if we've actually found it. */

                        // candidateIndex can't be negative (it's a count), but it could be equal to the size
                        // if the passed element comes after all other elements, so we exclude that case first.
                        if (candidateIndex == size()) {
                            Log.d(TAG, "indexOf: candidateIndex "+ candidateIndex +" is equal to size");
                            return -1;
                        }
                        T candidate = get(candidateIndex);

                        for (Column<?> idColumn : schema.idColumns()) {
                            Object candidateId = candidate.get(idColumn);
                            Object providedId = element.get(idColumn);
                            if (!candidateId.equals(providedId)) {
                                Log.d(TAG, "indexOf: candidate element at "+ candidateIndex +" has "+ idColumn.name +" = "+
                                candidateId +" ≠ supplied "+ providedId);
                                return -1;
                            }
                        }

                        // all id columns match
                        return candidateIndex;
                    }
                }
            };
        }
    }

    /** Copy the String sql value of the given column in the given entity, into the given target list. */
    private <T> String copyValue(List<String> target, Column<T> column, Entity entity, String operator) {
        target.add(String.valueOf(column.getSql((entity).get(column))));
        return column.name + operator;
    }

    protected static <T extends AbstractDomain<?>.Entity> T load(T entity, Cursor c) {
        entity.load(c);
        return entity;
    }

    public abstract class Entity {
        protected final Map<Column<?>, Object> values = new HashMap<>();
        protected Entity() {
        }

        public <T> T get(Column<T> column) {
            return (T)values.get(column);
        }

        public <T> void set(Column<T> column, T value) {
            values.put(column, value);
        }

        /** Initialise this entity from the given cursor. */
        protected void load(Cursor cursor) {
            int counter=0;
            for (Column<?> column : getSchema().columns()) {
                values.put(column, column.getFrom(cursor, counter++));
            }
        }

        abstract Schema<SELF, ?> getSchema();

        public ContentValues getValues() {
            ContentValues result = new ContentValues();
            for (Column<?> col : getSchema().columns()) {
                getSql(result, this, col);
            }
            return result;
        }
        public long insert() {
            final long newItem = connect().insert(getSchema().tableName(), null, getValues());
            getSchema().notifyNewItem(newItem);
            return newItem;
        }

        protected <T> void likeThis(Query<?> query, Column<T> column) {
            query.eq(column, this.get(column));
        }

        public void update() {
            Query<?> query = getSchema().query(domain());
            for (Column<?> idColumn : getSchema().idColumns()) {
                likeThis(query, idColumn);
            }
            query.update(getValues());
        }

        public void delete() {
            Query<?> query = getSchema().query(domain());
            for (Column<?> idColumn : getSchema().idColumns()) {
                likeThis(query, idColumn);
            }
            query.delete();
        }
    }

    private static <T> void getSql(ContentValues output, AbstractDomain<?>.Entity e, Column<T> column) {
        column.addTo(output, e.get(column));
    }

    public static abstract class Column<T> {
        public final String name;
        private final int since;
        protected Column(String name, int since) {
            this.name = name;
            this.since = since;
        }
        public abstract String sqlType();
        public abstract Object getSql(T value);
        public abstract T getFrom(Cursor cursor, int index);
        public abstract void addTo(ContentValues out, T in);
    }

    protected static class IntColumn extends Column<Integer> {
        private IntColumn(String name, int since) {
            super(name, since);
        }

        @Override
        public String sqlType() {
            return "INTEGER";
        }

        @Override
        public Integer getSql(Integer value) {
            return value;
        }

        @Override
        public Integer getFrom(Cursor cursor, int index) {
            return cursor.getInt(index);
        }

        @Override
        public void addTo(ContentValues out, Integer value) {
            out.put(name, value);
        }
    }

    protected static class LongColumn extends Column<Long> {
        private LongColumn(String name, int since) {
            super(name, since);
        }

        @Override
        public String sqlType() {
            return "INTEGER";
        }

        @Override
        public Long getSql(Long value) {
            return value;
        }

        @Override
        public Long getFrom(Cursor cursor, int index) {
            return cursor.getLong(index);
        }

        public void addTo(ContentValues out, Long value) {
            out.put(name, value);
        }
    }

    protected static class IdColumn extends LongColumn {
        private IdColumn(int since) {
            super(BaseColumns._ID, since);
        }
        public String sqlType() {
            return super.sqlType() +" PRIMARY KEY";
        }
    }

    protected static class StringColumn extends Column<String> {
        private StringColumn(String name, int since) {
            super(name, since);
        }
        public String sqlType() {
            return "TEXT";
        }

        @Override
        public String getSql(String value) {
            return value;
        }
        public String getFrom(Cursor cursor, int index) {
            return cursor.getString(index);
        }

        @Override
        public void addTo(ContentValues out, String value) {
            out.put(name, value);
        }
    }

    protected static class EnumColumn<T extends Enum<T>> extends Column<T> {
        private final Class<T> type;
        private final T unknownValue;
        private EnumColumn(Class<T> type, T unknownValue, String name, int since) {
            super(name, since);
            this.type = type;
            this.unknownValue = unknownValue;
        }
        public String sqlType() {
            return "TEXT";
        }
        public T getFrom(Cursor cursor, int index) {
            String name = cursor.getString(index);
            if (name == null) {
                Log.w(TAG, "Replacing null value by placeholder "+ unknownValue.getClass().getSimpleName() +"."+ unknownValue);
                return unknownValue;
            } else try {
                return Enum.valueOf(type, name);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Replacing unknown value "+ name +" by placeholder "+ unknownValue.getClass().getSimpleName() +"."+ unknownValue);
                return unknownValue;
            }
        }

        @Override
        public String getSql(T value) {
            return (value == null) ? null : value.name();
        }

        @Override
        public void addTo(ContentValues out, T value) {
            out.put(name, getSql(value));
        }
    }

    protected static class DateColumn extends Column<Date> {
        private DateColumn(String name, int since) {
            super(name, since);
        }
        public String sqlType() {
            return "INTEGER";
        }
        public Date getFrom(Cursor cursor, int index) {
            return toDate(cursor.getLong(index));
        }

        @Override
        public Long getSql(Date value) {
            return fromDate(value);
        }

            @Override
        public void addTo(ContentValues out, Date value) {
            out.put(name, getSql(value));
        }
    }

    protected static Column<Long> id(int since) {
        return new IdColumn(since);
    }

    protected static Column<Long> longColumn(String name, int since) {
        return new LongColumn(name, since);
    }

    protected static Column<String> string(String name, int since) {
        return new StringColumn(name, since);
    }

    protected static Column<Date> date(String name, int since) {
        return new DateColumn(name, since);
    }

    protected static <T extends Enum<T>> Column<T> enumerated(Class<T> type, T unknownValue, String name, int since) {
        return new EnumColumn<T>(type, unknownValue, name, since);
    }

    protected AbstractDomain(Context context, String dbName, int dbVersion) {
        super(context, dbName, null, dbVersion);
        this.context = context;
    }

    protected static void newTable(SQLiteDatabase db, int oldVersion, int newVersion, Schema<?, ?> schema) {
        int since = schema.since();

        StringBuilder create = new StringBuilder("CREATE TABLE "+ schema.tableName() +" (");
        String comma = "";
        for (Column<?> column : schema.columns()) {
            create.append(comma).append(column.name +" "+ column.sqlType());
            comma = ", ";
        }
        create.append(")");
        String delete = "DROP TABLE "+ schema.tableName() +" IF EXISTS";
        if (oldVersion < since && newVersion >= since) {
            db.execSQL(create.toString());
        } else if (oldVersion >= since && newVersion < since) {
            db.execSQL(delete);
        } else if (oldVersion >= since && newVersion >= since) {
            /* Table already exists but columns may have to be created. */
            for (Column<?> column : schema.columns()) {
                // If table did not exist in oldVersion then newTable created it with all required columns
                // so we don't need to add the column here
                if (oldVersion < column.since && newVersion >= column.since) {
                    db.execSQL("ALTER TABLE "+ schema.tableName() +" ADD COLUMN "+ column.name +" "+ column.sqlType());
                } // TODO remove column on downgrade
            }
        }
    }

    private class ColumnQuery<U> extends Query<U> {
        final Column<U> selection;

        ColumnQuery(Query<?> base, Column<U> column) {
            super(base.schema);
            this.restrictions.addAll(base.restrictions);
            this.ordering.addAll(base.ordering);
            this.args.addAll(base.args);

            this.selection = column;
        }

        @Override
        public CloseableList<U> list(boolean debug) {
            return new CursorList<U>(list(debug, selection.name)) {
                @Override
                protected U newEntity() {
                    return selection.getFrom(cursor, 0);
                }
            };
        }
    }

    public interface ColumnRenderer {
        void render(Column<?> column, StringBuilder target);
    }

    public interface Restriction {
        void append(ColumnRenderer renderer, StringBuilder queryString);
    }

    public class PostfixRestriction implements Restriction {
        protected final Column<?> column;
        private final String postfix;
        public PostfixRestriction(Column<?> column, String postfix) {
            this.column = column;
            this.postfix = postfix;
        }

        public void append(ColumnRenderer renderer, StringBuilder queryString) {
            renderer.render(column, queryString);
            queryString.append(postfix);
        }
    }

    public class Order extends PostfixRestriction {
        private final boolean asc;
        public Order(Column<?> column, boolean asc) {
            super(column, (asc ? "" : " DESC"));
            this.asc = asc;
        }

        public Column<?> getColumn() {
            return column;
        }

        public boolean isAsc() {
            return asc;
        }
    }

    public abstract class Query<T> {
        final Schema<SELF, ?> schema;
        final List<Restriction> restrictions = new ArrayList<>();
        final List<Order> ordering = new ArrayList<>();
        /** Actual ordering criterion to use. It is equal to ordering, followed by
         * id columns, to ensure deterministic ordering.
         */
        final List<Order> orderingPlusId;
        Cursor cursor = null;

        protected final List<String> args = new ArrayList<>();

        Query(Schema<SELF, ?> schema) {
            this.schema = schema;
            List<Order> defaultOrder = new ArrayList<>();
            for (Column<?> idColumn : schema.idColumns()) {
                defaultOrder.add(new Order(idColumn, true));
            }
            orderingPlusId = new ConcatList<>(ordering, defaultOrder);
        }

        /** Add an equality condition. {@code value} may be null, in which case an "is null" restriction
         * is generated.
         */
        public <V> Query<T> eq(Column<V> column, V value) {
            return (value == null) ? restriction(column, " is null") : restriction(column, " = ?",
                    column.getSql(value));
        }

        /** Add an inequality condition. {@code value} may be null, in which case an "is not null" restriction
         * is generated.
         */
        public <V> Query<T> ne(Column<V> column, V value) {
            return (value == null) ? restriction(column, " is not null") : restriction(column, " != ?",
                    column.getSql(value));
        }

        /** Add a "greater-than-or-equal" restriction. */
        public <V extends Comparable<V>> Query<T> ge(Column<V> column, V value) {
            return restriction(column, " >= ?", column.getSql(value));
        }

        /** Add a "less-than-or-equl" restriction. */
        public <V extends Comparable<V>> Query<T> le(Column<V> column, V value) {
            return restriction(column, " <= ?", column.getSql(value));
        }

        public Query<T> like(Column<String> column, String pattern) {
            return restriction(column, " like ?", pattern);
        }

        protected Query<T> restriction(Column<?> column, String postfix, Object arg) {
            restriction(column, postfix);
            args.add(String.valueOf(arg));

            return this;
        }

        protected Query<T> restriction(Column<?> column, String postfix) {
            restrictions.add(new PostfixRestriction(column, postfix));
            return this;
        }

        public <U> Query<U> selectDistinct(Column<U> column) {
            return new ColumnQuery<>(this, column);
        }

        public Query<T> orderAsc(Column<?> column) {
            ordering.add(new Order(column, true));
            return this;
        }

        public Query<T> orderDesc(Column<?> column) {
            ordering.add(new Order(column, false));
            return this;
        }

        protected StringBuilder renderQueryFragment(final String keyword, final String separator, ColumnRenderer renderer,
                                                    StringBuilder queryString, List<? extends Restriction> list) {
            String nextSeparator = keyword;
            for (Restriction restriction : list) {
                queryString.append(nextSeparator);
                restriction.append(renderer, queryString);
                nextSeparator = separator;
            }
            return queryString;
        }

        protected StringBuilder renderRestrictions(String keyword, ColumnRenderer renderer, StringBuilder queryString) {
            return renderQueryFragment(keyword, " and ", renderer, queryString, restrictions);
        }

        protected StringBuilder renderOrdering(String keyword, ColumnRenderer renderer, StringBuilder queryString) {
            return renderQueryFragment(keyword, ", ", renderer, queryString, orderingPlusId);
        }



        protected Cursor list(boolean debug, String... columns) {
            final ColumnRenderer renderer = getColumnRenderer();
            final String whereClause = renderRestrictions("", renderer, new StringBuilder()).toString();

            if (debug) {
                Log.d(TAG, "Running "+ schema.tableName() +" query where "+ whereClause);
                Log.d(TAG, "Parameters: "+ Arrays.toString(restrictionArgs()));
            }

            cursor = connect().query(
                    schema.tableName(), columns, whereClause,
                    restrictionArgs(), null, null, renderOrdering("", renderer, new StringBuilder()).toString());

            cursors.add(cursor);
            return cursor;
        }

        @NonNull
        protected ColumnRenderer getColumnRenderer() {
            return new ColumnRenderer() {
                @Override
                public void render(Column<?> column, StringBuilder target) {
                    target.append(column.name);
                }
            };
        }

        protected Cursor listAllColumns(boolean debug) {
            return list(debug, schema.columnNames());
        }

        /** Return a List of results that dynamically read through the Cursor.
         * You must close() this object when you are done accessing the list.
         */
        public final CloseableList<T> list() { return list(false); }

        public abstract CloseableList<T> list(boolean debug);

        /** Return a List of result that is entirely copied in memory. You
         * must not close() this object when you are done.
         */
        public List<T> detachedList() {
            List<T> result = new ArrayList<>(list());
            close();
            return result;
        }

        public int update(ContentValues values) {
            return connect().update(schema.tableName(), values,
                    renderRestrictions("", getColumnRenderer(), new StringBuilder()).toString(), restrictionArgs());
        }

        /** Delete all rows corresponding to this query.
         *
         * @return the number of affected rows.
         */
        public int delete() {
            return connect().delete(schema.tableName(),
                    renderRestrictions("", getColumnRenderer(), new StringBuilder()).toString(),
                    restrictionArgs());
        }

        protected String[] restrictionArgs() {
            return args.toArray(new String[args.size()]);
        }

        public void close() {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public interface CloseableList<T> extends List<T>, AutoCloseable {
        /** Forbid checked exceptions on closeable lists. */
        void close();
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
}
