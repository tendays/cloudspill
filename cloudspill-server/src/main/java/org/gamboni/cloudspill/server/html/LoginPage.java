package org.gamboni.cloudspill.server.html;

import org.gamboni.cloudspill.server.config.BackendConfiguration;
import org.gamboni.cloudspill.shared.api.CloudSpillApi;
import org.gamboni.cloudspill.shared.api.ItemCredentials;
import org.gamboni.cloudspill.shared.api.LoginState;

/**
 * @author tendays
 */
public class LoginPage extends AbstractPage {
    private final String title;
    private final LoginState state;

    public LoginPage(BackendConfiguration configuration, String title, LoginState state, ItemCredentials credentials) {
        super(configuration, credentials);
        this.title = title;
        this.state = state;
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
    protected String onLoad(ItemCredentials c) {
        if (state == LoginState.WAITING_FOR_VALIDATION) {
            return "waitForValidation('"+ ((ItemCredentials.UserToken)credentials).encodeLoginParam() +"', '"+
                    this.api.login()
                    +"')";
        } else {
            return super.onLoad(c);
        }
    }

    @Override
    protected HtmlFragment getBody(ItemCredentials.AuthenticationStatus authStatus) {
        HtmlFragment nameElement = tag("span", "name='name'",
                (credentials instanceof ItemCredentials.UserCredentials) ? ((ItemCredentials.UserCredentials) credentials).user.getName() : "stranger");
        HtmlFragment tokenIdElement = tag("span", "name='tokenId'",
                (credentials instanceof ItemCredentials.UserToken) ? String.valueOf(((ItemCredentials.UserToken) credentials).id) : "unknown");
        return HtmlFragment.concatenate(
                tag("form", hiddenUnless(state == LoginState.DISCONNECTED || state == LoginState.INVALID_TOKEN) +
                                "id='disconnected' class='login' onsubmit=" + quote("login(getElementById('username').value, '" +
                                api.newToken("{username}") + "', '" + api.login() + "'); event.preventDefault()"),
                        loginMessage("Enter your username to log in"),
                        unclosedTag("input type='text' id='username'"),
                        unclosedTag("input type='submit' value='GO'")
                ),
                tag("div", hiddenUnless(state == LoginState.WAITING_FOR_VALIDATION) + "id='waiting'",
                        tag("div", "class='login-message'",
                                HtmlFragment.escape("Hello "),
                                nameElement,
                                HtmlFragment.escape(", your personal token number is "),
                                tokenIdElement,
                                HtmlFragment.escape(".")),
                        loginMessage("Please ask an administrator to let you in."),
                        loginMessage("Alternatively, if you're already logged in on another device, you can validate this token yourself from there"),
                        unclosedTag("input type='button' value='CANCEL' onclick="+ quote("logout('"+ api.logout() +"')"))),
                tag("div", hiddenUnless(state == LoginState.LOGGED_IN) + "id='logged_in'",
                        loginMessage(
                                HtmlFragment.escape("Hello "),
                                nameElement,
                                HtmlFragment.escape(", you are now successfully logged in.")),
                        loginMessage("Please make sure your browser is set up to save cookies to ensure you stay logged in when you close your browser."),
                        unclosedTag("input type='button' value='LOG OUT' onclick="+ quote("logout('"+ api.logout() +"')"))));
    }

    private String hiddenUnless(boolean condition) {
        return (condition) ? "" : "style='display:none' ";
    }

    private HtmlFragment loginMessage(String message) {
        return loginMessage(HtmlFragment.escape(message));
    }

    private HtmlFragment loginMessage(HtmlFragment... content) {
        return tag("div", "class='login-message'", content);
    }
}
