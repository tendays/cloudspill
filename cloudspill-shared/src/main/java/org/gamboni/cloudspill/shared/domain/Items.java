package org.gamboni.cloudspill.shared.domain;

/**
 * @author tendays
 */
public abstract class Items {

    public static boolean isPublic(IsItem item) {
        return item.getTags().contains("public");
    }
}
