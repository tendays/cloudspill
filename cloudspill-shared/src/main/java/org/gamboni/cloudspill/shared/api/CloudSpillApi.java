package org.gamboni.cloudspill.shared.api;

import org.gamboni.cloudspill.shared.client.ResponseHandler;
import org.gamboni.cloudspill.shared.client.ResponseHandlers;
import org.gamboni.cloudspill.shared.domain.IsItem;
import org.gamboni.cloudspill.shared.domain.Items;
import org.gamboni.cloudspill.shared.query.GalleryRequest;
import org.gamboni.cloudspill.shared.query.QueryRange;
import org.gamboni.cloudspill.shared.util.UrlStringBuilder;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author tendays
 */
public class CloudSpillApi<T> {
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
    private final ApiElementMatcher<T> matcher;

    public CloudSpillApi(String serverUrl) {
        // make sure server urls end in a slash.
        this.serverUrl = serverUrl + (serverUrl.endsWith("/") ? "" : "/");
        this.matcher = null;
    }

    public CloudSpillApi(String serverUrl, ApiElementMatcher<T> matcher) {
        this.serverUrl = serverUrl + (serverUrl.endsWith("/") ? "" : "/");
        this.matcher = matcher;
    }

    public String getUrl(StaticResource resource) {
        String className = resource.getClass().getSimpleName();
        String extension = resource.getExtension();
        String suffix = extension.substring(0, 1).toUpperCase() + extension.substring(1);
        String baseName = className.endsWith(suffix) ? className.substring(0, className.length() - suffix.length()) : className;
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < baseName.length() ; i++) {
            char chr = baseName.charAt(i);
            if (Character.isUpperCase(chr)) {
                if (result.length() > 0) {
                    result.append('-');
                }
                result.append(Character.toLowerCase(chr));
            } else {
                result.append(chr);
            }
        }
        result.append('.');
        result.append(resource.getExtension());
        return serverUrl + result;
    }

    public static class Client implements ApiElementMatcher<ResponseHandler> {
        public final void match(ApiElementMatcher.HttpMethod method, String url, ResponseHandler consumer) {
            try {
                handle(method, url, consumer);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        protected void handle(HttpMethod method, String url, ResponseHandler consumer) throws IOException {
            final HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod(method.name());

            consumer.handle(connection);
        }
    }

    public static CloudSpillApi<ResponseHandler> client(String serverUrl) {
        return new CloudSpillApi<ResponseHandler>(serverUrl, new Client());
    }

    public static CloudSpillApi<ResponseHandler> authenticatedClient(String serverUrl, ItemCredentials credentials, Base64Encoder base64Encoder) {
        return new CloudSpillApi<ResponseHandler>(serverUrl, new Client() {

            protected void handle(HttpMethod method, String url, ResponseHandler consumer) throws IOException {
                super.handle(method, url, ResponseHandlers.withCredentials(credentials, base64Encoder, consumer));
            }
        });
    }

    /** Function used by clients to ensure connectivity is available and check API compatibility. */
    public String ping() {
        return serverUrl + "ping";
    }

    public String knownTags() {
        return serverUrl + "known-tags";
    }

    public void ping(T consumer) {
        matcher.match(ApiElementMatcher.HttpMethod.GET, ping(), consumer);
    }

    /** "Upload file" function: file timestamp HTTP header */
    public static final String UPLOAD_TIMESTAMP_HEADER = "X-CloudSpill-Timestamp";
    /** "Upload file" function: file type (ItemType) HTTP header */
    public static final String UPLOAD_TYPE_HEADER = "X-CloudSpill-Type";

    /** PUT URL to upload a file */
    public String upload(String user, String folder, String path) {
        return serverUrl +"item/"+ encodePathPart(user) +"/"+ encodePathPart(folder) +"/"+ encodePathPart(path);
    }

    public void upload(String user, String folder, String path, T consumer) {
        matcher.match(ApiElementMatcher.HttpMethod.PUT, upload(user, folder, path), consumer);
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
        return serverUrl +"item/"+ itemId +"/tags";
    }

    public void putTags(Object itemId, T consumer) {
        matcher.match(ApiElementMatcher.HttpMethod.PUT, getTagUrl(itemId), consumer);
    }

    public void putTags(T consumer) {
        matcher.match(ApiElementMatcher.HttpMethod.PUT, serverUrl +"tags", consumer);
    }

    public void setItemDescription(Object itemId, T consumer) {
        matcher.match(ApiElementMatcher.HttpMethod.POST, serverUrl +"item/"+ itemId +"/description", consumer);
    }

    public void postComment(Object itemId, T consumer) {
        matcher.match(ApiElementMatcher.HttpMethod.POST, serverUrl +"item/"+ itemId +"/comments", consumer);
    }

    public void getComments(Object itemId, T consumer) {
        matcher.match(ApiElementMatcher.HttpMethod.GET, serverUrl +"item/"+ itemId +"/comments", consumer);
    }

    public void validateToken(Object name, Object id, T consumer) {
        matcher.match(ApiElementMatcher.HttpMethod.POST, serverUrl + "/user/"+ name +"/tokens/"+ id +"/validate", consumer);
    }

    public void deleteToken(Object name, Object id, T consumer) {
        matcher.match(ApiElementMatcher.HttpMethod.DELETE, serverUrl + "/user/"+ name +"/tokens/"+ id, consumer);
    }

    public String css() {
        return serverUrl +"main.css";
    }

    public String galleryJS() {
        return serverUrl +"gallery.js";
    }

    public String editorJS() {
        return serverUrl +"editor.js";
    }

    public String tagwidgetJS() {
        return serverUrl +"tagwidget.js";
    }

    public String commentsJS() {
        return serverUrl +"comments.js";
    }

    public String tokenListJS() {
        return serverUrl +"token-list.js";
    }

    public String loginJS() { return serverUrl +"login.js"; }

    public String uploadJS() { return serverUrl +"upload.js"; }

    public String galleryListPage(ItemCredentials credentials) {
        return serverUrl + credentials.getUrlPrefix() + "gallery/";
    }

    public void rootView(T consumer) {
        matcher.match(ApiElementMatcher.HttpMethod.GET, serverUrl, consumer);
    }

    public void galleryListView(ItemCredentials credentials, T consumer) {
        matcher.match(ApiElementMatcher.HttpMethod.GET, galleryListPage(credentials), consumer);
    }

    public void tagView(T consumer) {
        matcher.match(ApiElementMatcher.HttpMethod.GET, serverUrl +"tag/", consumer);
    }

    public void tagView(String tag, T consumer) {
        matcher.match(ApiElementMatcher.HttpMethod.GET, serverUrl +"tag/"+ tag, consumer);
    }

    public void dayView(Object day, T consumer) {
        matcher.match(ApiElementMatcher.HttpMethod.GET, serverUrl +"day/"+ day, consumer);
    }

    public void yearView(Object year, T consumer) {
        matcher.match(ApiElementMatcher.HttpMethod.GET, serverUrl +"year/"+ year, consumer);
    }

    public UrlStringBuilder galleryPart(Object id, String key, Long relativeTo, ItemCredentials itemCredentials, QueryRange range) {
        final UrlStringBuilder url = new UrlStringBuilder(serverUrl + "public/gallery/" + id);
        if (key != null) {
            url.appendQueryParam("gkey", key);
        }
        return sliceParameters(url, relativeTo, itemCredentials, range);
    }

    public void galleryPart(Object id, String key, Long relativeTo, ItemCredentials itemCredentials, QueryRange range, T consumer) {
        matcher.match(ApiElementMatcher.HttpMethod.GET, galleryPart(id, key, relativeTo, itemCredentials, range).toString(), consumer);
    }

    public String login() {
        return serverUrl +"session/login";
    }

    public String logout() {
        return serverUrl +"session/delete-token";
    }

    public String newToken(String username) {
        return serverUrl +"user/"+ encodePathPart(username) +"/new-token";
    }

    public String listTokens(String username) {
        return serverUrl +"user/"+ username +"/tokens";
    }

    public void tokenListView(String username, T consumer) {
        matcher.match(ApiElementMatcher.HttpMethod.GET, listTokens(username), consumer);
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
        return Items.isPublic(item) ? new ItemCredentials.PublicAccess() : new ItemCredentials.UserPassword();
    }

    private static ItemCredentials publicCredentialsForItem(IsItem item) {
        return Items.isPublic(item) ? new ItemCredentials.PublicAccess() : new ItemCredentials.ItemKey(item.getChecksum());
    }

    public String getThumbnailUrl(IsItem item, Size size) {
        return getThumbnailUrl(item.getServerId(), Collections.singletonList(credentialsForItem(item)), size);
    }

    public String getThumbnailUrl(Object id, ItemCredentials credentials, Object pixels) {
        return getThumbnailUrl(id, Collections.singletonList(credentials), pixels);
    }

    public String getThumbnailUrl(Object id, List<ItemCredentials> credentials, Object pixels) {
        UrlStringBuilder url = getBaseUrl(credentials).append("thumbs/")
                .append(pixels)
                .append("/")
                .append(id);
        appendQueryStrings(credentials, url);
        return url.toString();
    }

    public String getImageUrl(long id, List<ItemCredentials> credentials) {
        UrlStringBuilder url = getBaseUrl(credentials);
        url.append("item/").append(id);
        appendQueryStrings(credentials, url);
        return url.toString();
    }

    private void appendQueryStrings(List<ItemCredentials> credentials, UrlStringBuilder url) {
        for (ItemCredentials c : credentials) {
            c.setQueryParams(url);
        }
    }

    private UrlStringBuilder getBaseUrl(List<ItemCredentials> credentials) {
        UrlStringBuilder url = new UrlStringBuilder(serverUrl);
        for (ItemCredentials c : credentials) {
            url.append(c.getUrlPrefix());
        }
        return url;
    }

    public String getImageUrl(IsItem item) {
        return getImageUrl(item.getServerId(), Collections.singletonList(credentialsForItem(item)));
    }

    public String getPublicImageUrl(IsItem item) {
        return getImageUrl(item.getServerId(), Collections.singletonList(publicCredentialsForItem(item)));
    }

    public String getPublicImagePageUrl(IsItem item) {
        return getImagePageUrl(item.getServerId(), null, Collections.singletonList(publicCredentialsForItem(item)));
    }

    public String getImagePageUrl(Object serverId, GalleryRequest criteria, ItemCredentials credentials) {
        return getImagePageUrl(serverId, criteria, Collections.singletonList(credentials));
    }

    public String getImagePageUrl(Object serverId, GalleryRequest criteria, List<ItemCredentials> credentials) {
        UrlStringBuilder url;
        if (criteria == null) {
            url = getBaseUrl(credentials).append("item/");
        } else {
            url = criteria.withRange(QueryRange.ALL).getUrl(this).append("/");
        }
        url.append(serverId)
           .append(ID_HTML_SUFFIX);
        appendQueryStrings(credentials, url);
        return url.toString();
    }

    public UrlStringBuilder getGalleryUrl(Set<String> tags, String stringFrom, String stringTo, Long relativeTo, ItemCredentials itemCredentials, QueryRange range) {
        Set<String> otherTags = new HashSet<>();
        boolean isPublic = false;
        for (String tag : tags) {
            if (tag.equals("public")) {
                isPublic = true;
            } else {
                otherTags.add(tag);
            }
        }

        UrlStringBuilder builder = new UrlStringBuilder(serverUrl);
        if (isPublic) {
            builder.append("public/");
        }
        if (otherTags.size() == 1 && stringFrom == null && stringTo == null) {
            builder.append("tag/" + encodePathPart(otherTags.iterator().next()));
        } else if (otherTags.isEmpty() && stringFrom != null && stringFrom.equals(stringTo)) {
            builder.append("day/"+ stringFrom);
        } else {
            // TODO
        }
        return sliceParameters(builder, relativeTo, itemCredentials, range);
    }

    private UrlStringBuilder sliceParameters(UrlStringBuilder builder, Long relativeTo, ItemCredentials itemCredentials, QueryRange range) {
        if (relativeTo != null) {
            builder.appendQueryParam("relativeTo", relativeTo);
            if (itemCredentials != null) {
                // only required for relativeTo
                itemCredentials.setQueryParams(builder);
            }
        }
        if (range.offset != 0) {
            builder.appendQueryParam("offset", range.offset);
        }
        if (range.limit != null) {
            builder.appendQueryParam("limit", range.limit);
        }
        return builder;
    }
}
