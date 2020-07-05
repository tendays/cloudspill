package org.gamboni.cloudspill.shared.api;

import org.gamboni.cloudspill.shared.domain.ClientUser;
import org.gamboni.cloudspill.shared.domain.InvalidPasswordException;
import org.gamboni.cloudspill.shared.domain.IsItem;
import org.gamboni.cloudspill.shared.domain.IsUser;
import org.gamboni.cloudspill.shared.domain.Items;
import org.gamboni.cloudspill.shared.util.Func;
import org.gamboni.cloudspill.shared.util.Log;
import org.gamboni.cloudspill.shared.util.Splitter;

import java.net.URLConnection;
import java.util.List;
import java.util.function.Function;

/** Specifies a way to access an Item: either by logging in (user+password), or by supplying an item-specific key String.
 *
 * @author tendays
 */
public interface ItemCredentials {

    public interface Matcher<E extends Throwable> {
        void when(UserPassword password) throws E;
        void when(UserToken token) throws E;
        void when(PublicAccess pub) throws E;
        void when(ItemKey key) throws E;
    }

    <E extends Throwable> void match(Matcher<E> matcher) throws E;

    AuthenticationStatus getAuthStatus();

    /** If these credentials work by setting an HTTP header, do so in this method. Otherwise,
     * this method should just do nothing.
     */
    void setHeaders(URLConnection connection, Base64Encoder b64);

    public enum AuthenticationStatus {
        LOGGED_IN {
            @Override
            protected ItemCredentials credentialsForNonPublicItem(IsItem item) {
                return new UserPassword();
            }
        }, ANONYMOUS {
            @Override
            protected ItemCredentials credentialsForNonPublicItem(IsItem item) {
                return new ItemKey(item.getChecksum());
            }
        };

        public ItemCredentials credentialsFor(IsItem item) {
            return Items.isPublic(item) ? new PublicAccess() :
                    credentialsForNonPublicItem(item);
        }

        protected abstract ItemCredentials credentialsForNonPublicItem(IsItem item);
    }

    String getQueryString();

    /** String to put after the server url and before the api paths ("public/" or "") */
    String getUrlPrefix();

    class UserPassword extends UserCredentials {
        public final String password;

        /** Use this constructor when you only need to generate a URL in an HTML page. The browser will take care of passing the actual credentials. */
        public UserPassword() {
            this.password = null;
        }

        /** Use this constructor when you actually need to send a request to the server. */
        public UserPassword(IsUser user, String password) {
            super(user);
            this.password = password;
        }

        @Override
        public <E extends Throwable> void match(Matcher<E> matcher) throws E {
            matcher.when(this);
        }

        public String getPassword() {
            if (password == null) {
                throw new IllegalStateException("No password available");
            }
            return password;
        }

        @Override
        public void setHeaders(URLConnection connection, Base64Encoder b64) {
            final String credentials = user.getName() + ":" + getPassword();

            connection.setRequestProperty("Authorization", "Basic " + b64.encode(credentials.getBytes()));
        }
    }

    class UserToken extends UserCredentials {
        public static final String AUTH_TYPE = "Token";
        public final long id;
        public final String secret;

        public UserToken(IsUser user, long id, String secret) {
            super(user);
            this.id = id;
            this.secret = secret;
        }

        public UserToken(IsUser user, String token) {
            super(user);
            final Splitter splitter = new Splitter(token, ':');
            this.id = splitter.getLong();
            this.secret = splitter.getString();
        }

        @Override
        public <E extends Throwable> void match(Matcher<E> matcher) throws E {
            matcher.when(this);
        }

        @Override
        public void setHeaders(URLConnection connection, Base64Encoder b64) {
            connection.setRequestProperty("Authorization", AUTH_TYPE +" "+ encodeCookie());
        }

        public String encodeCookie() {
            return user.getName() + ":" + id + ":" + secret;
        }

        public String encodeLoginParam() {
            return id +":"+ secret;
        }

        public static UserToken decode(String cookie) {
            Splitter splitter = new Splitter(cookie, ':');
            return new UserToken(
                    new ClientUser(splitter.getString()),
                    splitter.getLong(),
                    splitter.getString());
        }
    }

    abstract class UserCredentials implements ItemCredentials {
        public final IsUser user;
        protected UserCredentials() {
            this.user = null;
        }

        protected UserCredentials(IsUser user) {
            this.user = user;
        }

        @Override
        public AuthenticationStatus getAuthStatus() {
            return AuthenticationStatus.LOGGED_IN;
        }

        @Override
        public String getQueryString() {
            return "";
        }

        @Override
        public String getUrlPrefix() {
            return "";
        }
    }

    class ItemKey implements ItemCredentials {
        public final String checksum;
        public ItemKey(String checksum) {
            this.checksum = checksum;
        }

        @Override
        public <E extends Throwable> void match(Matcher<E> matcher) throws E {
            matcher.when(this);
        }

        @Override
        public AuthenticationStatus getAuthStatus() {
            return AuthenticationStatus.ANONYMOUS;
        }

        @Override
        public void setHeaders(URLConnection connection, Base64Encoder b64) {
        }

        @Override
        public String getQueryString() {
            return "?key="+ checksum.replace("+", "%2B");
        }

        @Override
        public String getUrlPrefix() {
            return "";
        }
    }

    /** No credentials are needed because the item is public. */
    class PublicAccess implements ItemCredentials {

        @Override
        public AuthenticationStatus getAuthStatus() {
            return AuthenticationStatus.ANONYMOUS;
        }

        @Override
        public void setHeaders(URLConnection connection, Base64Encoder b64) {
        }

        @Override
        public <E extends Throwable> void match(Matcher<E> matcher) throws E {
            matcher.when(this);
        }

        @Override
        public String getQueryString() {
            return "";
        }

        @Override
        public String getUrlPrefix() {
            return "public/";
        }
    }
}
