package org.gamboni.cloudspill.shared.api;

import org.gamboni.cloudspill.shared.domain.IsItem;
import org.gamboni.cloudspill.shared.domain.Items;
import org.gamboni.cloudspill.shared.query.SearchCriteria;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author tendays
 */
public abstract class CloudSpillApi {
    /** Function used by clients to ensure connectivity is available and check API compatibility. */
    public static final String PING = "/ping";
    public static final String PING_PREAMBLE = "CloudSpill server.";
    /** Data version field in response */
    public static final String PING_DATA_VERSION = "Data-Version";
    /** Public url field in response */
    public static final String PING_PUBLIC_URL = "Url";

    /** "Create User" function: name of the user to create (POST parameter) */
    public static final String CREATE_USER_NAME = "name";
    /** "Create User" function: password of the user to create (POST parameter) */
    public static final String CREATE_USER_PASS = "pass";

    /** Suffix added to the id parameter to trigger downloading an html page instead of the raw image. */
    public static final String ID_HTML_SUFFIX = ".cloudspill";

    /** PUT URL to upload a file */
    public static String upload(String user, String folder, String path) {
        return "/item/"+ user +"/"+ folder +"/"+ path;
    }
    /** "Upload file" function: file timestamp HTTP header */
    public static final String UPLOAD_TIMESTAMP_HEADER = "X-CloudSpill-Timestamp";
    /** "Upload file" function: file type (ItemType) HTTP header */
    public static final String UPLOAD_TYPE_HEADER = "X-CloudSpill-Type";

    public static String getThumbnailUrl(IsItem item) {
        return "/thumbs/300/"+ item.getServerId() + accessKeyQueryString(item);
    }

    public static String getImageUrl(IsItem item) {
        return "/item/"+ item.getServerId() + accessKeyQueryString(item);
    }

    public static String accessKeyQueryString(IsItem item) {
        return "?key="+ item.getChecksum().replace("+", "%2B");
    }

    public static String getPublicImagePageUrl(IsItem item) {
        if (Items.isPublic(item)) {
            return "/public/item/" + item.getServerId() + ID_HTML_SUFFIX;
        } else {
            return "/item/" + item.getServerId() + ID_HTML_SUFFIX + accessKeyQueryString(item);
        }
    }

    public static String getLoggedInImagePageUrl(IsItem item) {
        return "/item/" + item.getServerId() + ID_HTML_SUFFIX;
    }

    public static String getGalleryUrl(Set<String> tags, String stringFrom, String stringTo, int offset) {
        Set<String> otherTags = new HashSet<>();
        boolean isPublic = false;
        for (String tag : tags) {
            if (tag.equals("public")) {
                isPublic = true;
            } else {
                otherTags.add(tag);
            }
        }
        String offsetQuery = (offset == 0) ? "" : ("?offset="+ offset);
        String baseUrl = (isPublic) ? "/public" : "/";
        if (otherTags.size() == 1 && stringFrom == null && stringTo == null) {
            return baseUrl + "/tag/" + otherTags.iterator().next() + offsetQuery;
        } else if (otherTags.isEmpty() && stringFrom != null && stringFrom.equals(stringTo)) {
            return baseUrl +"/day/"+ stringFrom + offsetQuery;
        } else {
            return baseUrl + offsetQuery; // TODO
        }
    }
}
