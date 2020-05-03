package org.gamboni.cloudspill.server;

import com.google.common.io.ByteStreams;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.gamboni.cloudspill.domain.BackendItem;
import org.gamboni.cloudspill.domain.CloudSpillEntityManagerDomain;
import org.gamboni.cloudspill.server.config.BackendConfiguration;
import org.gamboni.cloudspill.server.html.AbstractPage;
import org.gamboni.cloudspill.server.html.GalleryListPage;
import org.gamboni.cloudspill.server.html.GalleryPage;
import org.gamboni.cloudspill.server.html.ImagePage;
import org.gamboni.cloudspill.server.query.ItemQueryLoader;
import org.gamboni.cloudspill.server.query.ItemSet;
import org.gamboni.cloudspill.server.query.Java8SearchCriteria;
import org.gamboni.cloudspill.server.query.ServerSearchCriteria;
import org.gamboni.cloudspill.shared.api.CloudSpillApi;
import org.gamboni.cloudspill.shared.api.Csv;
import org.gamboni.cloudspill.shared.api.ItemCredentials;
import org.gamboni.cloudspill.shared.domain.IsUser;
import org.gamboni.cloudspill.shared.util.Log;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import spark.Request;
import spark.Response;
import spark.Route;

import static org.gamboni.cloudspill.shared.api.CloudSpillApi.ID_HTML_SUFFIX;
import static spark.Spark.after;
import static spark.Spark.before;
import static spark.Spark.get;
import static spark.Spark.put;

/** Common implementation of the {@link org.gamboni.cloudspill.shared.api.CloudSpillApi}, implemented by both {@link CloudSpillServer}
 * and {@link CloudSpillForwarder}.
 *
 * @author tendays
 */
public abstract class CloudSpillBackend<D extends CloudSpillEntityManagerDomain> extends AbstractServer<D> {
    private static final ItemCredentials.PublicAccess publicAccess = new ItemCredentials.PublicAccess();

    protected final void setupRoutes(BackendConfiguration configuration) {
        CloudSpillApi api = new CloudSpillApi("");

        /* Access logging */
        before((req, res) -> {
            Log.info(req.ip() +" "+ req.requestMethod() +" "+ req.uri());
        });

        /* Print full request processing time in HTML pages */
        before((req, res) -> AbstractPage.recordRequestStart());
        after((req, res) -> AbstractPage.clearRequestStopwatch());

        get(api.ping(), secured((req, res, session, user) -> ping()));

        get(api.css(), (req, res) -> {
            ByteStreams.copy(
                    getClass().getClassLoader().getResourceAsStream("css/main.css"),
                    res.raw().getOutputStream());
            return "";
        });

        get("/robots.txt", (req, res)->
                "User-agent: *\n" +
                "Disallow: /");

        get("/tag/:tag", secured((req, res, domain, credentials) ->
        {
            return galleryPage(configuration, req, res, domain, credentials,
                    ServerSearchCriteria.ALL.withTag(req.params("tag")),
                    DumpFormat.WITH_TOTAL);
        }));

        get("/day/:day", secured((req, res, domain, credentials) -> {
            LocalDate day = LocalDate.parse(req.params("day"));

            return galleryPage(configuration, req, res, domain, credentials,
                    ServerSearchCriteria.ALL.at(day),
                    DumpFormat.WITH_TOTAL);
        }));

        get(api.galleryListPage(new ItemCredentials.UserPassword()), secured((req, res, domain, credentials) ->
                galleryListPage(res, configuration, domain, credentials, req)));

        get("/gallery/:part", secured((req, res, domain, credentials) -> {
            return galleryPage(configuration, req, res, domain, credentials,
                    loadGallery(domain, Long.parseLong(req.params("part"))),
                    DumpFormat.GALLERY_DATA);
        }));

        get(api.galleryListPage(publicAccess), (req, res) -> transacted(domain -> {
            return galleryListPage(res, configuration, domain, publicAccess, req);
        }));

        get("/public/gallery/:part", (req, res) -> transacted(domain -> {
            return galleryPage(configuration, req, res, domain,
                    publicAccess,
                    loadGallery(domain, Long.parseLong(req.params("part"))),
                    DumpFormat.GALLERY_DATA);
        }));

        final Route publicRoute = (req, res) -> transacted(session -> {
            return galleryPage(configuration, req, res, session,
                    publicAccess,
                    ServerSearchCriteria.ALL.withTag("public"),
                    DumpFormat.WITH_TOTAL);
        });
        get("/public", publicRoute);
        get("/public/", publicRoute);

        /* Download a file */
        get("/item/:id", securedItem(ItemCredentials.AuthenticationStatus.LOGGED_IN, (req, res, session, credentials, item) -> {
            return itemPage(configuration, req, res, session, credentials, item);
        }));

        /* Download a public file */
        get("/public/item/:id", securedItem(ItemCredentials.AuthenticationStatus.ANONYMOUS, (req, res, session, credentials, item) -> {
                return itemPage(configuration, req, res, session, credentials, item);
        }));

        /* Download a thumbnail */
        get("/thumbs/:size/:id", securedItem(ItemCredentials.AuthenticationStatus.LOGGED_IN, (req, res, session, credentials, item) -> {
            thumbnail(res, session, credentials, item, Integer.parseInt(req.params("size")));
            return "";
        }));

        /* Download a thumbnail */
        get("/public/thumbs/:size/:id", securedItem(ItemCredentials.AuthenticationStatus.ANONYMOUS, (req, res, session, credentials, item) -> {
            thumbnail(res, session, credentials, item, Integer.parseInt(req.params("size")));
            return "";
        }));

        /* Get list of items whose id is larger than the given one. */
        get("item/since/:id", secured((req, res, domain, credentials) -> {
            return dump(req, res, domain, ServerSearchCriteria.ALL.withIdAtLeast(Long.parseLong(req.params("id"))), credentials,
                    DumpFormat.WITH_TOTAL);
        }));

        /* Get list of items updated at or later than the given timestamp. */
        get(api.getItemsSinceUrl(":date"), secured((req, res, domain, credentials) -> {
            return dump(req, res, domain,
                    ServerSearchCriteria.ALL.modifiedSince(Instant.ofEpochMilli(Long.parseLong(req.params("date")))),
                    credentials,
                DumpFormat.WITH_TIMESTAMP);
        }));

        /* Add the tags specified in body to the given item. */
        put(api.getTagUrl(":id"), secured((req, res, session, user) -> {
            Log.debug("Tag query for item "+ req.params("id") +": '"+ req.body() +"'");
            putTags(session, Long.parseLong(req.params("id")), req.body());
            return true;
        }));

        /* Upload a file */
        put(api.upload(":user", ":folder", "*"), secured((req, res, session, credentials) -> {
        	/*if (req.bodyAsBytes() == null) {
        		Log.warn("Missing body");
        		res.status(400);
        		return null;
        	}*/
            IsUser user = credentials.user;
            String username = req.params("user");
            String folder = req.params("folder");
            if (!user.getName().equals(username)) {
                Log.error("User "+ user.getName() +" attempted to upload to folder of user "+ username);
                return forbidden(res, false);
            }
            String path = req.splat()[0];
            Log.debug("user is "+ username +", folder is "+ folder +" and path is "+ path);
            return upload(req, res, session, credentials, folder, path);
        }));
    }

    private Object galleryPage(BackendConfiguration configuration, Request req, Response res, D domain, ItemCredentials credentials, Java8SearchCriteria<BackendItem> allItems,
                               DumpFormat format) throws Exception {
        final Java8SearchCriteria<BackendItem> offset = allItems.atOffset(Integer.parseInt(req.queryParamOrDefault("offset", "0")));

        return getQueryLoader(domain, credentials)
                .load(offset.withLimit((isCsvRequested(req) || isJsonRequested(req)) ? requestedLimit(req) : Integer.valueOf(GalleryPage.PAGE_SIZE)))
                .get(res, itemSet -> {
            if (isCsvRequested(req) || isJsonRequested(req)) {
                return dump(req, res, offset, itemSet, format);
            } else {
                return new GalleryPage(configuration, offset, itemSet).getHtml(credentials);
            }
        });
    }

    private Object itemPage(BackendConfiguration configuration, Request req, Response res, D session, ItemCredentials credentials, BackendItem item) throws IOException {
        if (req.params("id").endsWith(ID_HTML_SUFFIX)) {
            return new ImagePage(configuration, item).getHtml(credentials);
        } else {
            if (isCsvRequested(req) || isJsonRequested(req)) {
                return dump(req, res, null, ItemSet.of(item), DumpFormat.WITH_TOTAL);
            } else {
                download(res, session, credentials, item);
                return String.valueOf(res.status());
            }
        }
    }

    private boolean isCsvRequested(Request req) {
        final String acceptHeader = req.headers("Accept");
        return acceptHeader != null && acceptHeader.equals("text/csv");
    }

    private boolean isJsonRequested(Request req) {
        final String acceptHeader = req.headers("Accept");
        return acceptHeader != null && acceptHeader.equals("application/json");
    }

    private Integer requestedLimit(Request req) {
        String string = req.queryParams("limit");
        return (string == null) ? null : Integer.valueOf(string);
    }

    /** Work around what looks like Whatsapp bug: even though url encodes correctly + as %2B,
     * It is sent as raw + in the test query to construct thumbnail and reaches us as a space,
     * so we tolerate that and map spaces back to pluses, as spaces are anyway not allowed in
     * b64
     */
    private String restorePluses(String optionalKey) {
        return (optionalKey == null) ? null : optionalKey.replace(' ', '+');
    }

    private String dropHtmlSuffix(String idParam) {
        return idParam.endsWith(ID_HTML_SUFFIX) ?
            idParam.substring(0, idParam.length() - ID_HTML_SUFFIX.length()) :
                idParam;
    }

    protected Route securedItem(ItemCredentials.AuthenticationStatus authStatus, SecuredItemBody<D> task) {
        return (req, res) -> transacted(session -> {
            String key = restorePluses(req.queryParams("key"));
            String idParam = dropHtmlSuffix(req.params("id"));
            OrHttpError<? extends ItemCredentials> credentialsOrError;
            if (key != null) {
                credentialsOrError = new OrHttpError<>(new ItemCredentials.ItemKey(key));
            } else if (authStatus == ItemCredentials.AuthenticationStatus.ANONYMOUS) {
                credentialsOrError = new OrHttpError<>(new ItemCredentials.PublicAccess());
            } else {
                credentialsOrError = authenticate(req, session);
            }

            return credentialsOrError.                            get(res, credentials -> {
            /* Either we have a key, or user must be authenticated. */
            final long id = Long.parseLong(idParam);
            return loadItem(session, id, credentials)
                    .get(res, item -> task.handle(req, res, session, credentials, item));
            });
        });
    }

    /** Acquire the item with the given id. */
    protected abstract OrHttpError<? extends BackendItem> loadItem(D session, long id, ItemCredentials credentials);

    protected abstract Long upload(Request req, Response res, D session, ItemCredentials.UserPassword user, String folder, String path) throws IOException;

    public static class GalleryListData {
        public final String title;
        public final List<GalleryListPage.Element> elements;

        public GalleryListData(String title, List<GalleryListPage.Element> elements) {
            this.title = title;
            this.elements = elements;
        }
    }

    protected abstract OrHttpError<GalleryListData> galleryList(ItemCredentials credentials, D domain);

    /** Add the given comma-separated tags to the specified object. If a tag starts with '-' then it is removed instead.
     * <p>
     * NOTE: anybody can change tags of anybody's item.
     */
    protected abstract void putTags(D session, long id, String body);

    protected static String csvMetadata(String attribute, Object value) {
        return (value == null) ? null :
                (attribute +":"+ value.toString()
                        .replace("\r", "")
                        .replace("\\", "\\\\")
                        .replace("\n", "\\n")
                        +"\n");
    }

    private enum DumpFormat {
        WITH_TOTAL {
            @Override
            MetadataRepresentation dumpMetadata(Java8SearchCriteria<? extends BackendItem> criteria, ItemSet itemSet, Instant timestamp,
                                                MetadataRepresentation representation) {
                return representation.put("Total", itemSet.totalCount);
            }
        },
        WITH_TIMESTAMP {
            @Override
            MetadataRepresentation dumpMetadata(Java8SearchCriteria<? extends BackendItem> criteria, ItemSet itemSet, Instant timestamp, MetadataRepresentation representation) {
                return WITH_TOTAL.dumpMetadata(criteria, itemSet, timestamp, representation)
                        .put("Timestamp", timestamp.toEpochMilli());
            }
        },
        GALLERY_DATA {
            @Override
            MetadataRepresentation dumpMetadata(Java8SearchCriteria<? extends BackendItem> criteria, ItemSet itemSet, Instant timestamp, MetadataRepresentation representation) {
                return WITH_TOTAL.dumpMetadata(criteria, itemSet, timestamp, representation)
                        .put("Title", criteria.buildTitle())
                        .put("Description", criteria.getDescription());
            }
        };

        abstract MetadataRepresentation dumpMetadata(Java8SearchCriteria<? extends BackendItem> criteria, ItemSet itemSet, Instant timestamp, MetadataRepresentation representation);
    }

    private interface MetadataRepresentation {
        MetadataRepresentation put(String key, String value);
        MetadataRepresentation put(String key, long value);
    }

    private static class OnePerLineMetadataRepresentation implements MetadataRepresentation {
        private final StringBuilder output;

        private OnePerLineMetadataRepresentation(StringBuilder output) {
            output.append("\n"); // insert blank line before metadata to tell csv extractors the csv stream is finished
            this.output = output;
        }

        @Override
        public MetadataRepresentation put(String key, String value) {
            output.append(csvMetadata(key, value));
            return this;
        }

        @Override
        public MetadataRepresentation put(String key, long value) {
            output.append(csvMetadata(key, value));
            return this;
        }

        @Override
        public String toString() {
            return output.toString();
        }
    }

    private static class JsonMetadataRepresentation implements MetadataRepresentation {
        private final JsonObject value;

        JsonMetadataRepresentation(JsonObject value) {
            this.value = value;
        }

        @Override
        public MetadataRepresentation put(String key, String value) {
            this.value.addProperty(key, value);
            return this;
        }

        @Override
        public MetadataRepresentation put(String key, long value) {
            this.value.addProperty(key, value);
            return this;
        }

        @Override
        public String toString() {
            return this.value.toString();
        }
    }

    private Object dump(Request req, Response res, D domain, ServerSearchCriteria criteria, ItemCredentials credentials, DumpFormat dumpFormat) throws Exception {
        return getQueryLoader(domain, credentials).load(criteria).get(res, set -> dump(req, res, criteria, set, dumpFormat));
    }

    private String dump(Request req, Response res, Java8SearchCriteria<? extends BackendItem> criteria, ItemSet itemSet, DumpFormat dumpFormat) {

        Instant[] timestamp = new Instant[]{Instant.EPOCH};
        final Stream<? extends BackendItem> stream = itemSet.rows.stream()
                .peek(item -> {
                    if (item.getUpdated().isAfter(timestamp[0])) {
                        timestamp[0] = item.getUpdated();
                    }
                });
        if (isCsvRequested(req)) {
            final StringBuilder data = dumpCsv(res, stream, BackendItem.CSV);
            return dumpFormat.dumpMetadata(criteria, itemSet, timestamp[0], new OnePerLineMetadataRepresentation(data)).toString();
        } else { // json
            final JsonArray data = dumpJson(res, stream, BackendItem.CSV);
            JsonObject object = new JsonObject();
            object.add("data", data);
            return dumpFormat.dumpMetadata(criteria, itemSet, timestamp[0], new JsonMetadataRepresentation(object)).toString();
        }
    }

    private <T> StringBuilder dumpCsv(Response res, Stream<? extends T> stream, Csv<T> csv) {
        res.type("text/csv; charset=UTF-8");
        StringBuilder result = new StringBuilder(csv.header() + "\n");
        stream.forEach(item -> {
            result.append(csv.serialise(item)).append("\n");
        });
        return result;
    }

    private <T> JsonArray dumpJson(Response res, Stream<? extends T> stream, Csv<T> csv) {
        res.type("application/json; charset=UTF-8");
        JsonArray result = new JsonArray();
        stream.forEach(item -> {
            JsonObject row = new JsonObject();
            csv.toMap(item, row::addProperty);
            result.add(row);
        });
        return result;
    }

    protected abstract Object thumbnail(Response res, D session, ItemCredentials credentials, BackendItem item, int size) throws InterruptedException, IOException;

    /**
     * Download the file corresponding to the item with the given id, optionally
     * after having checked an access key.
     *
     * @param res
     *            HTTP Response used to set Forbidden status if needed
     * @param session
     *            Database connection
     * @param credentials current user credentials (permissions have already been checked so this can be ignored)
     * @param item
     *            The item to retrieve
     * @throws IOException
     */
    protected abstract void download(Response res, D session, ItemCredentials credentials, BackendItem item) throws IOException;

    protected abstract String ping();

    /** @param credentials current user credentials (permissions have already been checked so this can be ignored) */
    protected abstract ItemQueryLoader getQueryLoader(D session, ItemCredentials credentials);

    protected abstract Java8SearchCriteria<BackendItem> loadGallery(D session, long partId);

    private Object galleryListPage(Response res, BackendConfiguration configuration, D domain, ItemCredentials credentials, Request req) throws Exception {
        return galleryList(credentials, domain).get(res, data -> {
            if (isCsvRequested(req)) {
                // TODO: escape \ and \n in title
                return dumpCsv(res, data.elements.stream(), GalleryListPage.Element.CSV) + "\n" + "Title:" + data.title;
            } else {
                return new GalleryListPage(configuration, data.title, data.elements).getHtml(credentials);
            }
        });
    }

    protected interface SecuredItemBody<D extends CloudSpillEntityManagerDomain> {
        Object handle(Request request, Response response, D session, ItemCredentials credentials, BackendItem item) throws Exception;
    }
}
