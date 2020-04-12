package org.gamboni.cloudspill.shared.api;

import org.gamboni.cloudspill.shared.domain.IsItem;
import org.gamboni.cloudspill.shared.domain.Items;
import org.gamboni.cloudspill.shared.query.SearchCriteria;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author tendays
 */
public class CloudSpillApi {
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

    private final String serverUrl;

    public CloudSpillApi(String serverUrl) {
        // make sure non-empty server urls end in a slash.
        this.serverUrl = serverUrl + (serverUrl.isEmpty() || serverUrl.endsWith("/") ? "" : "/");
    }

    /** Function used by clients to ensure connectivity is available and check API compatibility. */
    public String ping() {
        return serverUrl + "ping";
    }

    /** "Upload file" function: file timestamp HTTP header */
    public static final String UPLOAD_TIMESTAMP_HEADER = "X-CloudSpill-Timestamp";
    /** "Upload file" function: file type (ItemType) HTTP header */
    public static final String UPLOAD_TYPE_HEADER = "X-CloudSpill-Type";

    /** PUT URL to upload a file */
    public String upload(String user, String folder, String path) {
        return serverUrl +"item/"+ encodePathPart(user) +"/"+ encodePathPart(folder) +"/"+ encodePathPart(path);
    }

    private static String encodePathPart(String text) {
        /* See: https://www.talisman.org/~erlkonig/misc/lunatech%5Ewhat-every-webdev-must-know-about-url-encoding/ */
        // We however do NOT encode slashes. For instance upload("user", "folder", "path1/path2") will correctly do /user/folder/path2/path2
        // User and folder names are not allowed to contain slashes, as they must be usable in filesystem folder names
        return text
                .replace("%", "%25") // must be first
                .replace(" ", "%20")
                .replace("?", "%3F");
    }

    public String getBaseUrl() {
        return serverUrl;
    }

    public String getLoggedInThumbnailUrl(long serverId, Integer thumbnailSize) {
        return serverUrl +"thumbs/"+ thumbnailSize +"/"+ serverId;
    }

    public String getItemsSinceUrl(Object millis) {
        return serverUrl +"sinceDate/"+ millis;
    }

    public String getTagUrl(Object itemId) {
        return serverUrl +"item"+ itemId +"/tags";
    }

    public enum Size {
        GALLERY_THUMBNAIL(150),
        IMAGE_THUMBNAIL(300);
        public final int pixels;

        Size(int pixels) {
            this.pixels = pixels;
        }
    }

    public String getThumbnailUrl(IsItem item, Size size) {
        return getLoggedInThumbnailUrl(item.getServerId(), size.pixels) + accessKeyQueryString(item);
    }

    public String getImageUrl(IsItem item) {
        return serverUrl +"/item/"+ item.getServerId() + accessKeyQueryString(item);
    }

    public String getLoggedInImageUrl(long serverId) {
        return serverUrl +"/item/"+ serverId;
    }

    private static String accessKeyQueryString(IsItem item) {
        return "?key="+ item.getChecksum().replace("+", "%2B");
    }

    public String getPublicImagePageUrl(IsItem item) {
        if (Items.isPublic(item)) {
            return serverUrl +"/public/item/" + item.getServerId() + ID_HTML_SUFFIX;
        } else {
            return serverUrl +"/item/" + item.getServerId() + ID_HTML_SUFFIX + accessKeyQueryString(item);
        }
    }

    public String getLoggedInImagePageUrl(IsItem item) {
        return serverUrl +"/item/" + item.getServerId() + ID_HTML_SUFFIX;
    }

    public String getGalleryUrl(Set<String> tags, String stringFrom, String stringTo, int offset) {
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
        String baseUrl = serverUrl + (isPublic ? "/public" : "/");
        if (otherTags.size() == 1 && stringFrom == null && stringTo == null) {
            return baseUrl + "/tag/" + encodePathPart(otherTags.iterator().next()) + offsetQuery;
        } else if (otherTags.isEmpty() && stringFrom != null && stringFrom.equals(stringTo)) {
            return baseUrl +"/day/"+ stringFrom + offsetQuery;
        } else {
            return baseUrl + offsetQuery; // TODO
        }
    }
}
