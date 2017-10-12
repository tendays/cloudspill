package org.gamboni.cloudspill.domain;

/**
 * @author tendays
 */
public enum ItemType {
    IMAGE, VIDEO, UNKNOWN;

    public static ItemType valueOfOptional(String name) {
        if (name == null) {
            return UNKNOWN;
        }
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
