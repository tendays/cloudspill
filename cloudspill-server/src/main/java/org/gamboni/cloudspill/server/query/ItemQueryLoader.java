package org.gamboni.cloudspill.server.query;

import org.gamboni.cloudspill.domain.BackendItem;
import org.gamboni.cloudspill.server.OrHttpError;

/**
 * @author tendays
 */
public interface ItemQueryLoader {
    OrHttpError<ItemSet> load(Java8SearchCriteria<BackendItem> criteria);

    OrHttpError<ItemSet> load(Java8SearchCriteria<BackendItem> criteria, int limit);
}
