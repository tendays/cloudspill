package org.gamboni.cloudspill.shared.util;

/**
 * @author tendays
 */
public interface Func<F, T> {
    T apply(F value);
}
