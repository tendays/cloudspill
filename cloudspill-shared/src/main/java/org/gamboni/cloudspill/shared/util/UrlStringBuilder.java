package org.gamboni.cloudspill.shared.util;

/**
 * @author tendays
 */
public class UrlStringBuilder {
    private final StringBuilder base;
    private final StringBuilder queryParams;

    public UrlStringBuilder(String base) {
        this.base = new StringBuilder(base);
        this.queryParams = new StringBuilder();
    }

    public UrlStringBuilder append(String part) {
        base.append(part);
        return this;
    }

    public UrlStringBuilder appendQueryParam(String key, Object value) {
        queryParams.append((queryParams.length() == 0) ? '?' : '&').append(key +"="+value);
        return this;
    }

    public String toString() {
        return base.toString() + queryParams;
    }
}
