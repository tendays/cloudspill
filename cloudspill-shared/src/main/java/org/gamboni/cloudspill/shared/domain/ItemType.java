/**
 * 
 */
package org.gamboni.cloudspill.shared.domain;

/** Media type.
 *
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

    public String asMime() {
        switch (this) {
            case IMAGE: return  "image/jpeg";
            case VIDEO: return "video/mp4";
            case UNKNOWN: return "application/octet-stream";
        }
        throw new IllegalArgumentException("Unsupported type "+ this);
    }

    public static ItemType fromMime(String mimeType) {
        for (ItemType candidate : values()) {
            if (candidate.asMime().equals(mimeType)) {
                return candidate;
            }
        }
        return UNKNOWN;
    }
}