package org.gamboni.cloudspill.domain;

import java.util.Date;

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
        this.from = from;
        this.to = to;
        this.by = by;
        this.sort = sort;
    }
}
