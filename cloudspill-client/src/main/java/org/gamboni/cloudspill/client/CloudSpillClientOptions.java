/**
 * 
 */
package org.gamboni.cloudspill.client;

import com.google.common.collect.ImmutableList;

import org.gamboni.cloudspill.shared.api.CloudSpillApi;
import org.gamboni.cloudspill.shared.api.ItemCredentials;
import org.gamboni.cloudspill.shared.client.ResponseHandler;
import org.gamboni.cloudspill.shared.domain.ClientUser;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
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
        private final String description;

        Key(String option, String description) {
            this.option = option;
            this.description = description;
        }
        abstract Optional<T> parse(OptionParser parser);
    }

    static class StringOption extends Key<String> {
        StringOption(String option, String description) {
            super(option, description);
        }
        Optional<String> parse(OptionParser parser) {
            if (parser.consume(option)) {
                return Optional.of(parser.next());
            } else {
                return Optional.empty();
            }
        }
    }

    private static final boolean isTerminal = (System.console() != null);

    /** Server url */
    static final Key<String> server = new StringOption("--server", "server hostname");
    /** Username */
    static final Key<String> user = new StringOption("--user", "username to login with");
    /** Password */
    static final Key<String> password = new StringOption("--password", "password to login with");
    /** Folder name to use in the server */
    static final Key<String> folder = new StringOption("--folder", "server-side folder name for upload");

    static final List<Key<?>> options = ImmutableList.of(server, user, password, folder);

    private final Map<Key<?>, Object> values = new HashMap<>();
    private ItemCredentials credentials = null; // lazily initialised

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

    <T> T require(Key<T> key) {
        return this.get(key).orElseGet(() -> {
            if (isTerminal) {
                System.out.println("Please provide: "+ key.description);
                System.out.print(key.option +" ");
                try {
                    String value = new LineNumberReader(new InputStreamReader(System.in)).readLine();
                    // pretend the option and the user-provided value were passed on command line
                    return key.parse(new OptionParser(key.option, value)).get();
                } catch (IOException e) {
                    System.err.println("Unable to read from terminal");
                    throw new IllegalStateException();
                }
            } else {
                System.err.println("Missing "+ key.description +". You may set it with "+ key.option);
                throw new IllegalArgumentException();
            }
        });
    }

    private ItemCredentials getCredentials() {
        if (this.credentials == null) {
            this.credentials = CloudSpill.loadToken()
                    .<ItemCredentials>map(t -> t) // identity mapping to change type; could do an unchecked cast instead
                    .orElseGet(() ->
                            get(user).<ItemCredentials>flatMap(givenUser ->
                                    get(password).map(givenPassword ->
                                            new ItemCredentials.UserPassword(new ClientUser(givenUser), givenPassword)))
                                    .orElse(new ItemCredentials.PublicAccess()));
        }
        return this.credentials;
    }

    public String getUsername() {
        ItemCredentials c = getCredentials();

        if (c instanceof ItemCredentials.UserCredentials) {
            return ((ItemCredentials.UserCredentials)c).user.getName();
        } else {
            throw new IllegalArgumentException("No user specified");
        }
    }

    public CloudSpillApi<ResponseHandler> getServerApi() {
        // include authentication header if both user and password specified
        ItemCredentials credentials = getCredentials();

        return CloudSpillApi.authenticatedClient(
                this.require(server),
                credentials,
                Base64.getEncoder()::encodeToString);
    }
}
