package org.gamboni.cloudspill.shared.query;

import java.time.LocalDate;
import java.util.Date;
import java.util.Set;

/**
 *
 * @author tendays
 */
public interface SearchCriteria {

    Set<String> getTags();

    String getStringFrom();

    String getStringTo();
}
