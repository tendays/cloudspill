/**
 * 
 */
package org.gamboni.cloudspill.server.html;

import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.stream.Collectors;

import org.gamboni.cloudspill.domain.Item;
import org.gamboni.cloudspill.domain.User;
import org.gamboni.cloudspill.server.ServerConfiguration;
import org.gamboni.cloudspill.server.query.ServerSearchCriteria;
import org.gamboni.cloudspill.shared.api.CloudSpillApi;
import org.gamboni.cloudspill.shared.domain.ItemType;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;

import static org.gamboni.cloudspill.shared.api.CloudSpillApi.getGalleryUrl;

/**
 * @author tendays
 *
 */
public class ImagePage extends AbstractPage {
	private final Item item;
	
	private ImagePage(ServerConfiguration configuration, Item item) {
		super(configuration, configuration.getCss());

		this.item = item;
	}
	
	public static class Factory {
		private final ServerConfiguration configuration;
		@Inject
		public Factory(ServerConfiguration configuration) {
			this.configuration = configuration;
		}
		
		public ImagePage create(Item item) {
			return new ImagePage(configuration, item);
		}
	}

	public String getTitle() {
		return item.getUser() +"/"+ item.getFolder() +"/"+ item.getPath();
	}

	public String getPageUrl(User user) {
		if (user == null) {
			return configuration.getPublicUrl() + CloudSpillApi.getPublicImagePageUrl(this.item);
		} else {
			return configuration.getPublicUrl() + CloudSpillApi.getLoggedInImagePageUrl(this.item);
		}
	}

	public Optional<String> getThumbnailUrl() {
		return Optional.of(configuration.getPublicUrl() + CloudSpillApi.getThumbnailUrl(item, CloudSpillApi.Size.IMAGE_THUMBNAIL));
	}

	public String getImageUrl() {
		return configuration.getPublicUrl() + CloudSpillApi.getImageUrl(item);
	}

	public HtmlFragment getBody(User user) {
					return HtmlFragment.concatenate(unclosedTag((item.getType() == ItemType.VIDEO ? "video controls " : "img ") +
							"class='image' src="+ quote(getImageUrl())),
						tag("div", "class='metadata'", HtmlFragment.escape("By: "+ item.getUser()),
								dateLine(user)),
								tag("div", "class='metadata'", MoreObjects.firstNonNull(item.getDescription(), "")),
								tag("div", "class='metadata'",
										new HtmlFragment(item.getTags().stream()
										.map(tag -> tagElement(tag, user).toString())
										.collect(Collectors.joining(" ")))));
	}

	private HtmlFragment tagElement(String tag, User user) {
		if (user == null) {
			return tag("span class='tag'", tag);
		} else {
			return tag("a class='tag' href="+ quote(configuration.getPublicUrl() +
					new ServerSearchCriteria(null, null, null, ImmutableSet.of(tag), 0).getUrl()),
					tag);
		}
	}

	private HtmlFragment dateLine(User user) {
		if (item.getDate() == null) { return HtmlFragment.EMPTY; }
		String dateString = item.getDate()
				.format(DateTimeFormatter.ofPattern("YYYY-MM-dd HH:mm:ss"));
		return (user == null ? tag("span class='date'", dateString) : tag("a class='date' href="+
				quote(configuration.getPublicUrl() +
						new ServerSearchCriteria(item.getDate().toLocalDate(), item.getDate().toLocalDate(), null, ImmutableSet.of(), 0).getUrl()),
				dateString));
	}
	
	
}
