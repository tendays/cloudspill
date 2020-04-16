package org.gamboni.cloudspill.shared.api;

import org.gamboni.cloudspill.shared.domain.IsItem;
import org.gamboni.cloudspill.shared.domain.IsUser;
import org.gamboni.cloudspill.shared.domain.Items;
import org.gamboni.cloudspill.shared.util.Log;

import java.net.URLConnection;
import java.util.Base64;

/** Specifies a way to access an Item: either by logging in (user+password), or by supplying an item-specific key String.
 *
 * @author tendays
 */
public interface ItemCredentials {

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

    boolean verify(IsItem item);

    String getQueryString();

    /** String to put after the server url and before the api paths ("public/" or "") */
    String getUrlPrefix();

    class UserPassword implements ItemCredentials {
        public final IsUser user;
        private final String password;
        /** Use this constructor when you only need to generate a URL in an HTML page. The browser will take care of passing the actual credentials. */
        public UserPassword() {
            this.user = null;
            this.password = null;
        }
        /** Use this constructor when you actually need to send a request to the server. */
        public UserPassword(IsUser user, String password) {
            this.user = user;
            this.password = password;
        }

        @Override
        public AuthenticationStatus getAuthStatus() {
            return AuthenticationStatus.LOGGED_IN;
        }

        @Override
        public boolean verify(IsItem item) {
            return user.verifyPassword(password);
        }

        @Override
        public String getQueryString() {
            return "";
        }

        @Override
        public String getUrlPrefix() {
            return "";
        }

        @Override
        public void setHeaders(URLConnection connection, Base64Encoder b64) {
            final String credentials = user.getName() + ":" + password;

            connection.setRequestProperty("Authorization", "Basic " + b64.encode(credentials.getBytes()));
        }
    }

    class ItemKey implements ItemCredentials {
        private final String checksum;
        public ItemKey(String checksum) {
            this.checksum = checksum;
        }

        @Override
        public AuthenticationStatus getAuthStatus() {
            return AuthenticationStatus.ANONYMOUS;
        }

        @Override
        public void setHeaders(URLConnection connection, Base64Encoder b64) {
        }

        @Override
        public boolean verify(IsItem item) {
            if (checksum.equals(item.getChecksum())) {
                return true;
            } else {
                Log.warn("Bad key value. Expected " + item.getChecksum() + ", got " + checksum);
                return false;
            }
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
        public boolean verify(IsItem item) {
            return Items.isPublic(item);
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
