package org.gamboni.cloudspill.server.html;

import com.google.common.collect.ImmutableList;

import org.gamboni.cloudspill.domain.UserAuthToken;
import org.gamboni.cloudspill.server.config.BackendConfiguration;
import org.gamboni.cloudspill.server.html.js.EditorSubmissionJs;
import org.gamboni.cloudspill.server.html.js.TokenValidationJs;
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
            super(ImmutableList.of(credentials));
            this.user = user;
            this.tokens = ImmutableList.copyOf(tokens);
        }
    }

    public TokenListPage(BackendConfiguration configuration) {
        super(configuration);
    }


    @Override
    protected HtmlFragment scripts() {
        return HtmlFragment.concatenate(
                tag("script", "type='text/javascript' src=" + quote(api.tokenListJS()), ""),
                tag("script", "type='text/javascript' src=" + quote(api.getUrl(new TokenValidationJs(configuration))), ""));
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
                        {
                            final boolean isCurrentSession = (model.credentials instanceof ItemCredentials.UserToken) &&
                                    ((ItemCredentials.UserToken) model.credentials).id == token.getId();
                            final HtmlFragment deleteButton = button("delete-" + token.getId(), isCurrentSession ? "LOGOUT" : "DELETE",
                                    "del('" + token.getUser().getName() + "', " + token.getId() + ")");
                            final HtmlFragment validateButton = button("validate-" + token.getId(), "VALIDATE",
                                    "validate('" + token.getUser().getName() + "', " + token.getId() + ")");
                            return tag("tr", "id='row-" + token.getId() + "'",
                                    tag("td", "class='id-cell'", String.valueOf(token.getId())),
                                    tag("td", token.getDescription()),
                                    tag("td",
                                            tag("span",
                                                    "id='state-" + token.getId() + "' class=" + (token.getValid() ? "'valid-chip'" : "'invalid-chip'"),
                                                    ""),
                                            HtmlFragment.escape(" "),
                                            isCurrentSession ?
                                            tag("span", "class='current-session-chip'", "") : HtmlFragment.EMPTY),
                                    tag("td", "style='white-space:nowrap'",
                                            deleteButton,
                                            token.getValid() ?
                                                    HtmlFragment.EMPTY : HtmlFragment.concatenate(HtmlFragment.escape(" "), validateButton)
                                            ));
                        }
                )));
    }
}
