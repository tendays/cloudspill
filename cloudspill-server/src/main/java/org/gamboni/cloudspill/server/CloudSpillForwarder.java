package org.gamboni.cloudspill.server;

import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import com.google.inject.Guice;
import com.google.inject.Injector;

import org.gamboni.cloudspill.domain.ForwarderDomain;
import org.gamboni.cloudspill.domain.Item;
import org.gamboni.cloudspill.server.config.ForwarderConfiguration;
import org.gamboni.cloudspill.server.html.HtmlFragment;
import org.gamboni.cloudspill.server.query.ItemSet;
import org.gamboni.cloudspill.server.query.Java8SearchCriteria;
import org.gamboni.cloudspill.shared.api.Base64Encoder;
import org.gamboni.cloudspill.shared.api.CloudSpillApi;
import org.gamboni.cloudspill.shared.api.Csv;
import org.gamboni.cloudspill.shared.api.ItemCredentials;
import org.gamboni.cloudspill.shared.util.Log;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Base64;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.servlet.http.HttpServletResponse;

import spark.Request;
import spark.Response;

/**
 * @author tendays
 */
public class CloudSpillForwarder extends CloudSpillBackend<ForwarderDomain> {

    private final ForwarderConfiguration configuration;
    private final CloudSpillApi remoteApi;

    @Inject
    public CloudSpillForwarder(ForwarderConfiguration configuration) {
        this.configuration = configuration;
        this.remoteApi = new CloudSpillApi(configuration.getRemoteServer());
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            Log.error("Usage: CloudSpillForwarder configPath");
            System.exit(1);
        }
        Injector injector = Guice.createInjector(new ForwarderModule(args[0]));

        try {
            injector.getInstance(CloudSpillForwarder.class).run();
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }

    private void run() {
        setupRoutes(configuration);
    }

    @Override
    protected OrHttpError<Item> loadItem(ForwarderDomain session, long id, ItemCredentials credentials) {
        Item item = session.get(Item.class, id);
        if (item == null) {
            // item not found locally, try from remote server
            try {
                URLConnection connection = new URL(remoteApi.getImageUrl(id, credentials)).openConnection();
                credentials.setHeaders(connection, Base64.getEncoder()::encodeToString);
                connection.setRequestProperty("Accept", "text/csv");
                try (LineNumberReader r = new LineNumberReader(new InputStreamReader(connection.getInputStream()))) {
                    final int responseCode = ((HttpURLConnection) connection).getResponseCode();
                    if (responseCode < 200 || responseCode >= 300) {
                        return new OrHttpError<>(res -> {
                            try {
                                String responseString = CharStreams.toString(r);
                                res.status(responseCode);
                                return "Remote Server answered: " + responseString;
                            } catch (IOException e) {
                                res.status(HttpServletResponse.SC_GATEWAY_TIMEOUT);
                                return "Error communicating with remote server";
                            }
                        });
                    }
                    final Csv.Extractor<Item> extractor = Item.deserialise(r.readLine());
                    Item remote = extractor.deserialise(new Item(), r.readLine());
                    try {
                        /* This may fail in case there's another request for the same item at the same time. */
                        transacted(nestedSession -> {
                            nestedSession.persist(remote);
                            return null;
                        });
                    } catch (Exception e) {
                        Log.warn("Failed saving remote entity locally", e);
                    }
                    return new OrHttpError<>(remote);
                }
            } catch (IOException e) {
                e.printStackTrace();
                return new OrHttpError<>(res -> internalServerError(res));
            }
        } else if (!credentials.verify(item)) {
            return new OrHttpError<>(res -> forbidden(res, false));
        } else {
            return new OrHttpError<>(item);
        }
    }

    @Override
    protected void download(Response res, ForwarderDomain session, ItemCredentials credentials, Item item) throws IOException {
        final URLConnection connection = new URL(remoteApi.getImageUrl(item.getServerId(), credentials)).openConnection();
        credentials.setHeaders(connection, Base64.getEncoder()::encodeToString);
        res.status(((HttpURLConnection)connection).getResponseCode());

        res.header("Content-Type", item.getType().asMime());
        res.header("Content-Length", String.valueOf(connection.getContentLength()));

        ByteStreams.copy(connection.getInputStream(), res.raw().getOutputStream());
    }

    @Override
    protected Long upload(Request req, Response res, ForwarderDomain session, ItemCredentials.UserPassword user, String folder, String path) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void putTags(ForwarderDomain session, long id, String body) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected Object thumbnail(Response res, ForwarderDomain session, Item item, int size) throws InterruptedException, IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected String ping() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected ItemSet doSearch(ForwarderDomain session, Java8SearchCriteria<Item> criteria) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected ItemSet loadGallery(ForwarderDomain session, long partId) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected HtmlFragment galleryListPage(ForwarderDomain domain, ItemCredentials credentials) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected ForwarderDomain createDomain(EntityManager e) {
        throw new UnsupportedOperationException();
    }
}
