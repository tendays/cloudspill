package org.gamboni.cloudspill.domain;

import java.util.Date;
import java.util.Objects;

/**
 * @author tendays
 */

public class FilterSpecification {

    public enum Sort {
        DATE_DESC, DATE_ASC
    }

    public final Date from;
    public final Date to;
    public final String by;
    public final Sort sort;

    public FilterSpecification(Date from, Date to, String by, Sort sort) {
        Objects.requireNonNull(sort);
        this.from = from;
        this.to = to;
        this.by = by;
        this.sort = sort;
    }

    public static FilterSpecification defaultFilter() {
        return new FilterSpecification(null, null, null, Sort.DATE_DESC);
    }

    public String toString() {
        StringBuilder result = new StringBuilder();
        if (from != null) {
            result.append(from).append(" <= date");
            if (to != null) {
                result.append(" <= ").append(to);
            }
        } else if (to != null) {
            result.append("date <= ").append(to);
        }
        if (by != null) {
            if (result.length() != 0) {
                result.append(", ");
            }
            result.append("by ").append(by);
        }
        if (result.length() != 0) {
            result.append(", ");
        }
        result.append(sort);
        return result.toString();
    }
}
