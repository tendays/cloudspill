package org.gamboni.cloudspill.server;

import com.google.common.collect.Iterables;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import com.google.inject.Guice;
import com.google.inject.Injector;

import org.gamboni.cloudspill.domain.BackendItem;
import org.gamboni.cloudspill.domain.ForwarderDomain;
import org.gamboni.cloudspill.domain.GalleryPart;
import org.gamboni.cloudspill.domain.RemoteItem;
import org.gamboni.cloudspill.server.config.ForwarderConfiguration;
import org.gamboni.cloudspill.server.html.GalleryListPage;
import org.gamboni.cloudspill.server.query.ItemQueryLoader;
import org.gamboni.cloudspill.server.query.ItemSet;
import org.gamboni.cloudspill.server.query.Java8SearchCriteria;
import org.gamboni.cloudspill.shared.api.CloudSpillApi;
import org.gamboni.cloudspill.shared.api.Csv;
import org.gamboni.cloudspill.shared.api.ItemCredentials;
import org.gamboni.cloudspill.shared.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Supplier;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.servlet.http.HttpServletResponse;

import spark.Request;
import spark.Response;

import static org.gamboni.cloudspill.shared.util.Files.append;

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
    protected OrHttpError<RemoteItem> loadItem(ForwarderDomain session, long id, ItemCredentials credentials) {
        RemoteItem item = session.get(RemoteItem.class, id);
        if (item == null) {
            // item not found locally, try from remote server
            return deserialiseStream(remoteApi.getImageUrl(id, credentials), credentials)
                    .flatMap(remoteList -> {
                        switch (remoteList.size()) {
                            case 0:
                                Log.warn("Remote returned empty list to single-item query (should return 404 instead!)");
                                return notFound(id);
                            case 1:
                                final RemoteItem remote = Iterables.getOnlyElement(remoteList);
                                try {
                                    /* This may fail in case there's another request for the same item at the same time. */
                                    transacted(nestedSession -> {
                                        nestedSession.persist(remote);
                                        return "";
                                    });
                                } catch (Exception e) {
                                    Log.warn("Failed saving remote entity locally", e);
                                }
                                return new OrHttpError<>(remote);
                            default:
                                Log.warn("Remote returned " + remoteList.size() + " elements to single-item query");
                                return internalServerError();
                        }
                    });
        } else if (!credentials.verify(item)) {
            return forbidden(false);
        } else {
            return new OrHttpError<>(item);
        }
    }

    private interface ExtraDataReader<T, R> {
        R buildResult(List<T> rows, LineNumberReader reader) throws IOException;
    }

    private <T, R> OrHttpError<R> deserialiseStream(String url, ItemCredentials credentials,
                                                    Csv<? super T> csv, Supplier<T> factory, ExtraDataReader<T, R> extra) {
        try {
            final URLConnection connection = new URL(url).openConnection();
            credentials.setHeaders(connection, Base64.getEncoder()::encodeToString);
            connection.setRequestProperty("Accept", "text/csv");
            try (Reader reader = new InputStreamReader(connection.getInputStream())) {
                final int responseCode = ((HttpURLConnection) connection).getResponseCode();
                if (responseCode < 200 || responseCode >= 300) {
                    return new OrHttpError<>(res -> {
                        try {
                            String responseString = CharStreams.toString(reader);
                            res.status(responseCode);
                            return "Remote Server answered: " + responseString;
                        } catch (IOException e) {
                            return gatewayTimeout(res);
                        }
                    });
                }

                LineNumberReader lineReader = new LineNumberReader(reader);
                String headerLine = lineReader.readLine();
                if (headerLine == null) {
                    Log.warn("Missing header line from remote server");
                    return internalServerError();
                }
                final Csv.Extractor<? super T> extractor = csv.extractor(headerLine);
                String line;
                List<T> result = new ArrayList<>();
                while ((line = lineReader.readLine()) != null && !line.isEmpty()) {
                    result.add(extractor.deserialise(factory.get(), line));
                }
                return new OrHttpError<>(extra.buildResult(result, lineReader));
            }
        } catch (IOException e) {
            Log.warn("Error communicating with remote server", e);
            return gatewayTimeout();
        }
    }

    private OrHttpError<List<RemoteItem>> deserialiseStream(String url, ItemCredentials credentials) {
        return deserialiseStream(url, credentials, BackendItem.CSV, RemoteItem::new, (rows, reader) -> rows);
    }

    private <R> OrHttpError<R> gatewayTimeout() {
        return new OrHttpError<>(res ->  gatewayTimeout(res));
    }

    private String gatewayTimeout(Response res) {
        res.status(HttpServletResponse.SC_GATEWAY_TIMEOUT);
        return "Error communicating with remote server";
    }

    @Override
    protected void download(Response res, ForwarderDomain session, ItemCredentials credentials, BackendItem item) throws IOException {
        doCachedRequest(res, credentials,
                append(append(append(append(
                        configuration.getRepositoryPath(),
                        "full-size"),
                        item.getUser()),
                        item.getFolder()),
                        item.getPath()),
                remoteApi.getImageUrl(item.getServerId(), credentials),
                item.getType().asMime());
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
    protected Object thumbnail(Response res, ForwarderDomain session, ItemCredentials credentials, BackendItem item, int size) throws InterruptedException, IOException {
        return doCachedRequest(res, credentials,
                append(append(append(append(
                        configuration.getRepositoryPath(),
                        String.valueOf(size)),
                        item.getUser()),
                item.getFolder()),
                item.getPath()),
                remoteApi.getThumbnailUrl(item.getServerId(), credentials, size),
                "image/jpeg");
    }

    private Object doCachedRequest(Response res, ItemCredentials credentials, File cache, String url, String contentType) throws IOException {
        if (cache.exists()) {
            try (final FileInputStream inputStream = new FileInputStream(cache);
                 final OutputStream clientOutput = res.raw().getOutputStream()) {
                ByteStreams.copy(inputStream, clientOutput);
            }
        } else {
            final URLConnection connection = new URL(url).openConnection();
            credentials.setHeaders(connection, Base64.getEncoder()::encodeToString);
            res.status(((HttpURLConnection)connection).getResponseCode());

            res.header("Content-Type", contentType);
            res.header("Content-Length", String.valueOf(connection.getContentLength()));

            byte[] buffer = new byte[8192];

            cache.getParentFile().mkdirs();

            try (InputStream remoteInput = connection.getInputStream();
                 OutputStream clientOutput = res.raw().getOutputStream();
                 OutputStream cacheOutput = new FileOutputStream(cache)) {

                while (true) {
                    int r = remoteInput.read(buffer);
                    if (r == -1) {
                        break;
                    }

                    clientOutput.write(buffer, 0, r);
                    cacheOutput.write(buffer, 0, r);
                }
            }
        }

        return "";
    }

    @Override
    protected String ping() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected ItemQueryLoader getQueryLoader(ForwarderDomain session, ItemCredentials credentials) {
        return new ItemQueryLoader() {
            @Override
            public OrHttpError<ItemSet> load(Java8SearchCriteria<BackendItem> criteria) {
                return deserialiseStream(criteria.getUrl(remoteApi), credentials)
                        .map(rows -> new ItemSet(rows.size() + criteria.getOffset(), rows));
            }

            @Override
            public OrHttpError<ItemSet> load(Java8SearchCriteria<BackendItem> criteria, int limit) {
                return deserialiseStream(criteria.getUrl(remoteApi), credentials)
                        .map(rows -> new ItemSet(rows.size() + criteria.getOffset(),
                                (rows.size() > limit) ? rows.subList(0, limit) : rows));
            }
        };
    }

    @Override
    protected Java8SearchCriteria<BackendItem> loadGallery(ForwarderDomain session, long partId) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected OrHttpError<GalleryListData> galleryList(ItemCredentials credentials, ForwarderDomain domain) {
        return deserialiseStream(
                remoteApi.galleryListPage(credentials),
                credentials,
                GalleryListPage.Element.CSV,
                () -> new GalleryListPage.Element(new GalleryPart()),
                (elements, reader) -> {
                    String titleLine = reader.readLine();
                    return new GalleryListData(
                            (titleLine != null && titleLine.startsWith("Title:")) ?
                        titleLine.substring("Title:".length()) : "",
                            elements);
                });
    }

    @Override
    protected ForwarderDomain createDomain(EntityManager e) {
        return new ForwarderDomain(e);
    }
}
