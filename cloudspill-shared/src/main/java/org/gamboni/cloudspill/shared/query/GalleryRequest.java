package org.gamboni.cloudspill.shared.query;

/**
 * @author tendays
 */
public interface GalleryRequest extends SearchCriteria {
    GalleryRequest withRange(QueryRange range);
}
