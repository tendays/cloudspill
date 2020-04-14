/**
 * 
 */
package org.gamboni.cloudspill.client;

import com.google.common.collect.ImmutableList;

import org.gamboni.cloudspill.shared.api.CloudSpillApi;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Options that can be passed to the CloudSpill command line client. Eventually, those may be settable through a configuration file as well.
 *
 * @author tendays
 */
public class CloudSpillClientOptions {

    static abstract class Key<T> {
        protected final String option;
        Key(String option) {
            this.option = option;
        }
        abstract Optional<T> parse(OptionParser parser);
    }

    static class StringOption extends Key<String> {
        StringOption(String option) {
            super(option);
        }
        Optional<String> parse(OptionParser parser) {
            if (parser.consume(option)) {
                return Optional.of(parser.next());
            } else {
                return Optional.empty();
            }
        }
    }

    /** Server url */
    static final Key<String> server = new StringOption("--server");
    /** Username */
    static final Key<String> user = new StringOption("--user");
    /** Password */
    static final Key<String> password = new StringOption("--password");
    /** Folder name to use in the server */
    static final Key<String> folder = new StringOption("--folder");

    static final List<Key<?>> options = ImmutableList.of(server, user, password, folder);

    private final Map<Key<?>, Object> values = new HashMap<>();

    private <T> boolean apply(Key<T> key, OptionParser parser) {
        return key.parse(parser).map(value -> {
            values.put(key, value);
            return true;
        }).orElse(false);
    }

    static CloudSpillClientOptions from(OptionParser parser) {
        CloudSpillClientOptions result = new CloudSpillClientOptions();
        parsing: while (parser.hasNext()) {
            for (Key<?> option : options) {
                if (result.apply(option, parser)) {
                    continue parsing;
                }
            }
            break parsing;
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    <T> Optional<T> get(Key<T> key) {
        return Optional.ofNullable((T)values.get(key));
    }

    <T> T require(Key<T> key, String errorMessage) {
        return this.get(key).orElseThrow(() -> new IllegalArgumentException(errorMessage));
    }

    public CloudSpillApi getServerApi() {
        return new CloudSpillApi(this.require(server, "Server url not set"));
    }

    public HttpURLConnection openConnection(String url) {
        try {
            URLConnection connection = new URL(url).openConnection();

            // include authentication header if both user and password specified
            get(user).ifPresent(givenUser ->
                    get(password).ifPresent(givenPassword -> {
                        final String credentials = givenUser + ":" + givenPassword;
                        connection.setRequestProperty("Authorization", "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes()));
                    }));

            return (HttpURLConnection) connection;

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
