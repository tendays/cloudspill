package org.gamboni.cloudspill.shared.domain;

import org.gamboni.cloudspill.shared.query.SearchCriteria;

import java.util.Set;

/**
 * @author tendays
 */
public abstract class Items {
    public static boolean isPublic(SearchCriteria criteria) {
        return isPublic(criteria.getTags());
    }

    public static boolean isPublic(IsItem item) {
        return isPublic(item.getTags());
    }

    public static boolean isPublic(Set<String> tags) {
        return tags.contains("public");
    }
}
