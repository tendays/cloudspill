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
    public final String providedKey;
    private final Long relativeTo;
    private final ItemCredentials itemCredentials;
    private final QueryRange range;

    public GalleryPartReference(long id, String providedKey) {
        this(id, providedKey, null, null, QueryRange.ALL);
    }

    private GalleryPartReference(long id, String providedKey, Long relativeTo, ItemCredentials itemCredentials, QueryRange range) {
        this.id = id;
        this.providedKey = providedKey;
        this.relativeTo = relativeTo;
        this.itemCredentials = itemCredentials;
        this.range = range;
    }

    @Override
    public UrlStringBuilder getUrl(CloudSpillApi api) {
        return api.galleryPart(id, providedKey, relativeTo, itemCredentials, range);
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
        return new GalleryPartReference(id, providedKey, itemId, itemCredentials, range);
    }

    @Override
    public Java8SearchCriteria<BackendItem> withRange(QueryRange newRange) {
        return new GalleryPartReference(id, providedKey, relativeTo, itemCredentials, newRange);
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
    public ItemCredentials getCredentialForPattern(String itemKey) {
        if (providedKey != null) {
            return new ItemCredentials.ItemKey(itemKey);
        } else {
            return new ItemCredentials.PublicAccess();
        }
    }

    @Override
    public QueryRange getRange() {
        return range;
    }
}
