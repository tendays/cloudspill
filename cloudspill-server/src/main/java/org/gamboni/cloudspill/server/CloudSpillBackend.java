package org.gamboni.cloudspill.server;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.io.ByteStreams;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.gamboni.cloudspill.domain.BackendItem;
import org.gamboni.cloudspill.domain.CloudSpillEntityManagerDomain;
import org.gamboni.cloudspill.domain.User;
import org.gamboni.cloudspill.domain.UserAuthToken;
import org.gamboni.cloudspill.server.config.BackendConfiguration;
import org.gamboni.cloudspill.server.html.AbstractPage;
import org.gamboni.cloudspill.server.html.GalleryListPage;
import org.gamboni.cloudspill.server.html.GalleryPage;
import org.gamboni.cloudspill.server.html.ImagePage;
import org.gamboni.cloudspill.server.html.LoginPage;
import org.gamboni.cloudspill.server.query.ItemQueryLoader;
import org.gamboni.cloudspill.server.query.ItemSet;
import org.gamboni.cloudspill.server.query.Java8SearchCriteria;
import org.gamboni.cloudspill.server.query.ServerSearchCriteria;
import org.gamboni.cloudspill.shared.api.CloudSpillApi;
import org.gamboni.cloudspill.shared.api.Csv;
import org.gamboni.cloudspill.shared.api.ItemCredentials;
import org.gamboni.cloudspill.shared.domain.ClientUser;
import org.gamboni.cloudspill.shared.domain.InvalidPasswordException;
import org.gamboni.cloudspill.shared.domain.IsItem;
import org.gamboni.cloudspill.shared.domain.IsUser;
import org.gamboni.cloudspill.shared.domain.Items;
import org.gamboni.cloudspill.shared.util.Log;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import spark.Request;
import spark.Response;
import spark.Route;

import static org.gamboni.cloudspill.shared.api.CloudSpillApi.ID_HTML_SUFFIX;
import static spark.Spark.after;
import static spark.Spark.before;
import static spark.Spark.get;
import static spark.Spark.post;
import static spark.Spark.put;

/** Common implementation of the {@link org.gamboni.cloudspill.shared.api.CloudSpillApi}, implemented by both {@link CloudSpillServer}
 * and {@link CloudSpillForwarder}.
 *
 * @author tendays
 */
public abstract class CloudSpillBackend<D extends CloudSpillEntityManagerDomain> extends AbstractServer<D> {
    private static final ItemCredentials.PublicAccess publicAccess = new ItemCredentials.PublicAccess();

    /* Temporary: keep tokens in memory */
    Multimap<String, UserAuthToken> tokens = HashMultimap.create();

    protected final void setupRoutes(BackendConfiguration configuration) {
        CloudSpillApi api = new CloudSpillApi("");

        /* Access logging */
        before((req, res) -> {
            Log.info(req.ip() +" "+ req.requestMethod() +" "+ req.uri());
        });

        /* Print full request processing time in HTML pages */
        before((req, res) -> AbstractPage.recordRequestStart());
        after((req, res) -> AbstractPage.clearRequestStopwatch());

        get(api.ping(), (req, res) -> transacted(session ->
                getUnverifiedCredentials(req, session)
                        .flatMap(unverifiedCredentials ->
                            (unverifiedCredentials == null) ? // no authentication headers
                                forbidden(true) :
                            ping(session, unverifiedCredentials))
                        .get(res)));

        exposeResource(api.css(), "css/main.css", "text/css");
        exposeResource(api.lazyLoadJS(), "js/lazy-load.js", "application/javascript");
        exposeResource(api.editorJS(), "js/editor.js", "application/javascript");

        get("/robots.txt", (req, res)->
                "User-agent: *\n" +
                "Disallow: /");

        get("/tag/:tag", secured((req, res, domain, credentials) ->
        {
            return galleryPage(configuration, req, res, domain, credentials,
                    ServerSearchCriteria.ALL.withTag(req.params("tag")),
                    DumpFormat.WITH_TOTAL);
        }));

        get(api.dayListPage(":year"), secured((req, res, domain, credentials) ->
            dayList(credentials, domain, Integer.parseInt(req.params("year")))
                    .get(res, galleryListFunction(req, res, configuration, credentials))));

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

        get("/", (req, res) -> title().get(res, title -> new LoginPage(configuration, title).getHtml(publicAccess)));

        /* Request a new authentication token */
        post("/user/:name/new-token", (req, res) -> transacted(session -> {
            String chars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
            final SecureRandom random = new SecureRandom();
            final String secret = random.ints(255).map(n -> chars.charAt(Math.abs(n) % (chars.length())))
                    .collect(StringBuilder::new, (builder, chr) -> builder.append((char) chr),
                            (a, b) -> {
                                throw new UnsupportedOperationException();
                            }).toString();
            UserAuthToken token = new UserAuthToken();
            token.setId(tokens.values().size()); // NOTE: remove once in database
            token.setValue(secret);
            token.setValid(false);
            final User user = session.get(User.class, req.params("name"));
            token.setUser(user);
            token.setDescription(req.headers("User-Agent") +" at "+ req.ip() +" at "+ LocalDateTime.now());
            tokens.put(req.params("name"), token);

            return new ItemCredentials.UserToken(user, token.getId(), secret).encodeLoginParam();
        }));

        /* List authentication tokens that haven't been validated yet */
        get("/user/:name/tokens", secured((req, res, session, user) ->
            UserAuthToken.CSV.header() +"\n" +
                    tokens.get(req.params("name")).stream()
                            .filter(t -> !t.getValid())
                    .map(UserAuthToken.CSV::serialise)
                    .collect(Collectors.joining("\n"))));

        /* Authorise an authentication token */
        post("/user/:name/tokens/:id/validate", secured((req, res, session, user) -> {
            final String username = req.params("name");
            // In future, one user will be allowed to validate a token for a different "guest" user
            Preconditions.checkArgument(user.user.getName().equals(username));
            final long tokenId = Long.parseLong(req.params("id"));
            final UserAuthToken token = loadToken(username, tokenId);
            token.setValid(true);
            synchronized(token) { token.notifyAll(); }
            return "ok";
        }));

        /* Wait for an authentication token to be validated */
        post(api.login(":name"), (req, res) -> transacted(session -> {
            // NOTE: using ClientUser so that Forwarder may create it after a token is validated
            final String username = req.params("name");

            ItemCredentials.UserToken credentials = new ItemCredentials.UserToken(
                    new ClientUser(username),
                    req.body());
            final UserAuthToken token = loadToken(credentials.user.getName(), credentials.id);
            if (token == null || !token.getValue().equals(credentials.secret)) {
                return forbidden(res, false);
            }
            synchronized (token) {
                /* "Long-polling": wait at most one minute */
                final long deadline = System.currentTimeMillis() + 60_000;
                while (System.currentTimeMillis() < deadline && !token.getValid()) {
                    //  max() needed in case the deadline expires right after above condition check
                    token.wait(Math.max(1, deadline - System.currentTimeMillis()));
                }
            }
            if (token.getValid()) {
                res.cookie("/",
                        LOGIN_COOKIE_NAME,
                        credentials.encodeCookie(),
                        (int)Duration.ofDays(365).getSeconds(),
                        false, // TODO configuration value. On my localhost testing it's false, elsewhere it's true
                        true);
                return "ok";
            } else {
                return "invalid";
            }
        }));
    }

    private UserAuthToken loadToken(String username, long tokenId) {
        return Iterables.find(tokens.get(username), t -> t.getId() == tokenId);
    }

    protected void verifyCredentials(ItemCredentials credentials, IsItem item) throws InvalidPasswordException {
        credentials.match(new ItemCredentials.Matcher<InvalidPasswordException>() {
            @Override
            public void when(ItemCredentials.UserPassword password) throws InvalidPasswordException {
                password.user.verifyPassword(password.getPassword());
            }

            @Override
            public void when(ItemCredentials.UserToken token) throws InvalidPasswordException {
                verifyUserToken(token.user, token.id, token.secret);
            }

            @Override
            public void when(ItemCredentials.PublicAccess pub) throws InvalidPasswordException {
                if (!Items.isPublic(item)) {
                    throw new InvalidPasswordException();
                }
            }

            @Override
            public void when(ItemCredentials.ItemKey key) throws InvalidPasswordException {
                if (!key.checksum.equals(item.getChecksum())) {
                    Log.warn("Bad key value. Expected " + item.getChecksum() + ", got " + key.checksum);
                    throw new InvalidPasswordException();
                }
            }
        });
    }

    private void exposeResource(String url, String fileName, String mime) {
        get(url, (req, res) -> {
            res.type(mime+ "; charset=UTF-8");
            ByteStreams.copy(
                    getClass().getClassLoader().getResourceAsStream(fileName),
                    res.raw().getOutputStream());
            return "";
        });
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
                return new GalleryPage(configuration, offset, itemSet, req.queryParamOrDefault("experimental", "").equals("true")).getHtml(credentials);
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

    protected abstract Long upload(Request req, Response res, D session, ItemCredentials.UserCredentials user, String folder, String path) throws IOException;

    @Override
    protected void verifyUserToken(IsUser user, long id, String secret) throws InvalidPasswordException {
        final UserAuthToken userAuthToken = loadToken(user.getName(), id);

        if (!userAuthToken.getValid() || !userAuthToken.getValue().equals(secret)) {
            throw new InvalidPasswordException();
        }
    }

    public static class GalleryListData {
        public final String title;
        public final List<GalleryListPage.Element> elements;

        public GalleryListData(String title, List<GalleryListPage.Element> elements) {
            this.title = title;
            this.elements = elements;
        }
    }

    protected abstract OrHttpError<GalleryListData> galleryList(ItemCredentials credentials, D domain);

    protected abstract OrHttpError<String> title();

    protected abstract OrHttpError<GalleryListData> dayList(ItemCredentials credentials, D domain, int year);

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
                    if (item.getUpdated() != null && item.getUpdated().isAfter(timestamp[0])) {
                        timestamp[0] = item.getUpdated();
                    }
                });
        if (isJsonRequested(req)) {
            final JsonArray data = dumpJson(res, stream, BackendItem.CSV);
            JsonObject object = new JsonObject();
            object.add("data", data);
            // galleries do not yet support metadata in forwarder, so disabling that for now
            //return dumpFormat.dumpMetadata(criteria, itemSet, timestamp[0], new JsonMetadataRepresentation(object)).toString();
            return object.toString();
        } else { // csv
            final StringBuilder data = dumpCsv(res, stream, BackendItem.CSV);
            return dumpFormat.dumpMetadata(criteria, itemSet, timestamp[0], new OnePerLineMetadataRepresentation(data)).toString();
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

    protected abstract OrHttpError<String> ping(D session, ItemCredentials.UserCredentials credentials);

    /** @param credentials current user credentials (permissions have already been checked so this can be ignored) */
    protected abstract ItemQueryLoader getQueryLoader(D session, ItemCredentials credentials);

    protected abstract Java8SearchCriteria<BackendItem> loadGallery(D session, long partId);

    private OrHttpError.ItemConsumer<GalleryListData> galleryListFunction(Request req, Response res, BackendConfiguration configuration, ItemCredentials credentials) {
        return data -> {
                    if (isCsvRequested(req)) {
                        // TODO: escape \ and \n in title
                        return dumpCsv(res, data.elements.stream(), GalleryListPage.Element.CSV) + "\n" + "Title:" + data.title;
                    } else {
                        return new GalleryListPage(configuration, data.title, data.elements).getHtml(credentials);
                    }
                };
    }

    private Object galleryListPage(Response res, BackendConfiguration configuration, D domain, ItemCredentials credentials, Request req) throws Exception {
        return galleryList(credentials, domain).get(res, galleryListFunction(req, res, configuration, credentials));
    }

    protected interface SecuredItemBody<D extends CloudSpillEntityManagerDomain> {
        Object handle(Request request, Response response, D session, ItemCredentials credentials, BackendItem item) throws Exception;
    }
}
