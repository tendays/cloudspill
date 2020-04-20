package org.gamboni.cloudspill.server.query;

import org.gamboni.cloudspill.shared.query.SearchCriteria;

/**
 * @author tendays
 */
public interface GalleryRequest extends SearchCriteria {
    GalleryRequest atOffset(int offset);
}
