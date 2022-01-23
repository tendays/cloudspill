package org.gamboni.cloudspill.server;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.gamboni.cloudspill.domain.BackendItem;
import org.gamboni.cloudspill.domain.CloudSpillEntityManagerDomain;
import org.gamboni.cloudspill.domain.User;
import org.gamboni.cloudspill.domain.UserAuthToken;
import org.gamboni.cloudspill.server.config.BackendConfiguration;
import org.gamboni.cloudspill.server.html.AbstractRenderer;
import org.gamboni.cloudspill.server.html.GalleryListPage;
import org.gamboni.cloudspill.server.html.GalleryPage;
import org.gamboni.cloudspill.server.html.ImagePage;
import org.gamboni.cloudspill.server.html.LoginPage;
import org.gamboni.cloudspill.server.html.TokenListPage;
import org.gamboni.cloudspill.server.html.js.AbstractJs;
import org.gamboni.cloudspill.server.html.js.CommentSubmissionJs;
import org.gamboni.cloudspill.server.html.js.EditorSubmissionJs;
import org.gamboni.cloudspill.server.html.js.TokenValidationJs;
import org.gamboni.cloudspill.server.query.ItemQueryLoader;
import org.gamboni.cloudspill.server.query.ItemSet;
import org.gamboni.cloudspill.server.query.Java8SearchCriteria;
import org.gamboni.cloudspill.server.query.ServerSearchCriteria;
import org.gamboni.cloudspill.shared.api.ApiElementMatcher;
import org.gamboni.cloudspill.shared.api.CloudSpillApi;
import org.gamboni.cloudspill.shared.api.Csv;
import org.gamboni.cloudspill.shared.api.ItemCredentials;
import org.gamboni.cloudspill.shared.api.ItemMetadata;
import org.gamboni.cloudspill.shared.api.ItemSecurity;
import org.gamboni.cloudspill.shared.api.LoginState;
import org.gamboni.cloudspill.shared.api.MassTagging;
import org.gamboni.cloudspill.shared.api.NewComment;
import org.gamboni.cloudspill.shared.domain.AccessDeniedException;
import org.gamboni.cloudspill.shared.domain.Comment;
import org.gamboni.cloudspill.shared.domain.InvalidPasswordException;
import org.gamboni.cloudspill.shared.domain.IsItem;
import org.gamboni.cloudspill.shared.domain.IsUser;
import org.gamboni.cloudspill.shared.domain.ItemType;
import org.gamboni.cloudspill.shared.domain.Items;
import org.gamboni.cloudspill.shared.domain.PermissionDeniedException;
import org.gamboni.cloudspill.shared.query.QueryRange;
import org.gamboni.cloudspill.shared.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
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
import static spark.Spark.delete;
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


    protected final CloudSpillApi<Route> api = new CloudSpillApi<>("", (method, url, route) -> {
        if (method == ApiElementMatcher.HttpMethod.GET) {
            get(url, route);
        } else if (method == ApiElementMatcher.HttpMethod.POST) {
            post(url, route);
        } else if (method == ApiElementMatcher.HttpMethod.PUT) {
            put(url, route);
        } else if (method == ApiElementMatcher.HttpMethod.DELETE) {
            delete(url, route);
        } else {
            throw new UnsupportedOperationException(method.toString());
        }
    });

    protected static class AbstractGalleryRequestModel {
        final QueryRange range;
        final Long relativeTo;
        final ItemCredentials itemCredentials;
        final boolean experimental;

        public AbstractGalleryRequestModel(QueryRange range, Long relativeTo, ItemCredentials itemCredentials, boolean experimental) {
            this.range = range;
            this.relativeTo = relativeTo;
            this.itemCredentials = itemCredentials;
            this.experimental = experimental;
        }
    }

    public static class BadRequestException extends RuntimeException {}

    protected final void setupRoutes(BackendConfiguration configuration) {

        /* Request Model: just the year as an int */
        final Serialiser<GalleryListPage.Model> galleryListSerialiser = (data, ct) -> {
            Preconditions.checkArgument(ct == ContentType.CSV, "Content type " + ct + " not supported");
            return dumpCsv(data.elements.stream(), GalleryListPage.Element.CSV) + "\n" + "Title:" + data.title;
        };

        /* Access logging */
        before((req, res) -> {
            Log.info(req.headers("User-Agent") +" @"+ req.ip() +" "+ req.requestMethod() +" "+ req.uri());
        });

        /* Print full request processing time in HTML pages */
        before((req, res) -> AbstractRenderer.recordRequestStart());
        after((req, res) -> AbstractRenderer.clearRequestStopwatch());

        before((req, res) -> {
            Csrf.handleRequest(configuration, req, res);
        });

        Spark.exception(Exception.class, (exception, req, res) -> {
            if (exception instanceof BadRequestException) {
                res.body(badRequest(res));
            } else {
                Log.error("Uncaught exception handling " + req.requestMethod() + " " + req.uri(), exception);
                res.status(500);
                res.body("500 Internal Server Error");
            }
        });

        api.ping((req, res) -> transacted(session ->
                getUnverifiedCredentials(req, session)
                        .flatMap(unverifiedCredentials ->
                            (unverifiedCredentials == null) ? // no authentication headers
                                forbidden(true) :
                            ping(session, unverifiedCredentials))
                        .get(res)));

        expose(new EditorSubmissionJs(configuration));
        expose(new TokenValidationJs(configuration));
        expose(new CommentSubmissionJs(configuration));

        exposeResource(api.css(), "css/main.css", "text/css");
        exposeResource(api.galleryJS(), "js/gallery.js", "application/javascript");
        exposeResource(api.commentsJS(), "js/comments.js", "application/javascript");
        exposeResource(api.tagwidgetJS(), "js/tagwidget.js", "application/javascript");
        exposeResource(api.editorJS(), "js/editor.js", "application/javascript");
        exposeResource(api.loginJS(), "js/login.js", "application/javascript");
        exposeResource(api.uploadJS(), "js/upload.js", "application/javascript");
        exposeResource(api.tokenListJS(), "js/token-list.js", "application/javascript");

        get("/robots.txt", (req, res)->
                "User-agent: *\n" +
                "Disallow: /");

        get(api.knownTags(), secured((req, res, domain, credentials) -> Lists.transform(tagList(domain),
                this::toJsonString
        )));


        class TagGalleryRequestModel extends AbstractGalleryRequestModel {
            final String tag;

            TagGalleryRequestModel(String tag, Long relativeTo, ItemCredentials itemCredentials, QueryRange range, boolean experimental) {
                super(range, relativeTo, itemCredentials, experimental);
                this.tag = tag;
            }
        }

        api.tagView(securedPage(req -> null,
                (nothing, credentials, domain) -> tagGalleryList(credentials, domain),
                galleryListSerialiser,
                new GalleryListPage(configuration)));
        api.tagView(":tag", securedPage(
                req -> {
                    final QueryRange range = requestedRange(req);
                    return new TagGalleryRequestModel(req.params("tag"),
                            nullableLong(req.queryParams("relativeTo")),
                            itemKeyFromRequest(req),
                            (isCsvRequested(req) || isJsonRequested(req)) ?
                                    range : range.withLimit(GalleryPage.PAGE_SIZE),
                            req.queryParamOrDefault("experimental", "").equals("true"));
                },
                (model, credentials, domain) -> {
                    Java8SearchCriteria<BackendItem> offset = ServerSearchCriteria.ALL.withTag(model.tag)
                            .relativeTo(model.relativeTo, MoreObjects.firstNonNull(model.itemCredentials, credentials))
                            .withRange(model.range);

                    return getQueryLoader(domain, credentials)
                            .load(offset)
                            .map(itemSet ->
                                    new GalleryPage.Model(credentials, offset, itemSet, model.experimental));
                },
                /* serialiser */
                (data, ct) -> dump(data, ct, DumpFormat.GALLERY_DATA),
                /* renderer */
                new GalleryPage(configuration)));
        get("/tag/:tag/:id", securedItem(ItemCredentials.AuthenticationStatus.LOGGED_IN, (req, res, session, credentials, item) -> {
            final String tag = req.params("tag");
            return itemPage(configuration, req, res, session, credentials, item, () -> ServerSearchCriteria.ALL.withTag(tag))
                    .get(res).toString();
        }));

        api.yearView(":year", securedPage(
                req -> Integer.parseInt(req.params("year")),
                (model, credentials, domain) -> dayList(credentials, domain, model),
                galleryListSerialiser,
                    new GalleryListPage(configuration)));

        class DayGalleryRequestModel extends AbstractGalleryRequestModel {
            final LocalDate day;

            DayGalleryRequestModel(LocalDate day, QueryRange range, Long relativeTo, ItemCredentials itemCredentials, boolean experimental) {
                super(range, relativeTo, itemCredentials, experimental);
                this.day = day;
            }
        }
        api.dayView(":day", securedPage(
                req -> {
                    final QueryRange range = requestedRange(req);
                    return new DayGalleryRequestModel(LocalDate.parse(req.params("day")),
                            (isCsvRequested(req) || isJsonRequested(req)) ?
                                                        range : range.withLimit(GalleryPage.PAGE_SIZE),
                            nullableLong(req.queryParams("relativeTo")),
                                                itemKeyFromRequest(req),
                                                req.queryParamOrDefault("experimental", "").equals("true"));
                },
                (model, credentials, domain) -> {
                    Java8SearchCriteria<BackendItem> offset = ServerSearchCriteria.ALL.at(model.day)
                            .relativeTo(model.relativeTo, MoreObjects.firstNonNull(model.itemCredentials, credentials))
                            .withRange(model.range);

                    return getQueryLoader(domain, credentials)
                            .load(offset)
                            .map(itemSet ->
                                    new GalleryPage.Model(credentials, offset, itemSet, model.experimental));
                },
                /* serialiser */
                (data, ct) -> dump(data, ct, DumpFormat.GALLERY_DATA),
                /* renderer */
                new GalleryPage(configuration)));
        get("/day/:day/:id", securedItem(ItemCredentials.AuthenticationStatus.LOGGED_IN, (req, res, session, credentials, item) -> {
            final LocalDate day = LocalDate.parse(req.params("day"));
            return itemPage(configuration, req, res, session, credentials, item, () -> ServerSearchCriteria.ALL.at(day))
                    .get(res).toString();
        }));

        api.galleryListView(new ItemCredentials.UserPassword(), securedPage(
                req -> null, // nothing in request model
                (model, credentials, domain) -> galleryList(credentials, domain),
                galleryListSerialiser,
                new GalleryListPage(configuration)));

        api.galleryListView(publicAccess, page(
                req -> null, // nothing in request model
                (model, credentials, domain) -> galleryList(publicAccess, domain),
                galleryListSerialiser,
                new GalleryListPage(configuration)));

        class GalleryRequestModel extends AbstractGalleryRequestModel {
            final long partId;
            final String key;
            GalleryRequestModel(long partId, String key, QueryRange range, Long relativeTo, ItemCredentials itemCredentials, boolean experimental) {
                super(range, relativeTo, itemCredentials, experimental);
                this.partId = partId;
                this.key = key;
            }
        }
        api.galleryPart(":part", null, null, null, QueryRange.ALL, page(
                /* request parser */
                req -> {
                    final QueryRange range = requestedRange(req);
                    return new GalleryRequestModel(
                            Long.parseLong(req.params("part")),
                            req.queryParams("gkey"),
                            (isCsvRequested(req) || isJsonRequested(req)) ?
                                    range : range.withLimit(GalleryPage.PAGE_SIZE),
                            nullableLong(req.queryParams("relativeTo")),
                            itemKeyFromRequest(req),
                            req.queryParamOrDefault("experimental", "").equals("true"));
                },
                /* executor */
                (model, credentials, domain) -> {
                    Java8SearchCriteria<BackendItem> offset = loadGallery(domain, model.partId, model.key)
                            .relativeTo(model.relativeTo, MoreObjects.firstNonNull(model.itemCredentials, credentials))
                            .withRange(model.range);

                    return getQueryLoader(domain, credentials)
                            .load(offset)
                            .map(itemSet ->
                                    new GalleryPage.Model(credentials, offset, itemSet, model.experimental));
                },
                /* serialiser */
                (data, ct) -> dump(data, ct, DumpFormat.GALLERY_DATA),
                /* renderer */
                new GalleryPage(configuration)
        ));

        get("/public/gallery/:part/:id", securedItem(ItemCredentials.AuthenticationStatus.ANONYMOUS, (req, res, session, credentials, item) -> {
            final long partId = Long.parseLong(req.params("part"));
            final String key = req.queryParams("gkey");
            return itemPage(configuration, req, res, session, credentials, item, () -> loadGallery(session, partId, key))
                    .get(res).toString();
        }));

        final Route publicPage = page(
                req -> {
                    final QueryRange range = requestedRange(req);
                    return new AbstractGalleryRequestModel(
                            (isCsvRequested(req) || isJsonRequested(req)) ?
                                    range : range.withLimit(GalleryPage.PAGE_SIZE),
                            nullableLong(req.queryParams("relativeTo")),
                            itemKeyFromRequest(req),
                            req.queryParamOrDefault("experimental", "").equals("true"));
                },
                (model, credentials, domain) -> {
                    Java8SearchCriteria<BackendItem> offset = ServerSearchCriteria.ALL.withTag("public")
                            .relativeTo(model.relativeTo, MoreObjects.firstNonNull(model.itemCredentials, credentials))
                            .withRange(model.range);

                    return getQueryLoader(domain, credentials)
                            .load(offset)
                            .map(itemSet ->
                                    new GalleryPage.Model(credentials, offset, itemSet, model.experimental));
                },
                /* serialiser */
                (data, ct) -> dump(data, ct, DumpFormat.WITH_TOTAL),
                /* renderer */
                new GalleryPage(configuration));

        get("/public", publicPage);
        get("/public/", publicPage);

        /* Download a file */
        get("/item/:id", securedItem(ItemCredentials.AuthenticationStatus.LOGGED_IN, (req, res, session, credentials, item) -> {
            return itemPage(configuration, req, res, session, credentials, item, null).get(res).toString();
        }));

        /* Download a public file */
        get("/public/item/:id", securedItem(ItemCredentials.AuthenticationStatus.ANONYMOUS, (req, res, session, credentials, item) -> {
                return itemPage(configuration, req, res, session, credentials, item, null).get(res).toString();
        }));

        /* Download a thumbnail */
        get("/thumbs/:size/:id", securedItem(ItemCredentials.AuthenticationStatus.LOGGED_IN, (req, res, session, credentials, item) -> {
            thumbnail(res, session, ItemSecurity.mostPowerful(credentials), item, Integer.parseInt(req.params("size")));
            return "";
        }));

        /* Download a thumbnail */
        get("/public/thumbs/:size/:id", securedItem(ItemCredentials.AuthenticationStatus.ANONYMOUS, (req, res, session, credentials, item) -> {
            thumbnail(res, session, ItemSecurity.mostPowerful(credentials), item, Integer.parseInt(req.params("size")));
            return "";
        }));

        /* Get list of items whose id is larger than the given one. */
        get("item/since/:id", secured((req, res, domain, credentials) -> {
            ContentType ct = isJsonRequested(req)? ContentType.JSON : ContentType.CSV;
            res.type(ct.mime);
            return dump(domain, ServerSearchCriteria.ALL.withIdAtLeast(Long.parseLong(req.params("id"))), credentials, ct,
                    DumpFormat.WITH_TOTAL).get(res);
        }));

        /* Get list of items updated at or later than the given timestamp. */
        get(api.getItemsSinceUrl(":date"), secured((req, res, domain, credentials) -> {
            ContentType ct = isJsonRequested(req)? ContentType.JSON : ContentType.CSV;
            res.type(ct.mime);
            return dump(domain,
                    ServerSearchCriteria.ALL.modifiedSince(Instant.ofEpochMilli(Long.parseLong(req.params("date")))),
                    credentials,
                ct,
                DumpFormat.WITH_TIMESTAMP).get(res);
        }));

        /* Add the tags specified in body to the given item. */
        api.putTags(":id", secured((req, res, session, user) -> {
            Log.debug("Tag query for item "+ req.params("id") +": '"+ req.body() +"'");
            MassTagging dto = new MassTagging();
            dto.ids = ImmutableList.of(Long.parseLong(req.params("id")));
            dto.tags = req.body();
            putTags(session, dto, user);
            return true;
        }));

        /* Mass-tagging. */
        api.putTags(secured((req, res, session, user) -> {
            MassTagging dto = new Gson().fromJson(req.body(), MassTagging.class);
            putTags(session, dto, user);
            return true;
        }));

        api.setItemDescription(":id", secured((req, res, session, user) -> {
            setItemDescription(session, Long.parseLong(req.params("id")), req.body(), user);
            return true;
        }));


        api.postComment(":id", securedItem(ItemCredentials.AuthenticationStatus.LOGGED_IN, (req, res, session, credentials, item) -> {
            NewComment body = new Gson().fromJson(req.body(), NewComment.class);
            if (body == null || body.text == null || body.text.length() > 1000 ||
                    body.author == null || body.author.length() > 100 || !Csrf.isValid()) {
                return badRequest(res);
            }

            Comment comment = new Comment();
            comment.setText(body.text);
            comment.setAuthor(body.author);

            // Question: how to propagate this securely?
            // 1. allowing the client to specify the timestamp allows forging past comments
            // 2. letting the server specify the timestamp means the forwarder and the upstream server will have different values

            // 3. let postComment return the posted timestamp. In the forwarder, either synchronously call upstream, or asynchronously UPDATE
            // timestamp when upstream responds. <- best

            return postComment(credentials, item.getServerId(), comment).get(res, timestamp -> {

                comment.setPosted(timestamp);

                item.getComments().add(comment);

                session.persist(comment);
                return comment.getPosted();
            });
        }));

        api.getComments(":id", securedItem(ItemCredentials.AuthenticationStatus.LOGGED_IN, (req, res, session, credentials, item) -> {
            return new Gson().toJson(item.getComments());
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

        LoginPage lp = new LoginPage(configuration);

        get("/", (req, res) -> title().get(res, title -> transacted(session -> getUnverifiedCredentials(req, session)).map(credentials ->
        {
            final LoginPage.Model model = credentials == null ? new LoginPage.Model(new ItemCredentials.PublicAccess(), title, LoginState.DISCONNECTED) :
                    credentials.map(new ItemCredentials.Mapper<LoginPage.Model>() {
                        @Override
                        public LoginPage.Model when(ItemCredentials.UserPassword password) {
                            try {
                                verifyCredentials(password, null);
                            } catch (AccessDeniedException e) {
                                /* Wrong password supplied */
                                return new LoginPage.Model(new ItemCredentials.PublicAccess(), title, LoginState.DISCONNECTED);
                            }

                            return new LoginPage.Model(password, title, LoginState.LOGGED_IN);
                        }

                        @Override
                        public LoginPage.Model when(ItemCredentials.UserToken token) {
                            return new LoginPage.Model(token, title, getUserTokenState(token.user, token.id, token.secret));
                        }

                        @Override
                        public LoginPage.Model when(ItemCredentials.PublicAccess pub) {
                            return new LoginPage.Model(credentials, title, LoginState.DISCONNECTED);
                        }

                        @Override
                        public LoginPage.Model when(ItemCredentials.ItemKey key) {
                            return new LoginPage.Model(credentials, title, LoginState.DISCONNECTED);

                        }
                    });
            return lp.render(model).toString();
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
                        Http.setCookie(res, configuration, LOGIN_COOKIE_NAME, token.encodeCookie(), Http.HttpOnlyFlag.SET);

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
        api.tokenListView(":name", securedPage(
                req -> req.params("name"),
                (model, credentials, domain) -> listTokens(domain, model, (ItemCredentials.UserCredentials) credentials)
                        .map(tokens -> new TokenListPage.Model(credentials, model, tokens)),
                (model, ct) -> UserAuthToken.CSV.header() +"\n" +
                        model.tokens.stream()
                                .map(UserAuthToken.CSV::serialise)
                                .collect(Collectors.joining("\n")),
                new TokenListPage(configuration)

        ));

        /* Authorise an authentication token */
        api.validateToken(":name", ":id", secured((req, res, session, user) -> {
            final String username = req.params("name");
            if (!user.user.getName().equals(username)) {
                // admins can validate tokens for other users
                user.user.verifyGroup(User.ADMIN_GROUP);
            }
            final long tokenId = Long.parseLong(req.params("id"));
            return validateToken(user, session, username, tokenId).get(res);
        }));

        /* Authorise an authentication token */
        api.deleteToken(":name", ":id", secured((req, res, session, user) -> {
            final String username = req.params("name");
            if (!user.user.getName().equals(username)) {
                // admins can delete tokens of other users
                user.user.verifyGroup(User.ADMIN_GROUP);
            }
            final long tokenId = Long.parseLong(req.params("id"));
            return deleteToken(user, session, username, tokenId).get(res);
        }));

        /* Page for experimenting new stuff */
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

    private Long nullableLong(String string) {
        return string == null ? null :
                Long.parseLong(string);
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

    protected abstract OrHttpError<Object> validateToken(ItemCredentials.UserCredentials credentials, D session, String username, long tokenId);

    protected abstract OrHttpError<Object> deleteToken(ItemCredentials.UserCredentials credentials, D session, String username, long tokenId);

    protected abstract OrHttpError<LoginState> login(ItemCredentials.UserToken credentials);

    protected abstract OrHttpError<String> logout(D session, ItemCredentials.UserToken credentials);

    protected abstract OrHttpError<List<UserAuthToken>> listTokens(D session, String name, ItemCredentials.UserCredentials credentials);

    protected abstract OrHttpError<ItemCredentials.UserToken> newToken(String username, String userAgent, String client);

    protected abstract OrHttpError<Instant> postComment(List<ItemCredentials> credentials, Long serverId, Comment comment);

    protected void verifyCredentials(List<ItemCredentials> credentials, IsItem item) throws AccessDeniedException {
        /* Only require the user to have access to the item if there are no other credentials. */
        boolean checkUserAccess = Iterables.all(credentials, c -> c.getPower() == ItemCredentials.Power.USER);
        for (ItemCredentials c : credentials) {
            if (checkUserAccess || c.getPower() != ItemCredentials.Power.USER) {
                verifyCredentials(c, item);
            }
        }
    }

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
                    throw new InvalidPasswordException("User token #"+ token.id +"' has not been validated");
                }
                authorise(token.user, item);
            }

            @Override
            public void when(ItemCredentials.PublicAccess pub) throws InvalidPasswordException {
                if (!Items.isPublic(item)) {
                    throw new InvalidPasswordException("Item #"+ item.getServerId() +" is not public");
                }
            }

            @Override
            public void when(ItemCredentials.ItemKey key) throws InvalidPasswordException {
                if (!key.checksum.equals(item.getChecksum())) {
                    throw new InvalidPasswordException("Bad key value. Expected " + item.getChecksum() + ", got " + key.checksum);
                }
            }
        });
    }

    protected void authorise(IsUser user, IsItem item) throws PermissionDeniedException {
        if (item != null && !item.getUser().equals(user.getName()) && !Items.isPublic(item)) {
            user.verifyGroup(User.ADMIN_GROUP);
        }
    }

    protected void expose(AbstractJs js) {
        String script = js.toString(); // do it once at initialisation, keep whole string in server memory
        get(api.getUrl(js), (req, res) -> {
            res.type("application/javascript; charset=UTF-8");
            return script;
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

    private OrHttpError<?> itemPage(BackendConfiguration configuration, Request req, Response res, D session, List<ItemCredentials> credentials, BackendItem item,
                            Supplier<Java8SearchCriteria<BackendItem>> gallerySupplier) throws IOException {
        boolean experimental = req.queryParamOrDefault("experimental", "false").equals("true");
        final ItemCredentials mostPowerful = ItemSecurity.mostPowerful(credentials);
        if (req.params("id").endsWith(ID_HTML_SUFFIX)) {
            User user = session.get(User.class, item.getUser());
            ImagePage renderer = new ImagePage(configuration);
            OrHttpError<ImagePage.Model> model;
            if (gallerySupplier == null) {
                model = new OrHttpError<>(new ImagePage.Model(item, null, null, null, user, credentials, experimental));
            } else {
                final Java8SearchCriteria<BackendItem> gallery = gallerySupplier.get();
                model = this.getQueryLoader(session, mostPowerful).load(gallery
                        .withRange(new QueryRange(-1, 3))
                        .relativeTo(item.getServerId(), mostPowerful))
                        .map(neighbours -> {
                            int index = Iterables.indexOf(neighbours.rows, n -> n.getServerId().equals(item.getServerId()));
                            return new ImagePage.Model(item,
                                    gallery,
                                    index > 0 ? neighbours.rows.get(index - 1) : null,
                                    index < neighbours.rows.size() - 1 ? neighbours.rows.get(index + 1) : null,
                                    user, credentials,
                                    experimental);
                        });
            }
            return model.map(m -> renderer.render(m).toString());
        } else {
            if (isCsvRequested(req) || isJsonRequested(req)) {
                GalleryPage.Model model = new GalleryPage.Model(mostPowerful, null, ItemSet.of(item), false);
                ContentType ct = isCsvRequested(req) ? ContentType.CSV : ContentType.JSON;
                res.type(ct.mime);
                return new OrHttpError<>(dump(model, ct, DumpFormat.WITH_TOTAL));
            } else {
                download(res, session, mostPowerful, item);
                return new OrHttpError<>(String.valueOf(res.status()));
            }
        }
    }

    private QueryRange requestedRange(Request req) {
        int offset = Integer.parseInt(req.queryParamOrDefault("offset", "0"));
        String limit = req.queryParams("limit");
        return (limit == null) ? QueryRange.offset(offset) :
                new QueryRange(offset, Integer.valueOf(limit));
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

    /** Create a Route for an action accessing the item specified in the {@code :id} route parameter.
     *
     * @param authStatus {@link org.gamboni.cloudspill.shared.api.ItemCredentials.AuthenticationStatus#LOGGED_IN}
     *  if the action is restricted to logged-in users (typically to modify the item),
     *  {@link org.gamboni.cloudspill.shared.api.ItemCredentials.AuthenticationStatus#ANONYMOUS} if the action is
     *  open to anonymous users (e.g. just to view the Item).
     * @param task execute the action.
     * @return a Spark Route.
     */
    protected Route securedItem(ItemCredentials.AuthenticationStatus authStatus, SecuredItemBody<D> task) {
        return (req, res) -> transacted(session -> {
            ItemCredentials key = itemKeyFromRequest(req);
            String idParam = dropHtmlSuffix(req.params("id"));
            OrHttpError<List<ItemCredentials>> credentialsOrError =
            optionalAuthenticate(req, session)
                    .map(login -> {
                        List<ItemCredentials> credentials = new ArrayList<>();
                        if (login != null) {
                            credentials.add(login);
                        }
                        if (key != null) {
                            credentials.add(key);
                        }
                        // don't put PublicAccess when there's a key or user is logged in to allow seeing password-protected gallery elements
                        if (credentials.isEmpty() && authStatus == ItemCredentials.AuthenticationStatus.ANONYMOUS) {
                            credentials.add(new ItemCredentials.PublicAccess());
                        }
                        return credentials;
                    });

            return credentialsOrError.get(res, credentials -> {
                /* Either we have a key, or user must be authenticated. */
                final long id = Long.parseLong(idParam);

                if (credentials.isEmpty()) {
                    Log.error("Denied access to item "+ id +": no credentials provided");
                    return forbidden(res, true);
                }

                return loadItem(session, id, credentials)
                        .get(res, item -> task.handle(req, res, session, credentials, item));
            });
        });
    }

    private ItemCredentials itemKeyFromRequest(Request req) {
        final String keyString = restorePluses(req.queryParams("key"));
        return (keyString == null) ? null : new ItemCredentials.ItemKey(keyString);
    }

    /** Acquire the item with the given id. If multiple credentials are provided, they must all be valid. If no credentials
     * are provided, access must be denied. */
    protected abstract OrHttpError<? extends BackendItem> loadItem(D session, long id, List<ItemCredentials> credentials);

    protected abstract OrHttpError<Long> upload(D session, ItemCredentials.UserCredentials user, InputStream inputStream,
                                   String folder, String path,
                                   ItemMetadata metadata) throws IOException;

    protected abstract OrHttpError<GalleryListPage.Model> galleryList(ItemCredentials credentials, D domain);

    protected List<String> tagList(D domain) {
        final Query query = domain.getEntityManager().createNativeQuery(
                "select distinct tags from Item_tags order by tags");
        return (List<String>) query.getResultList();
    }

    protected abstract OrHttpError<GalleryListPage.Model> tagGalleryList(ItemCredentials credentials, D domain);

    protected abstract OrHttpError<String> title();

    protected abstract OrHttpError<GalleryListPage.Model> dayList(ItemCredentials credentials, D domain, int year);

    /** Add the given comma-separated tags to the specified object. If a tag starts with '-' then it is removed instead.
     * <p>
     * NOTE: anybody can change tags of anybody's item.
     */
    protected void putTags(D session, MassTagging dto, ItemCredentials credentials) throws IOException {
        dto.ids.forEach(id -> {
            final BackendItem item = itemForUpdate(session, id);

            final Set<String> existingTags = item.getTags();
            Splitter.on(',').split(dto.tags).forEach(t -> {
                if (t.startsWith("-")) {
                    existingTags.remove(t.substring(1).trim());
                } else {
                    existingTags.add(t.trim());
                }
            });
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

    private OrHttpError<String> dump(D domain, ServerSearchCriteria criteria, ItemCredentials credentials, ContentType ct, DumpFormat dumpFormat) throws Exception {
        return getQueryLoader(domain, credentials).load(criteria).map(set -> dump(
                new GalleryPage.Model(credentials, criteria, set, false), ct, dumpFormat));
    }

    private String dump(GalleryPage.Model model, ContentType ct, DumpFormat dumpFormat) {

        Instant[] timestamp = new Instant[]{Instant.EPOCH};
        final Stream<? extends BackendItem> stream = model.itemSet.rows.stream()
                .peek(item -> {
                    if (item.getUpdated() != null && item.getUpdated().isAfter(timestamp[0])) {
                        timestamp[0] = item.getUpdated();
                    }
                });
        if (ct == ContentType.JSON) {
            final JsonArray data = dumpJson(stream, BackendItem.CSV);
            JsonObject object = new JsonObject();
            object.add("data", data);
            // galleries do not yet support metadata in forwarder, so disabling that for now
            //return dumpFormat.dumpMetadata(criteria, itemSet, timestamp[0], new JsonMetadataRepresentation(object)).toString();
            return object.toString();
        } else { // csv
            final StringBuilder data = dumpCsv(stream, BackendItem.CSV);
            return dumpFormat.dumpMetadata(model.criteria, model.itemSet, timestamp[0], new OnePerLineMetadataRepresentation(data)).toString();
        }
    }

    private <T> StringBuilder dumpCsv(Stream<? extends T> stream, Csv<T> csv) {
        StringBuilder result = new StringBuilder(csv.header() + "\n");
        stream.forEach(item -> {
            result.append(csv.serialise(item)).append("\n");
        });
        return result;
    }

    private <T> JsonArray dumpJson(Stream<? extends T> stream, Csv<T> csv) {
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

    protected abstract Java8SearchCriteria<BackendItem> loadGallery(D session, long partId, String key);

    protected interface SecuredItemBody<D extends CloudSpillEntityManagerDomain> {
        Object handle(Request request, Response response, D session, List<ItemCredentials> credentials, BackendItem item) throws Exception;
    }
}
