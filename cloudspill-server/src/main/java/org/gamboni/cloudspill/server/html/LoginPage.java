package org.gamboni.cloudspill.server.html;

import org.gamboni.cloudspill.server.config.BackendConfiguration;
import org.gamboni.cloudspill.shared.api.ItemCredentials;

/**
 * @author tendays
 */
public class LoginPage extends AbstractPage {
    private final String title;

    public LoginPage(BackendConfiguration configuration, String title) {
        super(configuration);
        this.title = title;
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
        return tag("form", "class='login' onsubmit="+quote("login(getElementById('username').value, '"+
                        api.newToken("%s")+ "', '"+ api.login("%s") +"'); event.preventDefault()"),
                tag("div", "class='login-message'", "Enter your username to log in"),
                unclosedTag("input type='text' id='username'"),
                unclosedTag("input type='submit' value='GO'")
        );
    }
}
