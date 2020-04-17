package org.gamboni.cloudspill.server;

import org.gamboni.cloudspill.domain.BackendItem;
import org.gamboni.cloudspill.domain.CloudSpillEntityManagerDomain;
import org.gamboni.cloudspill.server.config.BackendConfiguration;
import org.gamboni.cloudspill.server.html.GalleryPage;
import org.gamboni.cloudspill.server.html.HtmlFragment;
import org.gamboni.cloudspill.server.html.ImagePage;
import org.gamboni.cloudspill.server.query.ItemSet;
import org.gamboni.cloudspill.server.query.Java8SearchCriteria;
import org.gamboni.cloudspill.server.query.ServerSearchCriteria;
import org.gamboni.cloudspill.shared.api.CloudSpillApi;
import org.gamboni.cloudspill.shared.api.ItemCredentials;
import org.gamboni.cloudspill.shared.domain.IsUser;
import org.gamboni.cloudspill.shared.util.Log;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;

import spark.Request;
import spark.Response;
import spark.Route;

import static org.gamboni.cloudspill.shared.api.CloudSpillApi.ID_HTML_SUFFIX;
import static spark.Spark.before;
import static spark.Spark.get;
import static spark.Spark.put;

/** Common implementation of the {@link org.gamboni.cloudspill.shared.api.CloudSpillApi}, implemented by both {@link CloudSpillServer}
 * and CloudSpillProxy
 *
 * @author tendays
 */
public abstract class CloudSpillBackend<D extends CloudSpillEntityManagerDomain> extends AbstractServer<D> {
    protected final void setupRoutes(BackendConfiguration configuration) {
        CloudSpillApi api = new CloudSpillApi("");

        /* Access logging */
        before((req, res) -> {
            Log.info(req.ip() +" "+ req.requestMethod() +" "+ req.uri());
        });

        get(api.ping(), secured((req, res, session, user) -> ping()));

        get("/tag/:tag", secured((req, res, domain, credentials) ->
        {
            final ServerSearchCriteria searchCriteria = ServerSearchCriteria.ALL
                    .withTag(req.params("tag"))
                    .atOffset(Integer.parseInt(req.queryParamOrDefault("offset", "0")));

            return new GalleryPage(configuration, doSearch(domain, searchCriteria)).getHtml(credentials);
        }));

        get("/day/:day", secured((req, res, domain, user) -> {
            LocalDate day = LocalDate.parse(req.params("day"));
            final ServerSearchCriteria searchCriteria = ServerSearchCriteria.ALL
                    .at(day)
                    .atOffset(Integer.parseInt(req.queryParamOrDefault("offset", "0")));

            return new GalleryPage(configuration, doSearch(domain, searchCriteria)).getHtml(user);
        }));

        get("/gallery/", secured((req, res, domain, credentials) -> galleryListPage(domain, credentials)));

        get("/gallery/:part", secured((req, res, domain, user) -> {
            return new GalleryPage(configuration, loadGallery(domain, Long.parseLong(req.params("part")))).getHtml(user);
        }));

        get("/public/gallery/", (req, res) -> transacted(domain -> {
            return galleryListPage(domain, new ItemCredentials.PublicAccess());
        }));

        get("/public/gallery/:part", (req, res) -> transacted(domain -> {
            return new GalleryPage(configuration, loadGallery(domain, Long.parseLong(req.params("part")))).getHtml(new ItemCredentials.PublicAccess());
        }));

        get("/public", (req, res) -> transacted(session -> {
            ServerSearchCriteria criteria = ServerSearchCriteria.ALL
                    .withTag("public")
                    .atOffset(Integer.parseInt(req.queryParamOrDefault("offset", "0")));
            return new GalleryPage(configuration, doSearch(session, criteria)).getHtml(new ItemCredentials.PublicAccess());
        }));

        /* Download a file */
        get("/item/:id", securedItem(ItemCredentials.AuthenticationStatus.LOGGED_IN, (req, res, session, credentials, item) -> {
            return itemPage(configuration, req, res, session, credentials, item);
        }));

        /* Download a public file */
        get("/public/item/:id", securedItem(ItemCredentials.AuthenticationStatus.ANONYMOUS, (req, res, session, credentials, item) -> {
                return itemPage(configuration, req, res, session, credentials, item);
        }));

        /* Download a thumbnail */
        get("/thumbs/:size/:id", securedItem(ItemCredentials.AuthenticationStatus.LOGGED_IN, (req, res, session, user, item) ->
                thumbnail(res, session, item, Integer.parseInt(req.params("size")))
        ));

        /* Get list of items whose id is larger than the given one. */
        get("item/since/:id", secured((req, res, domain, user) -> {
            return dump(res, doSearch(domain, ServerSearchCriteria.ALL.withIdAtLeast(Long.parseLong(req.params("id")))),
                    DumpFormat.NO_TIMESTAMP);
        }));

        /* Get list of items updated at or later than the given timestamp. */
        get(api.getItemsSinceUrl(":date"), secured((req, res, domain, user) -> {
            return dump(res, doSearch(domain,
                    ServerSearchCriteria.ALL.modifiedSince(Instant.ofEpochMilli(Long.parseLong(req.params("date"))))),
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

    private Object itemPage(BackendConfiguration configuration, Request req, Response res, D session, ItemCredentials credentials, BackendItem item) throws IOException {
        final String acceptHeader = req.headers("Accept");
        if (req.params("id").endsWith(ID_HTML_SUFFIX)) {
            return new ImagePage(configuration, item).getHtml(credentials);
        } else if (acceptHeader != null && acceptHeader.equals("text/csv")) {
            return dump(res, ItemSet.of(item), DumpFormat.NO_TIMESTAMP);
        } else {
            download(res, session, credentials, item);
            return String.valueOf(res.status());
        }
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

    /** Add the given comma-separated tags to the specified object. If a tag starts with '-' then it is removed instead.
     * <p>
     * NOTE: anybody can change tags of anybody's item.
     */
    protected abstract void putTags(D session, long id, String body);

    private enum DumpFormat {
        NO_TIMESTAMP {
            @Override
            String dumpTimestamp(Instant timestamp) {
                return "";
            }
        },
        WITH_TIMESTAMP {
            @Override
            String dumpTimestamp(Instant timestamp) {
                return "Timestamp:"+ timestamp.toEpochMilli() +'\n';
            }
        };

        abstract String dumpTimestamp(Instant timestamp);
    }

    private String dump(Response res, ItemSet set, DumpFormat dumpFormat) {
        res.type("text/csv; charset=UTF-8");

        StringBuilder result = new StringBuilder(BackendItem.csvHeader() + "\n");
        Instant timestamp = Instant.EPOCH;
        for (BackendItem item : set.getAllItems()) {
            result.append(item.serialise()).append("\n");
            timestamp = item.getUpdated();
        }
        result.append(dumpFormat.dumpTimestamp(timestamp));
        return result.toString();
    }

    protected abstract Object thumbnail(Response res, D session, BackendItem item, int size) throws InterruptedException, IOException;

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

    protected abstract ItemSet doSearch(D session, Java8SearchCriteria<BackendItem> criteria);

    protected abstract ItemSet loadGallery(D session, long partId);

    protected abstract HtmlFragment galleryListPage(D domain, ItemCredentials credentials);

    protected interface SecuredItemBody<D extends CloudSpillEntityManagerDomain> {
        Object handle(Request request, Response response, D session, ItemCredentials credentials, BackendItem item) throws Exception;
    }
}
