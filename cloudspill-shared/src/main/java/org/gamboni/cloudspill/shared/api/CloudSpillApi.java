package org.gamboni.cloudspill.shared.api;

import org.gamboni.cloudspill.shared.domain.IsItem;
import org.gamboni.cloudspill.shared.domain.Items;

import java.util.HashSet;
import java.util.Set;

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
        // make sure server urls end in a slash.
        this.serverUrl = serverUrl + (serverUrl.endsWith("/") ? "" : "/");
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

    public String getItemsSinceUrl(Object millis) {
        return serverUrl +"sinceDate/"+ millis;
    }

    public String getTagUrl(Object itemId) {
        return serverUrl +"item"+ itemId +"/tags";
    }

    public enum Size {
        PHONE_THUMBNAIL(90),
        GALLERY_THUMBNAIL(150),
        IMAGE_THUMBNAIL(300);
        public final int pixels;

        Size(int pixels) {
            this.pixels = pixels;
        }
        public String toString() {
            return String.valueOf(pixels);
        }
    }

    private static ItemCredentials credentialsForItem(IsItem item) {
        return Items.isPublic(item) ? new ItemCredentials.PublicAccess() : new ItemCredentials.ItemKey(item.getChecksum());
    }

    public String getThumbnailUrl(IsItem item, Size size) {
        return getThumbnailUrl(item.getServerId(), credentialsForItem(item), size);
    }

    public String getThumbnailUrl(long id, ItemCredentials credentials, Object pixels) {
        return serverUrl + credentials.getUrlPrefix() +"thumbs/"+ pixels +"/"+ id + credentials.getQueryString();
    }

    public String getImageUrl(long id, ItemCredentials credentials) {
        return serverUrl + credentials.getUrlPrefix() + "item/"+ id + credentials.getQueryString();
    }

    public String getImageUrl(IsItem item) {
        return getImageUrl(item.getServerId(), credentialsForItem(item));
    }

    public String getPublicImagePageUrl(IsItem item) {
        return getImagePageUrl(item.getServerId(), credentialsForItem(item));
    }

    public String getImagePageUrl(long serverId, ItemCredentials credentials) {
        return serverUrl + credentials.getUrlPrefix() + "item/"+ serverId + ID_HTML_SUFFIX + credentials.getQueryString();
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
