package org.gamboni.cloudspill.server;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.gamboni.cloudspill.domain.BackendItem;
import org.gamboni.cloudspill.domain.CloudSpillEntityManagerDomain;
import org.gamboni.cloudspill.domain.Item;
import org.gamboni.cloudspill.domain.User;
import org.gamboni.cloudspill.domain.UserAuthToken;
import org.gamboni.cloudspill.server.config.BackendConfiguration;
import org.gamboni.cloudspill.server.html.AbstractPage;
import org.gamboni.cloudspill.server.html.GalleryListPage;
import org.gamboni.cloudspill.server.html.GalleryPage;
import org.gamboni.cloudspill.server.html.ImagePage;
import org.gamboni.cloudspill.server.html.LabPage;
import org.gamboni.cloudspill.server.html.LoginPage;
import org.gamboni.cloudspill.server.query.ItemQueryLoader;
import org.gamboni.cloudspill.server.query.ItemSet;
import org.gamboni.cloudspill.server.query.Java8SearchCriteria;
import org.gamboni.cloudspill.server.query.ServerSearchCriteria;
import org.gamboni.cloudspill.shared.api.ApiElementMatcher;
import org.gamboni.cloudspill.shared.api.CloudSpillApi;
import org.gamboni.cloudspill.shared.api.Csv;
import org.gamboni.cloudspill.shared.api.ItemCredentials;
import org.gamboni.cloudspill.shared.api.ItemMetadata;
import org.gamboni.cloudspill.shared.api.LoginState;
import org.gamboni.cloudspill.shared.domain.AccessDeniedException;
import org.gamboni.cloudspill.shared.domain.InvalidPasswordException;
import org.gamboni.cloudspill.shared.domain.IsItem;
import org.gamboni.cloudspill.shared.domain.IsUser;
import org.gamboni.cloudspill.shared.domain.ItemType;
import org.gamboni.cloudspill.shared.domain.Items;
import org.gamboni.cloudspill.shared.domain.PermissionDeniedException;
import org.gamboni.cloudspill.shared.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.persistence.Query;
import javax.servlet.MultipartConfigElement;
import javax.servlet.http.Part;

import spark.Request;
import spark.Response;
import spark.Route;
import spark.Spark;

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

    protected final void setupRoutes(BackendConfiguration configuration) {
        CloudSpillApi<Route> api = new CloudSpillApi<>("", (method, url, route) -> {
            if (method == ApiElementMatcher.HttpMethod.GET) {
                get(url, route);
            } else if (method == ApiElementMatcher.HttpMethod.POST) {
                post(url, route);
            } else if (method == ApiElementMatcher.HttpMethod.PUT) {
                put(url, route);
            } else {
                throw new UnsupportedOperationException(method.toString());
            }
        });

        /* Access logging */
        before((req, res) -> {
            Log.info(req.headers("User-Agent") +" @"+ req.ip() +" "+ req.requestMethod() +" "+ req.uri());
        });

        /* Print full request processing time in HTML pages */
        before((req, res) -> AbstractPage.recordRequestStart());
        after((req, res) -> AbstractPage.clearRequestStopwatch());

        Spark.exception(Exception.class, (exception, req, res) -> {
            Log.error("Uncaught exception handling "+ req.requestMethod() +" "+ req.uri(), exception);
            res.status(500);
            res.body("500 Internal Server Error");
        });

        api.ping((req, res) -> transacted(session ->
                getUnverifiedCredentials(req, session)
                        .flatMap(unverifiedCredentials ->
                            (unverifiedCredentials == null) ? // no authentication headers
                                forbidden(true) :
                            ping(session, unverifiedCredentials))
                        .get(res)));

        exposeResource(api.css(), "css/main.css", "text/css");
        exposeResource(api.lazyLoadJS(), "js/lazy-load.js", "application/javascript");
        exposeResource(api.editorJS(), "js/editor.js", "application/javascript");
        exposeResource(api.loginJS(), "js/login.js", "application/javascript");
        exposeResource(api.uploadJS(), "js/upload.js", "application/javascript");

        get("/robots.txt", (req, res)->
                "User-agent: *\n" +
                "Disallow: /");

        get(api.knownTags(), secured((req, res, domain, credentials) -> Lists.transform(tagList(domain),
                this::toJsonString
        )));
        get("/tag/:tag", secured((req, res, domain, credentials) -> galleryPage(configuration, req, res, domain, credentials,
                    ServerSearchCriteria.ALL.withTag(req.params("tag")),
                    DumpFormat.WITH_TOTAL)));

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
            return itemPage(configuration, req, res, session, credentials, item).toString();
        }));

        /* Download a public file */
        get("/public/item/:id", securedItem(ItemCredentials.AuthenticationStatus.ANONYMOUS, (req, res, session, credentials, item) -> {
                return itemPage(configuration, req, res, session, credentials, item).toString();
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
        api.putTags(":id", secured((req, res, session, user) -> {
            Log.debug("Tag query for item "+ req.params("id") +": '"+ req.body() +"'");
            putTags(session, Long.parseLong(req.params("id")), req.body(), user);
            return true;
        }));

        api.setItemDescription(":id", secured((req, res, session, user) -> {
            setItemDescription(session, Long.parseLong(req.params("id")), req.body(), user);
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

            final String timestampHeader = req.headers(CloudSpillApi.UPLOAD_TIMESTAMP_HEADER);
            Date timestamp = (timestampHeader == null) ? null : new Date(Long.valueOf(timestampHeader));
            final String typeHeader = req.headers(CloudSpillApi.UPLOAD_TYPE_HEADER);

            ItemType itemType = null;
            if (typeHeader != null) {
                try {
                    itemType = ItemType.valueOf(typeHeader);
                } catch (IllegalArgumentException e) {
                    Log.warn("Received invalid item type "+ typeHeader);
                    // Then just leave it blank
                }
            }

            return upload(session, credentials, req.raw().getInputStream(), folder, path, new ItemMetadata(timestamp, itemType)).get(res);
        }));

        get("/", (req, res) -> title().get(res, title -> transacted(session -> getUnverifiedCredentials(req, session)).map(credentials ->
        {
            final LoginPage loginPage = credentials == null ? new LoginPage(configuration, title, LoginState.DISCONNECTED, new ItemCredentials.PublicAccess()) :
                    credentials.map(new ItemCredentials.Mapper<LoginPage>() {
                        @Override
                        public LoginPage when(ItemCredentials.UserPassword password) {
                            try {
                                verifyCredentials(password, null);
                            } catch (AccessDeniedException e) {
                                /* Wrong password supplied */
                                return new LoginPage(configuration, title, LoginState.DISCONNECTED, new ItemCredentials.PublicAccess());
                            }

                            return new LoginPage(configuration, title, LoginState.LOGGED_IN, password);
                        }

                        @Override
                        public LoginPage when(ItemCredentials.UserToken token) {
                            return new LoginPage(configuration, title, getUserTokenState(token.user, token.id, token.secret), token);
                        }

                        @Override
                        public LoginPage when(ItemCredentials.PublicAccess pub) {
                            return new LoginPage(configuration, title, LoginState.DISCONNECTED, credentials);
                        }

                        @Override
                        public LoginPage when(ItemCredentials.ItemKey key) {
                            return new LoginPage(configuration, title, LoginState.DISCONNECTED, credentials);

                        }
                    });
            return loginPage.toString();
        })
            .get(res)));

        /* Login, step 1: request a new authentication token */
        post(api.newToken(":name"), (req, res) -> {
            final String forwarded = req.headers("X-Forwarded-For");
            return newToken(
                    req.params("name"),
                    req.headers("User-Agent"),
                            (forwarded == null ? req.ip() :
                            (forwarded +" (connecting through "+ req.ip() +")")))
                    .get(res, token -> {
                        /* "Lax" SameSite to allow people to link to pages. We don't do state changes in GET requests. */
                        res.header("Set-Cookie", LOGIN_COOKIE_NAME +"="+ token.encodeCookie() +
                                "; Path=/; Max-Age="+ (int)Duration.ofDays(365).getSeconds() +"; HttpOnly"+
                                (configuration.insecureAuthentication() ? "" : "; Secure")+"; SameSite=Lax");

                        return token.encodeLoginParam();
                    });
        });

        /* Login, step 2: wait for an authentication token to be validated */
        post(api.login(), (req, res) ->  transacted(session -> getUnverifiedCredentials(req, session))
                .flatMap(credentials ->
                    (credentials == null ?
                            badRequest() :
                            credentials.map(new ItemCredentials.Mapper<OrHttpError<LoginState>>() {
                                @Override
                                public OrHttpError<LoginState> when(ItemCredentials.UserPassword password) {
                                    // Basic authentication shouldn't use login() calls...
                                    return badRequest();
                                }

                                @Override
                                public OrHttpError<LoginState> when(ItemCredentials.UserToken token) {
                                    String username = token.user.getName();
                                    Log.debug("Handling login request for " + username + " with credentials " +
                                            extract(token.secret));

                                    return login(token);
                                }

                                @Override
                                public OrHttpError<LoginState> when(ItemCredentials.PublicAccess pub) {
                                    return badRequest();
                                }

                                @Override
                                public OrHttpError<LoginState> when(ItemCredentials.ItemKey key) {
                                    return badRequest();
                                }
                            })))
                    .get(res, LoginState::name));

        post(api.logout(), (req, res) -> transacted(session ->
            getUnverifiedCredentials(req, session).flatMap(credentials -> {
                if (credentials == null) {
                    Log.error("logout without credentials");
                    return badRequest();
                }

                return credentials.map(new ItemCredentials.Mapper<OrHttpError<String>>() {
                    @Override
                    public OrHttpError<String> when(ItemCredentials.UserPassword password) {
                        return badRequest();
                    }

                    @Override
                    public OrHttpError<String> when(ItemCredentials.UserToken token) {
                        return logout(session, token);
                    }

                    @Override
                    public OrHttpError<String> when(ItemCredentials.PublicAccess pub) {
                        return badRequest();
                    }

                    @Override
                    public OrHttpError<String> when(ItemCredentials.ItemKey key) {
                        return badRequest();
                    }
                });
            })).get(res, v -> {
            res.removeCookie("/", LOGIN_COOKIE_NAME);
            return v;
        }));

        /* List authentication tokens that haven't been validated yet */
        get(api.listInvalidTokens(":name"), secured((req, res, session, user) ->
                listInvalidTokens(session, user).get(res, tokens ->
                        UserAuthToken.CSV.header() +"\n" +
                                tokens.stream()
                                        .map(UserAuthToken.CSV::serialise)
                                        .collect(Collectors.joining("\n")))));

        /* Authorise an authentication token */
        post("/user/:name/tokens/:id/validate", secured((req, res, session, user) -> {
            final String username = req.params("name");
            if (!user.user.getName().equals(username)) {
                // admins can validate tokens for other users
                user.user.verifyGroup(User.ADMIN_GROUP);
            }
            final long tokenId = Long.parseLong(req.params("id"));
            return validateToken(session, username, tokenId).get(res);
        }));

        /* Page for experimenting new stuff */
        get("/lab", secured((req, res, session, user) -> new LabPage(configuration, user)));
        post("/lab", secured((req, res, session, user) -> {
            req.attribute("org.eclipse.jetty.multipartConfig", new MultipartConfigElement("/tmp"));
            Set<Long> ids = new LinkedHashSet<>();
            for (Part part : req.raw().getParts()) {
                if (part.getName().equals("files[]") && part.getSize() > 0) {
                    final OrHttpError<Long> newId = upload(session, user, part.getInputStream(), "web", part.getSubmittedFileName(),
                            new ItemMetadata(null,
                            ItemType.fromMime(part.getContentType())));
                    newId.ifPresent(ids::add);
                } else {
                    Log.info("Skipping "+ part);
                }
            }
            return ids;
        }));
    }

    private String toJsonString(String string) {
        return "\""+ string.replace("\\", "\\\\").replace("\"", "\\\"") +"\"";
    }

    /** Input: "abcdefghijklmnopqrstuvwxyz". Output: "abc...xyz". */
    private String extract(String text) {
        if (text == null || text.length() <= 6) {
            return text;
        } else {
            return text.substring(0, 3) +"..."+ text.substring(text.length() - 3);
        }
    }

    protected abstract OrHttpError<Object> validateToken(D session, String username, long tokenId);

    protected abstract OrHttpError<LoginState> login(ItemCredentials.UserToken credentials);

    protected abstract OrHttpError<String> logout(D session, ItemCredentials.UserToken credentials);

    protected abstract OrHttpError<List<UserAuthToken>> listInvalidTokens(D session, ItemCredentials.UserCredentials user);

    protected abstract OrHttpError<ItemCredentials.UserToken> newToken(String username, String userAgent, String client);

    protected void verifyCredentials(ItemCredentials credentials, IsItem item) throws AccessDeniedException {
        credentials.match(new ItemCredentials.Matcher<AccessDeniedException>() {
            @Override
            public void when(ItemCredentials.UserPassword password) throws AccessDeniedException {
                password.user.verifyPassword(password.getPassword());
                authorise(password.user, item);
            }

            @Override
            public void when(ItemCredentials.UserToken token) throws AccessDeniedException {
                final LoginState state = getUserTokenState(token.user, token.id, token.secret);
                if (state != LoginState.LOGGED_IN) {
                    throw new InvalidPasswordException();
                }
                authorise(token.user, item);
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

    protected void authorise(IsUser user, IsItem item) throws PermissionDeniedException {
        if (item != null && !item.getUser().equals(user.getName()) && !Items.isPublic(item)) {
            user.verifyGroup(User.ADMIN_GROUP);
        }
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
                return new GalleryPage(configuration, offset, itemSet, req.queryParamOrDefault("experimental", "").equals("true"),
                        credentials).toString();
            }
        });
    }

    private Object itemPage(BackendConfiguration configuration, Request req, Response res, D session, ItemCredentials credentials, BackendItem item) throws IOException {
        if (req.params("id").endsWith(ID_HTML_SUFFIX)) {
            User user = session.get(User.class, item.getUser());
            return new ImagePage(configuration, item, user, credentials);
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
            OrHttpError<List<ItemCredentials>> credentialsOrError =
            optionalAuthenticate(req, session)
                    .map(login -> {
                        List<ItemCredentials> credentials = new ArrayList<>();
                        if (login != null) {
                            credentials.add(login);
                        }
                        if (key != null) {
                            credentials.add(new ItemCredentials.ItemKey(key));
                        }
                        if (authStatus == ItemCredentials.AuthenticationStatus.ANONYMOUS) {
                            credentials.add(new ItemCredentials.PublicAccess());
                        }
                        return credentials;
                    });

            return credentialsOrError.get(res, credentials -> {
            /* Either we have a key, or user must be authenticated. */
            final long id = Long.parseLong(idParam);

                final Optional<ItemCredentials> mostPowerful = credentials.stream().max(Comparator.comparing(ItemCredentials::getPower));

                if (!mostPowerful.isPresent()) {
                    return forbidden(res, true);
                }

                return loadItem(session, id, credentials)
                    .get(res, item -> task.handle(req, res, session, mostPowerful.get(), item));
            });
        });
    }

    /** Acquire the item with the given id. If multiple credentials are provided, they must all be valid. If no credentials
     * are provided, access must be denied. */
    protected abstract OrHttpError<? extends BackendItem> loadItem(D session, long id, List<ItemCredentials> credentials);

    protected abstract OrHttpError<Long> upload(D session, ItemCredentials.UserCredentials user, InputStream inputStream,
                                   String folder, String path,
                                   ItemMetadata metadata) throws IOException;

    public static class GalleryListData {
        public final String title;
        public final List<GalleryListPage.Element> elements;

        public GalleryListData(String title, List<GalleryListPage.Element> elements) {
            this.title = title;
            this.elements = elements;
        }
    }

    protected abstract OrHttpError<GalleryListData> galleryList(ItemCredentials credentials, D domain);

    protected List<String> tagList(D domain) {
        final Query query = domain.getEntityManager().createNativeQuery(
                "select distinct tags from Item_tags order by tags");
        return (List<String>) query.getResultList();
    }

    protected abstract OrHttpError<String> title();

    protected abstract OrHttpError<GalleryListData> dayList(ItemCredentials credentials, D domain, int year);

    /** Add the given comma-separated tags to the specified object. If a tag starts with '-' then it is removed instead.
     * <p>
     * NOTE: anybody can change tags of anybody's item.
     */
    protected void putTags(D session, long id, String tags, ItemCredentials credentials) throws IOException {
        final BackendItem item = itemForUpdate(session, id);

        final Set<String> existingTags = item.getTags();
        Splitter.on(',').split(tags).forEach(t -> {
            if (t.startsWith("-")) {
                existingTags.remove(t.substring(1).trim());
            } else {
                existingTags.add(t.trim());
            }
        });
    }

    protected void setItemDescription(D session, long id, String description, ItemCredentials credentials) throws IOException {
        final BackendItem item = itemForUpdate(session, id);
        item.setDescription(description);
    }

    private BackendItem itemForUpdate(D session, long id) {
        final CloudSpillEntityManagerDomain.Query<? extends BackendItem> itemQuery = session.selectItem();
        final BackendItem item = Iterables.getOnlyElement(
                itemQuery.add(root -> session.criteriaBuilder.equal(root.get("id"), id)).forUpdate().list());
        Log.debug("Loaded item "+ id +" for update, at timestamp "+ item.getUpdated().toString());
        session.reload(item);
        Log.debug("After reload, item "+ id +" has timestamp "+ item.getUpdated().toString());
        return item;
    }


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
                        return new GalleryListPage(configuration, data.title, data.elements, credentials).toString();
                    }
                };
    }

    private Object galleryListPage(Response res, BackendConfiguration configuration, D domain, ItemCredentials credentials, Request req) throws Exception {
        try {
            return galleryList(credentials, domain).get(res, galleryListFunction(req, res, configuration, credentials));
        } catch (Throwable e) {
            e.printStackTrace();
            throw e;
        }
    }

    protected interface SecuredItemBody<D extends CloudSpillEntityManagerDomain> {
        Object handle(Request request, Response response, D session, ItemCredentials credentials, BackendItem item) throws Exception;
    }
}
