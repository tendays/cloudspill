package org.gamboni.cloudspill.shared.query;

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

    String getUrl();

}
