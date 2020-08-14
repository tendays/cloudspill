package org.gamboni.cloudspill.server;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import com.google.inject.Guice;
import com.google.inject.Injector;

import org.gamboni.cloudspill.domain.BackendItem;
import org.gamboni.cloudspill.domain.CloudSpillEntityManagerDomain;
import org.gamboni.cloudspill.domain.ForwarderDomain;
import org.gamboni.cloudspill.domain.RemoteItem;
import org.gamboni.cloudspill.domain.RemoteUserAuthToken;
import org.gamboni.cloudspill.domain.User;
import org.gamboni.cloudspill.domain.UserAuthToken;
import org.gamboni.cloudspill.lambda.client.ApiInvokers;
import org.gamboni.cloudspill.server.config.ForwarderConfiguration;
import org.gamboni.cloudspill.server.html.GalleryListPage;
import org.gamboni.cloudspill.server.query.GalleryPartReference;
import org.gamboni.cloudspill.server.query.ItemQueryLoader;
import org.gamboni.cloudspill.server.query.ItemSet;
import org.gamboni.cloudspill.server.query.Java8SearchCriteria;
import org.gamboni.cloudspill.shared.api.Base64Encoder;
import org.gamboni.cloudspill.shared.api.CloudSpillApi;
import org.gamboni.cloudspill.shared.api.Csv;
import org.gamboni.cloudspill.shared.api.CsvEncoding;
import org.gamboni.cloudspill.shared.api.ItemCredentials;
import org.gamboni.cloudspill.shared.api.ItemMetadata;
import org.gamboni.cloudspill.shared.api.LoginState;
import org.gamboni.cloudspill.shared.client.ResponseHandler;
import org.gamboni.cloudspill.shared.client.ResponseHandlers;
import org.gamboni.cloudspill.shared.domain.AccessDeniedException;
import org.gamboni.cloudspill.shared.domain.ClientUser;
import org.gamboni.cloudspill.shared.domain.IsUser;
import org.gamboni.cloudspill.shared.domain.ItemType;
import org.gamboni.cloudspill.shared.util.Log;
import org.mindrot.jbcrypt.BCrypt;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Supplier;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.servlet.http.HttpServletResponse;

import spark.Response;

import static org.gamboni.cloudspill.shared.util.Files.append;

/** The forwarder acts both as a client to a remote CloudSpill server, and as a server.
 * It will cache items in a local directory.
 * <p>
 *     This is a standalone Java application but can also be started by passing the -forward option to {@link CloudSpillServer}.
 * </p>
 *
 * @author tendays
 */
public class CloudSpillForwarder extends CloudSpillBackend<ForwarderDomain> {

    public static final Base64Encoder BASE_64_ENCODER = Base64.getEncoder()::encodeToString;
    private final ForwarderConfiguration configuration;
    private final CloudSpillApi<ResponseHandler> remoteApi;

    /** Temporary: keep tokens in memory */
    Multimap<String, UserAuthToken> tokens = HashMultimap.create();

    @Inject
    public CloudSpillForwarder(ForwarderConfiguration configuration) {
        this.configuration = configuration;
        this.remoteApi = CloudSpillApi.client(configuration.getRemoteServer());
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

    public void run() {
        setupRoutes(configuration);
    }

    @Override
    protected OrHttpError<User> getUser(String username, CloudSpillEntityManagerDomain session) {
        return getUserFromDB(username, session);
    }

    @Override
    protected OrHttpError<Object> validateToken(ForwarderDomain session, String username, long tokenId) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected OrHttpError<LoginState> login(ItemCredentials.UserToken credentials) {
        try {
            final HttpURLConnection connection = (HttpURLConnection) new URL(remoteApi.login()).openConnection();
            connection.setRequestMethod("POST");
            credentials.setHeaders(connection, BASE_64_ENCODER);
            final String response;
            try (final InputStreamReader in = new InputStreamReader(connection.getInputStream())) {
                response = CharStreams.toString(in);
            }

            try {
                return new OrHttpError<>(LoginState.valueOf(response));
            } catch (IllegalArgumentException e) {
                Log.warn("Unexpected response '"+ response +"' after login");
                return gatewayTimeout();
            }
        } catch (IOException e) {
            Log.warn("Error communicating with remote server", e);
            return gatewayTimeout();
        }
    }

    @Override
    protected OrHttpError<String> logout(ForwarderDomain session, ItemCredentials.UserToken credentials) {
        try {
            final HttpURLConnection connection = (HttpURLConnection) new URL(remoteApi.logout()).openConnection();
            connection.setRequestMethod("POST");
            credentials.setHeaders(connection, BASE_64_ENCODER);
            final String response;
            try (final InputStreamReader in = new InputStreamReader(connection.getInputStream())) {
                response = CharStreams.toString(in);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return new OrHttpError<>(res -> {
                    res.status(responseCode);
                    return response;
                });
            }

            /* Token was accepted for deletion by remote instance so delete it directly here */
            final RemoteUserAuthToken token = session.get(RemoteUserAuthToken.class, credentials.id);
            if (token != null) {
                session.remove(token);
            }

            return new OrHttpError<>(response);
        } catch (IOException e) {
            Log.warn("Error communicating with remote server", e);
            return gatewayTimeout();
        }
    }

    @Override
    protected OrHttpError<List<UserAuthToken>> listInvalidTokens(ForwarderDomain session, ItemCredentials.UserCredentials user) {
        return this.deserialiseStream(remoteApi.listInvalidTokens(user.user.getName()), ImmutableList.of(user), UserAuthToken.CSV, UserAuthToken::new, (rows, reader) -> rows);
    }

    @Override
    protected OrHttpError<ItemCredentials.UserToken> newToken(String username, String userAgent, String client) {
        try {
            final HttpURLConnection connection = (HttpURLConnection) new URL(remoteApi.newToken(username)).openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("X-Forwarded-For", client);
            connection.setRequestProperty("User-Agent", userAgent);
            final String response = CharStreams.toString(new InputStreamReader(connection.getInputStream()));
            return new OrHttpError<>(ItemCredentials.UserToken.decodeLoginParam(new ClientUser(username), response));
        } catch (IOException e) {
            Log.warn("Error communicating with remote server", e);
            return gatewayTimeout();
        }
    }

    @Override
    protected LoginState getUserTokenState(IsUser user, long id, String secret) {
        return this.<LoginState>transactedOrError(session -> {
            final RemoteUserAuthToken token = session.get(RemoteUserAuthToken.class, id);

            if (token != null && (!token.getValue().equals(secret) || !token.getUser().getName().equals(user.getName()))) {
                return LoginState.INVALID_TOKEN;
            }

            if (token == null || !token.getValid()) {
                boolean[] verified = new boolean[1];
                remoteApi.ping(ResponseHandlers.withCredentials(new ItemCredentials.UserToken(user, id, secret), BASE_64_ENCODER,
                        connection -> {
                            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                                /* Try saving token in database, ignoring failures */
                                try {
                                    transacted(nested -> {
                                        RemoteUserAuthToken newToken = new RemoteUserAuthToken();
                                        User userEntity = this.getUserFromDB(user.getName(), nested).orElse(() -> {
                                            User newUser = User.withName(user.getName());
                                            nested.persist(newUser);
                                            return newUser;
                                        });
                                        newToken.setUser(userEntity);
                                        newToken.setValid(true);
                                        newToken.setValue(secret);
                                        newToken.setId(id);
                                        nested.persist(newToken);

                                        return null; // unused return value required by transacted()
                                    });
                                } catch (Exception e) {
                                    Log.warn("Failed saving UserAuthToken after successful connection", e);
                                }
                                verified[0] = true;
                            } else {
                                Log.error("Failed verifying user token: server returned HTTP status code " + connection.getResponseCode());
                            }
                        }));
                if (verified[0]) {
                    return LoginState.LOGGED_IN;
                } else {
                    // either invalid or waiting (maybe we could use different status code to distinguish?)
                    // waiting is expected to be the most common case, if 'invalid' then the login() call will notify
                    return LoginState.WAITING_FOR_VALIDATION;
                }
            }

            return LoginState.LOGGED_IN;
        }).orElse(()-> LoginState.INVALID_TOKEN);
    }

    @Override
    protected OrHttpError<? extends BackendItem> loadItem(ForwarderDomain session, long id, List<ItemCredentials> credentials) {
        RemoteItem item = session.get(RemoteItem.class, id);
        if (item == null) {
            // item not found locally, try from remote server
            return deserialiseStream(remoteApi.getImageUrl(id, credentials), credentials)
                    .flatMap(remoteList -> {
                        switch (remoteList.rows.size()) {
                            case 0:
                                Log.warn("Remote returned empty list to single-item query (should return 404 instead!)");
                                return notFound(id);
                            case 1:
                                final BackendItem remote = Iterables.getOnlyElement(remoteList.rows);
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
                                Log.warn("Remote returned " + remoteList.rows.size() + " elements to single-item query");
                                return internalServerError();
                        }
                    });
        } else {
            try {
                for (ItemCredentials c : credentials) {
                    verifyCredentials(c, item);
                }
            } catch (AccessDeniedException e) {
                return forbidden(false);
            }
            return new OrHttpError<>(item);
        }
    }

    private interface ExtraDataReader<T, R> {
        R buildResult(List<T> rows, LineNumberReader reader) throws IOException;
    }

    private <T, R> OrHttpError<R> deserialiseStream(String url, List<ItemCredentials> credentials,
                                                    Csv<? super T> csv, Supplier<T> factory, ExtraDataReader<T, R> extra) {
        try {
            final URLConnection connection = new URL(url).openConnection();
            credentials.forEach(c -> c.setHeaders(connection, BASE_64_ENCODER));
            connection.setRequestProperty("Accept", "text/csv");
            try (Reader reader = new InputStreamReader(connection.getInputStream())) {
                final int responseCode = ((HttpURLConnection) connection).getResponseCode();
                if (responseCode < 200 || responseCode >= 300) {
                    return passHttpError(reader, responseCode);
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

    private <R> OrHttpError<R> passHttpError(Reader reader, int responseCode) {
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

    private OrHttpError<ItemSet> deserialiseStream(String url, List<ItemCredentials> credentials) {
        return deserialiseStream(url, credentials, BackendItem.CSV, RemoteItem::new, (rows, reader) -> {
            String title = "";
            String description = "";
            String totalString = Integer.toString(rows.size());

            String line;
            while ((line = reader.readLine()) != null) {
                title = deserialiseAttribute(line, "Title", title);
                description = deserialiseAttribute(line, "Description", description);
                totalString = deserialiseAttribute(line, "Total", totalString);
            }
            return new ItemSet(Integer.parseInt(totalString), rows, title, description);
        });
    }

    private String deserialiseAttribute(String line, String attribute, String defaultValue) {
        if (line.startsWith(attribute +":")) {
            return CsvEncoding.unslash(line.substring(attribute.length() + 1));
        } else {
            return defaultValue;
        }
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
                remoteApi.getImageUrl(item.getServerId(), ImmutableList.of(credentials)),
                item.getType().asMime());
    }

    @Override
    protected OrHttpError<Long> upload(ForwarderDomain session, ItemCredentials.UserCredentials user, InputStream data, String folder, String path,
                                       ItemMetadata metadata) throws IOException {
        long[] id = new long[1];
        remoteApi.upload(user.user.getName(), folder, path,
                ResponseHandlers.withCredentials(user, BASE_64_ENCODER,
                ApiInvokers.upload(metadata, data, result -> id[0] = result)));
        if (id[0] > 0) {
            return new OrHttpError<>(id[0]);
        } else {
            // TODO retrieve remote status in the last parameter of the ApiInvokers method.
            // as it's all actually synchronous that should really be returned by the remoteApi.upload() call instead!
            return internalServerError();
        }
    }

    @Override
    protected void putTags(ForwarderDomain session, long id, String tags, ItemCredentials credentials) throws IOException {
        remoteApi.putTags(id, ResponseHandlers.withCredentials(credentials, BASE_64_ENCODER, connection -> {
            connection.setDoOutput(true);
            try (Writer w = new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8)) {
                w.write(tags);
            }
            if (connection.getResponseCode() >= 200 && connection.getResponseCode() <= 299) {
                super.putTags(session, id, tags, credentials);
            }
        }));
    }

    @Override
    protected void setItemDescription(ForwarderDomain session, long id, String description, ItemCredentials credentials) throws IOException {
        remoteApi.setItemDescription(id, ResponseHandlers.withCredentials(credentials, BASE_64_ENCODER, connection -> {
            connection.setDoOutput(true);
            try (Writer w = new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8)) {
                w.write(description);
            }
            if (connection.getResponseCode() >= 200 && connection.getResponseCode() <= 299) {
                super.setItemDescription(session, id, description, credentials);
            }
        }));
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

            final File tempFile = File.createTempFile("cloudspill", null);

            try (InputStream remoteInput = connection.getInputStream();
                 OutputStream clientOutput = res.raw().getOutputStream();
                 OutputStream cacheOutput = new FileOutputStream(tempFile)) {

                while (true) {
                    int r = remoteInput.read(buffer);
                    if (r == -1) {
                        break;
                    }

                    clientOutput.write(buffer, 0, r);
                    cacheOutput.write(buffer, 0, r);
                }
            }
            tempFile.renameTo(cache);
        }

        return "";
    }

    @Override
    protected OrHttpError<String> ping(ForwarderDomain session, ItemCredentials.UserCredentials credentials) {
        try {
            final URLConnection connection = new URL(remoteApi.ping()).openConnection();
            credentials.setHeaders(connection, Base64.getEncoder()::encodeToString);
            try (Reader reader = new InputStreamReader(connection.getInputStream())) {
                final int responseCode = ((HttpURLConnection) connection).getResponseCode();
                if (responseCode < 200 || responseCode >= 300) {
                    return passHttpError(reader, responseCode);
                }
                String info = CharStreams.toString(reader);

                final OrHttpError<User> userFromDB = getUserFromDB(credentials.user.getName(), session);

                if (!userFromDB.hasValue()) {
                    User u = User.withName(credentials.user.getName());

                    credentials.match(new ItemCredentials.Matcher<RuntimeException>() {
                        @Override
                        public void when(ItemCredentials.UserPassword password) {
                            String salt = BCrypt.gensalt();
                            u.setSalt(salt);
                            u.setPass(BCrypt.hashpw(requireNotNull(password.getPassword()), salt));
                        }

                        @Override
                        public void when(ItemCredentials.UserToken token) {

                        }

                        @Override
                        public void when(ItemCredentials.PublicAccess pub) {

                        }

                        @Override
                        public void when(ItemCredentials.ItemKey key) {

                        }
                    });

                    session.persist(u);
                } // TODO support changing password?

                return new OrHttpError<>(info);
            }
        } catch (IOException e) {
            Log.error(e.toString());
            return gatewayTimeout();
        }
    }

    @Override
    protected ItemQueryLoader getQueryLoader(ForwarderDomain session, ItemCredentials credentials) {
        return criteria -> deserialiseStream(criteria.getUrl(remoteApi), ImmutableList.of(credentials));
    }

    @Override
    protected Java8SearchCriteria<BackendItem> loadGallery(ForwarderDomain session, long partId) {
        return new GalleryPartReference(partId);
    }

    @Override
    protected OrHttpError<GalleryListData> galleryList(ItemCredentials credentials, ForwarderDomain domain) {
        return deserialiseGalleryList(credentials, remoteApi.galleryListPage(credentials));
    }

    @Override
    protected OrHttpError<String> title() {
        // TODO load actual title
        return new OrHttpError<>("Welcome to CloudSpill");
    }

    @Override
    protected OrHttpError<GalleryListData> dayList(ItemCredentials credentials, ForwarderDomain domain, int year) {
        return deserialiseGalleryList(credentials, remoteApi.dayListPage(year));
    }

    private OrHttpError<GalleryListData> deserialiseGalleryList(ItemCredentials credentials, String url) {
        return deserialiseStream(
                url,
                ImmutableList.of(credentials),
                GalleryListPage.Element.CSV,
                GalleryListPage.Element::new,
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
