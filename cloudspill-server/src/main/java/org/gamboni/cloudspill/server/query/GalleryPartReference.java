package org.gamboni.cloudspill.server.query;

import org.gamboni.cloudspill.domain.BackendItem;
import org.gamboni.cloudspill.shared.api.CloudSpillApi;

import java.time.LocalDate;
import java.util.Set;

/**
 * @author tendays
 */
public class GalleryPartReference implements Java8SearchCriteria<BackendItem> {
    public final long id;
    private final int offset;
    private final Integer limit;

    @Override
    public String getUrl(CloudSpillApi api) {
        return api.galleryPart(id, offset, limit);
    }

    public GalleryPartReference(long id) {
        this(id, 0, null);
    }

    private GalleryPartReference(long id, int offset, Integer limit) {
        this.id = id;
        this.offset = offset;
        this.limit = limit;
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
    public GalleryPartReference atOffset(int newOffset) {
        return new GalleryPartReference(id, newOffset, limit);
    }

    @Override
    public Java8SearchCriteria<BackendItem> withLimit(Integer newLimit) {
        return new GalleryPartReference(id, offset, newLimit);
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
    public int getOffset() {
        return offset;
    }

    @Override
    public Integer getLimit() {
        return limit;
    }
}
