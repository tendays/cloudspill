package org.gamboni.cloudspill.shared.query;

import org.gamboni.cloudspill.shared.api.CloudSpillApi;

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

    int getOffset();

    Integer getLimit();

    String getUrl(CloudSpillApi api);

}
