package org.gamboni.cloudspill.shared.query;

/**
 * @author tendays
 */
public class QueryRange {
    /** A QueryRange with no offset and no limit. */
    public static final QueryRange ALL = new QueryRange(0, null);

    /** Make a QueryRange with a limit but no offset. */
    public static QueryRange limit(int limit) {
        return new QueryRange(0, limit);
    }

    /** Make a QueryRange with an offset but no limit. */
    public static QueryRange offset(int offset) {
        return new QueryRange(offset, null);
    }

    public final int offset;
    public final Integer limit;

    public QueryRange(int offset, Integer limit) {
        this.offset = offset;
        this.limit = limit;
    }

    public QueryRange shift(int amount) {
        return new QueryRange(offset + amount, limit);
    }

    public QueryRange truncate() {
        return (offset >= 0) ? this :
        new QueryRange(0,
                (limit == null) ? null : limit + offset);
    }
}
