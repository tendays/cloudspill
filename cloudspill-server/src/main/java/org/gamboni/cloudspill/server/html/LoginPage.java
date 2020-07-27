package org.gamboni.cloudspill.server.html;

import org.gamboni.cloudspill.server.config.BackendConfiguration;
import org.gamboni.cloudspill.shared.api.ItemCredentials;

/**
 * @author tendays
 */
public class LoginPage extends AbstractPage {
    private final String title;
    private final State state;
    private final ItemCredentials.UserCredentials credentials;

    public enum State {
        DISCONNECTED,
        INVALID_TOKEN,
        WAITING_FOR_VALIDATION,
        LOGGED_IN
    }

    public LoginPage(BackendConfiguration configuration, String title, State state, ItemCredentials.UserCredentials credentials) {
        super(configuration);
        this.title = title;
        this.state = state;
        this.credentials = credentials;
    }

    @Override
    protected String getTitle() {
        return title;
    }

    @Override
    protected String getPageUrl() {
        return configuration.getPublicUrl();
    }

    @Override
    protected HtmlFragment scripts() {
        return tag("script", "type='text/javascript' src="+ quote(api.loginJS()), "");
    }

    @Override
    protected HtmlFragment getBody(ItemCredentials.AuthenticationStatus authStatus) {
        if (state == State.DISCONNECTED || state == State.INVALID_TOKEN) {
            return tag("form", "class='login' onsubmit=" + quote("login(getElementById('username').value, '" +
                            api.newToken("%s") + "', '" + api.login("%s") + "'); event.preventDefault()"),
                    loginMessage("Enter your username to log in"),
                    unclosedTag("input type='text' id='username'"),
                    unclosedTag("input type='submit' value='GO'")
            );
        } else if (state == State.WAITING_FOR_VALIDATION) {
            return HtmlFragment.concatenate(
                    tag("div", "class='login-message'", "Hello "+ credentials.user.getName() +
                    ", your personal token number is "+ ((ItemCredentials.UserToken)credentials).id +"."),
                    loginMessage("Please ask an administrator to let you in."),
                    loginMessage("Alternatively, if you're already logged in on another device, you can validate this token yourself from there"));
        } else if (state == State.LOGGED_IN) {
            return HtmlFragment.concatenate(
                    loginMessage("Hello "+ credentials.user.getName() +", you are now successfully logged in."),
                    loginMessage("Please make sure your browser is set up to save cookies to avoid getting logged out when you close your browser."));
        } else {
            throw new IllegalStateException();
        }
    }

    private HtmlFragment loginMessage(String message) {
        return tag("div", "class='login-message'", message);
    }
}
