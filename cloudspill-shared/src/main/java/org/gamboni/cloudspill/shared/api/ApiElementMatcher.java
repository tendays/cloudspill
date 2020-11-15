package org.gamboni.cloudspill.shared.api;

/**
 * @author tendays
 */
public interface ApiElementMatcher<T> {
    public enum HttpMethod { GET, POST, DELETE, PUT}

    void match(HttpMethod method, String url, T consumer);
}
