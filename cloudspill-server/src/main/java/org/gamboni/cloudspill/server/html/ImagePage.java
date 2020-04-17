/**
 * 
 */
package org.gamboni.cloudspill.server.html;

import com.google.common.base.MoreObjects;

import org.gamboni.cloudspill.domain.BackendItem;
import org.gamboni.cloudspill.server.config.BackendConfiguration;
import org.gamboni.cloudspill.server.query.ServerSearchCriteria;
import org.gamboni.cloudspill.shared.api.CloudSpillApi;
import org.gamboni.cloudspill.shared.api.ItemCredentials;
import org.gamboni.cloudspill.shared.domain.ItemType;

import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author tendays
 *
 */
public class ImagePage extends AbstractPage {
	private final BackendItem item;

	public ImagePage(BackendConfiguration configuration, BackendItem item) {
		super(configuration, configuration.getCss());

		this.item = item;
	}
	
	public String getTitle() {
		return item.getUser() +"/"+ item.getFolder() +"/"+ item.getPath();
	}

	public String getPageUrl() {
		return api.getPublicImagePageUrl(this.item);
	}

	public Optional<String> getThumbnailUrl() {
		return Optional.of(api.getThumbnailUrl(item, CloudSpillApi.Size.IMAGE_THUMBNAIL));
	}

	public String getImageUrl() {
		return api.getImageUrl(item);
	}

	public HtmlFragment getBody(ItemCredentials.AuthenticationStatus authStatus) {
					return HtmlFragment.concatenate(unclosedTag((item.getType() == ItemType.VIDEO ? "video controls " : "img ") +
							"class='image' src="+ quote(getImageUrl())),
						tag("div", "class='metadata'", HtmlFragment.escape("By: "+ item.getUser()),
								dateLine(authStatus)),
								tag("div", "class='metadata'", MoreObjects.firstNonNull(item.getDescription(), "")),
								tag("div", "class='metadata'",
										new HtmlFragment(item.getTags().stream()
										.map(tag -> tagElement(tag, authStatus).toString())
										.collect(Collectors.joining(" ")))));
	}

	private HtmlFragment tagElement(String tag, ItemCredentials.AuthenticationStatus authStatus) {
		if (authStatus == ItemCredentials.AuthenticationStatus.ANONYMOUS) {
			return tag("span class='tag'", tag);
		} else {
			return tag("a class='tag' href="+ quote(
					ServerSearchCriteria.ALL.withTag(tag).getUrl(api)),
					tag);
		}
	}

	private HtmlFragment dateLine(ItemCredentials.AuthenticationStatus authStatus) {
		if (item.getDate() == null) { return HtmlFragment.EMPTY; }
		String dateString = item.getDate()
				.format(DateTimeFormatter.ofPattern("YYYY-MM-dd HH:mm:ss"));
		return (authStatus == ItemCredentials.AuthenticationStatus.ANONYMOUS ? tag("span","class='date'", dateString) : tag("a", "class='date' href="+
				quote(ServerSearchCriteria.ALL.at(item.getDate().toLocalDate()).getUrl(api)),
				dateString));
	}
	
	
}
