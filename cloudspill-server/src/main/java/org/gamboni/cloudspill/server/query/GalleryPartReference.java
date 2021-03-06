package org.gamboni.cloudspill.server.query;

import org.gamboni.cloudspill.domain.BackendItem;
import org.gamboni.cloudspill.shared.api.CloudSpillApi;
import org.gamboni.cloudspill.shared.query.QueryRange;

import java.time.LocalDate;
import java.util.Set;

/**
 * @author tendays
 */
public class GalleryPartReference implements Java8SearchCriteria<BackendItem> {
    public final long id;
    private final Long relativeTo;
    private final QueryRange range;

    public GalleryPartReference(long id) {
        this(id, null, QueryRange.ALL);
    }

    private GalleryPartReference(long id, Long relativeTo, QueryRange range) {
        this.id = id;
        this.relativeTo = relativeTo;
        this.range = range;
    }

    @Override
    public String getUrl(CloudSpillApi api) {
        return api.galleryPart(id, relativeTo, range);
    }

    @Override
    public LocalDate getFrom() {
        // TODO should use GalleryRequest instead, then there's no need to define these methods
        throw new IllegalStateException();
    }

    @Override
    public LocalDate getTo() {
        throw new IllegalStateException();
    }

    @Override
    public GalleryPartReference relativeTo(Long itemId) {
        return new GalleryPartReference(id, itemId, range);
    }

    @Override
    public Java8SearchCriteria<BackendItem> withRange(QueryRange newRange) {
        return new GalleryPartReference(id, relativeTo, newRange);
    }

    @Override
    public Set<String> getTags() {
        throw new IllegalStateException();
    }

    @Override
    public String getUser() {
        throw new IllegalStateException();
    }

    @Override
    public Long getRelativeTo() { return relativeTo; }

    @Override
    public QueryRange getRange() {
        return range;
    }
}
