package org.gamboni.cloudspill.server.query;

import org.gamboni.cloudspill.domain.BackendItem;
import org.gamboni.cloudspill.shared.api.CloudSpillApi;
import org.gamboni.cloudspill.shared.api.ItemCredentials;
import org.gamboni.cloudspill.shared.query.QueryRange;
import org.gamboni.cloudspill.shared.util.UrlStringBuilder;

import java.time.LocalDate;
import java.util.Set;

/**
 * @author tendays
 */
public class GalleryPartReference implements Java8SearchCriteria<BackendItem> {
    public final long id;
    public final String key;
    private final Long relativeTo;
    private final ItemCredentials itemCredentials;
    private final QueryRange range;

    public GalleryPartReference(long id, String key) {
        this(id, key, null, null, QueryRange.ALL);
    }

    private GalleryPartReference(long id, String key, Long relativeTo, ItemCredentials itemCredentials, QueryRange range) {
        this.id = id;
        this.key = key;
        this.relativeTo = relativeTo;
        this.itemCredentials = itemCredentials;
        this.range = range;
    }

    @Override
    public UrlStringBuilder getUrl(CloudSpillApi api) {
        return api.galleryPart(id, key, relativeTo, itemCredentials, range);
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
    public GalleryPartReference relativeTo(Long itemId, ItemCredentials itemCredentials) {
        return new GalleryPartReference(id, key, itemId, itemCredentials, range);
    }

    @Override
    public Java8SearchCriteria<BackendItem> withRange(QueryRange newRange) {
        return new GalleryPartReference(id, key, relativeTo, itemCredentials, newRange);
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
    public ItemCredentials getItemCredentials() { return itemCredentials; }

    @Override
    public QueryRange getRange() {
        return range;
    }
}
