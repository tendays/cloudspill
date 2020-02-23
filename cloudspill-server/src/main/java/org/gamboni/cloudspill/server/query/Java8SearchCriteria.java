package org.gamboni.cloudspill.server.query;

import org.gamboni.cloudspill.shared.query.SearchCriteria;

import java.time.LocalDate;

/** {@link SearchCriteria} extended with Java 8 features like java.time or default methods.
 *
 * @author tendays
 */
public interface Java8SearchCriteria extends SearchCriteria {
    LocalDate getFrom();

    LocalDate getTo();

    @Override
    default String getStringFrom() {
        return (getFrom() == null) ? null : getFrom().toString();
    }

    @Override
    default String getStringTo() {
        return (getTo() == null) ? null : getTo().toString();
    }

    Java8SearchCriteria atOffset(int newOffset);
}
