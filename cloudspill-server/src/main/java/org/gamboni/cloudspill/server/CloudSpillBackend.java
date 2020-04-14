package org.gamboni.cloudspill.server;

import org.gamboni.cloudspill.domain.CloudSpillEntityManagerDomain;
import org.gamboni.cloudspill.domain.Item;
import org.gamboni.cloudspill.domain.User;
import org.gamboni.cloudspill.server.config.BackendConfiguration;
import org.gamboni.cloudspill.server.config.ServerConfiguration;
import org.gamboni.cloudspill.server.html.GalleryPage;
import org.gamboni.cloudspill.server.html.HtmlFragment;
import org.gamboni.cloudspill.server.html.ImagePage;
import org.gamboni.cloudspill.server.query.ItemSet;
import org.gamboni.cloudspill.server.query.Java8SearchCriteria;
import org.gamboni.cloudspill.server.query.ServerSearchCriteria;
import org.gamboni.cloudspill.shared.api.CloudSpillApi;
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

        get("/tag/:tag", secured((req, res, domain, user) ->
        {
            final ServerSearchCriteria searchCriteria = ServerSearchCriteria.ALL
                    .withTag(req.params("tag"))
                    .atOffset(Integer.parseInt(req.queryParamOrDefault("offset", "0")));

            return new GalleryPage(configuration, doSearch(domain, searchCriteria)).getHtml(user);
        }));

        get("/day/:day", secured((req, res, domain, user) -> {
            LocalDate day = LocalDate.parse(req.params("day"));
            final ServerSearchCriteria searchCriteria = ServerSearchCriteria.ALL
                    .at(day)
                    .atOffset(Integer.parseInt(req.queryParamOrDefault("offset", "0")));

            return new GalleryPage(configuration, doSearch(domain, searchCriteria)).getHtml(user);
        }));

        get("/gallery/", secured((req, res, domain, user) -> galleryListPage(domain, user)));

        get("/gallery/:part", secured((req, res, domain, user) -> {
            return new GalleryPage(configuration, loadGallery(domain, Long.parseLong(req.params("part")))).getHtml(user);
        }));

        get("/public/gallery/", (req, res) -> transacted(domain -> {
            return galleryListPage(domain, null);
        }));

        get("/public/gallery/:part", (req, res) -> transacted(domain -> {
            return new GalleryPage(configuration, loadGallery(domain, Long.parseLong(req.params("part")))).getHtml(null);
        }));

        get("/public", (req, res) -> transacted(session -> {
            ServerSearchCriteria criteria = ServerSearchCriteria.ALL
                    .withTag("public")
                    .atOffset(Integer.parseInt(req.queryParamOrDefault("offset", "0")));
            return new GalleryPage(configuration, doSearch(session, criteria)).getHtml(null);
        }));

        /* Download a file */
        get("/item/:id", securedItem((req, res, session, user, item) -> {
            if (req.params("id").endsWith(ID_HTML_SUFFIX)) {
                return new ImagePage(configuration, item).getHtml(user);
            } else {
                download(res, session, item);
                return String.valueOf(res.status());
            }
        }));

        /* Download a public file */
        get("/public/item/:id", (req, res) -> transacted(session -> {
            String idParam = req.params("id");
            if (idParam.endsWith(ID_HTML_SUFFIX)) {
                idParam = idParam.substring(0, idParam.length() - ID_HTML_SUFFIX.length());
            }
            final long id = Long.parseLong(idParam);
            final Item item = session.get(Item.class, id);

            if (item == null) {
                return notFound(res, id);
            } else if (!item.isPublic()) {
                return forbidden(res, false);
            }

            if (req.params("id").endsWith(ID_HTML_SUFFIX)) {
                return new ImagePage(configuration, item).getHtml(null);
            } else {
                download(res, session, item);
                return String.valueOf(res.status());
            }
        }));

        /* Download a thumbnail */
        get("/thumbs/:size/:id", securedItem((req, res, session, user, item) ->
                thumbnail(res, session, item, Integer.parseInt(req.params("size")))
        ));

        /* Get list of items whose id is larger than the given one. */
        get("item/since/:id", secured((req, res, domain, user) -> {
            return dump(res, domain, ServerSearchCriteria.ALL.withIdAtLeast(Long.parseLong(req.params("id"))),
                    DumpFormat.NO_TIMESTAMP);
        }));

        /* Get list of items updated at or later than the given timestamp. */
        get(api.getItemsSinceUrl(":date"), secured((req, res, domain, user) -> {
            return dump(res, domain,
                    ServerSearchCriteria.ALL.modifiedSince(Instant.ofEpochMilli(Long.parseLong(req.params("date")))),
                DumpFormat.WITH_TIMESTAMP);
        }));

        /* Add the tags specified in body to the given item. */
        put(api.getTagUrl(":id"), secured((req, res, session, user) -> {
            Log.debug("Tag query for item "+ req.params("id") +": '"+ req.body() +"'");
            putTags(session, Long.parseLong(req.params("id")), req.body());
            return true;
        }));

        /* Upload a file */
        put(api.upload(":user", ":folder", "*"), secured((req, res, session, user) -> {
        	/*if (req.bodyAsBytes() == null) {
        		Log.warn("Missing body");
        		res.status(400);
        		return null;
        	}*/

            String username = req.params("user");
            String folder = req.params("folder");
            if (!user.getName().equals(username)) {
                Log.error("User "+ user.getName() +" attempted to upload to folder of user "+ username);
                return forbidden(res, false);
            }
            String path = req.splat()[0];
            Log.debug("user is "+ username +", folder is "+ folder +" and path is "+ path);
            return upload(req, res, session, user, folder, path);
        }));
    }

    protected abstract Long upload(Request req, Response res, D session, User user, String folder, String path) throws IOException;

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

    private String dump(Response res, D domain, Java8SearchCriteria<Item> searchCriteria, DumpFormat dumpFormat) {
        ItemSet set = doSearch(domain, searchCriteria);
        res.type("text/csv; charset=UTF-8");

        StringBuilder result = new StringBuilder();
        Instant timestamp = Instant.EPOCH;
        for (Item item : set.getAllItems()) {
            result.append(item.serialise()).append("\n");
            timestamp = item.getUpdated();
        }
        result.append(dumpFormat.dumpTimestamp(timestamp));
        return result.toString();
    }

    protected abstract Object thumbnail(Response res, D session, Item item, int size) throws InterruptedException, IOException;

    /**
     * Download the file corresponding to the item with the given id, optionally
     * after having checked an access key.
     *
     * @param res
     *            HTTP Response used to set Forbidden status if needed
     * @param session
     *            Database connection
     * @param item
     *            The item to retrieve
     * @throws IOException
     */
    protected abstract void download(Response res, D session, Item item) throws IOException;

    protected abstract String ping();

    protected abstract ItemSet doSearch(D session, Java8SearchCriteria<Item> criteria);

    protected abstract ItemSet loadGallery(D session, long partId);

    protected abstract HtmlFragment galleryListPage(D domain, User user);

    protected interface SecuredItemBody<D extends CloudSpillEntityManagerDomain> {
        Object handle(Request request, Response response, D session, User user, Item item) throws Exception;
    }

    protected abstract Route securedItem(SecuredItemBody<D> task);

}
