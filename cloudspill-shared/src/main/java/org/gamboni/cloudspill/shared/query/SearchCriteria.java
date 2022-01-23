package org.gamboni.cloudspill.shared.query;

import org.gamboni.cloudspill.shared.api.CloudSpillApi;
import org.gamboni.cloudspill.shared.util.UrlStringBuilder;

import java.util.Set;

/**
 *
 * @author tendays
 */
public interface SearchCriteria {

    Set<String> getTags();

    String getStringFrom();

    String getStringTo();

    String getUser();

    QueryRange getRange();

    /** The search criteria offset can be "relative to" an item given by id, to get the items right before or right after the item
     * in the search results. When this value is non-null, offset zero is the specified item. Offsets can be negative, for instance
     * offset -1 means to start from the item right before the one specified by {@code relativeTo}. If the item is not part of
     * search results, just add it at the right location according to sort criteria to decide from which item to start returning
     * results (but don't actually return the specified item).
     */
    Long getRelativeTo();

    UrlStringBuilder getUrl(CloudSpillApi api);

}
