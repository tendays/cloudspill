package org.gamboni.cloudspill.shared.util;

/** Same as Java8's Supplier, but also available in Android client.
 * Implementations can be easily be convert to another Supplier by using the {@code ::get} method reference
 *
 * @author tendays
 */
public interface Supplier<T> {
    T get();
}
