package org.gamboni.cloudspill.server.html;

import com.google.common.collect.ImmutableList;

import org.gamboni.cloudspill.domain.UserAuthToken;
import org.gamboni.cloudspill.server.config.BackendConfiguration;
import org.gamboni.cloudspill.shared.api.ItemCredentials;

import java.util.List;

/**
 * @author tendays
 */
public class TokenListPage extends AbstractRenderer<TokenListPage.Model> {

    public static class Model extends OutputModel {
        public final String user;
        public final ImmutableList<UserAuthToken> tokens;

        public Model(ItemCredentials credentials, String user, List<UserAuthToken> tokens) {
            super(credentials);
            this.user = user;
            this.tokens = ImmutableList.copyOf(tokens);
        }
    }

    public TokenListPage(BackendConfiguration configuration) {
        super(configuration);
    }

    @Override
    protected String getTitle(Model model) {
        return "Authentication Tokens";
    }

    @Override
    protected String getPageUrl(Model model) {
        return api.listTokens(model.user);
    }

    @Override
    protected HtmlFragment getBody(Model model) {
        return tag("table",
                tag("tr",
                        tag("th", "ID"),
                        tag("th", "Description"),
                        tag("th", "State"),
                        tag("th")),
                HtmlFragment.concatenate(model.tokens.stream().map(token ->
                        tag("tr",
                                tag("td", String.valueOf(token.getId())),
                                tag("td", token.getDescription()),
                                tag("td",
                                        tag("span",
                                                "class=" + (token.getValid() ? "'valid-chip'" : "'invalid-chip'"),
                                                token.getValid() ? "VALIDATED" : "NOT VALIDATED"))
                        )))
        );
    }
}
