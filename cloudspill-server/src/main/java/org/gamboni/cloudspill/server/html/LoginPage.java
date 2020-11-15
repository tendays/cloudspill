package org.gamboni.cloudspill.server.html;

import org.gamboni.cloudspill.server.config.BackendConfiguration;
import org.gamboni.cloudspill.shared.api.ItemCredentials;
import org.gamboni.cloudspill.shared.api.LoginState;

import java.time.LocalDate;

/**
 * @author tendays
 */
public class LoginPage extends AbstractRenderer<LoginPage.Model> {
    public static class Model extends OutputModel {
        private final String title;
        private final LoginState state;

        public Model(ItemCredentials credentials, String title, LoginState state) {
            super(credentials);
            this.title = title;
            this.state = state;
        }
    }
    public LoginPage(BackendConfiguration configuration) {
        super(configuration);
    }

    @Override
    protected String getTitle(Model model) {
        return model.title;
    }

    @Override
    protected String getPageUrl(Model model) {
        return configuration.getPublicUrl();
    }

    @Override
    protected HtmlFragment scripts() {
        return tag("script", "type='text/javascript' src="+ quote(api.loginJS()), "");
    }

    @Override
    protected String onLoad(Model model) {
        if (model.state == LoginState.WAITING_FOR_VALIDATION) {
            return "waitForValidation('"+ ((ItemCredentials.UserToken)model.credentials).encodeLoginParam() +"', '"+
                    this.api.login()
                    +"')";
        } else {
            return super.onLoad(model);
        }
    }

    @Override
    protected HtmlFragment getBody(Model model) {
        HtmlFragment nameElement = tag("span", "name='name'",
                (model.credentials instanceof ItemCredentials.UserCredentials) ? ((ItemCredentials.UserCredentials) model.credentials).user.getName() : "stranger");
        HtmlFragment tokenIdElement = tag("span", "name='tokenId'",
                (model.credentials instanceof ItemCredentials.UserToken) ? String.valueOf(((ItemCredentials.UserToken) model.credentials).id) : "unknown");
        return HtmlFragment.concatenate(
                tag("form", hiddenUnless(model.state == LoginState.DISCONNECTED || model.state == LoginState.INVALID_TOKEN) +
                                "id='disconnected' class='login' onsubmit=" + quote("login(getElementById('username').value, '" +
                                api.newToken("{username}") + "', '" + api.login() + "'); event.preventDefault()"),
                        loginMessage("Enter your username to log in"),
                        unclosedTag("input type='text' id='username'"),
                        unclosedTag("input type='submit' value='GO'")
                ),
                tag("div", hiddenUnless(model.state == LoginState.WAITING_FOR_VALIDATION) + "id='waiting'",
                        tag("div", "class='login-message'",
                                HtmlFragment.escape("Hello "),
                                nameElement,
                                HtmlFragment.escape(", your personal token number is "),
                                tokenIdElement,
                                HtmlFragment.escape(".")),
                        loginMessage("Please ask an administrator to let you in."),
                        loginMessage("Alternatively, if you're already logged in on another device, you can validate this token yourself from there"),
                        unclosedTag("input type='button' value='CANCEL' onclick="+ quote("logout('"+ api.logout() +"')"))),
                tag("div", hiddenUnless(model.state == LoginState.LOGGED_IN) + "id='logged_in'",
                        loginMessage(
                                HtmlFragment.escape("Hello "),
                                nameElement,
                                HtmlFragment.escape(", you are now successfully logged in.")),
                        loginMessage("Please make sure your browser is set up to save cookies to ensure you stay logged in when you close your browser."),
                        unclosedTag("input type='button' value='LOG OUT' onclick="+ quote("logout('"+ api.logout() +"')")),
                        (model.state == LoginState.LOGGED_IN) ?
                                HtmlFragment.concatenate(
                                tag("div", tag("a", "href='"+ api.listTokens(
                                ((ItemCredentials.UserCredentials)model.credentials).user.getName()) +"'", "Manage your authentication tokens here")),
                                        tag("div", tag("a", "href='"+ api.getBaseUrl() +"year/"+ LocalDate.now().getYear() +"'", "This year's photos")),
                                        tag("div", tag("a", "href='"+ api.getBaseUrl() +"gallery/'", "All galleries")),
                                        tag("div", tag("a", "href='"+ api.getBaseUrl() +"tag/'", "All tags"))
                                        ) : HtmlFragment.EMPTY
                        ));
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
