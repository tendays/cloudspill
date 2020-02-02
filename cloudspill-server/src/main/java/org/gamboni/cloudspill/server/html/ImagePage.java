/**
 * 
 */
package org.gamboni.cloudspill.server.html;

import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.stream.Collectors;

import org.gamboni.cloudspill.domain.Item;
import org.gamboni.cloudspill.domain.User;
import org.gamboni.cloudspill.server.CloudSpillServer;
import org.gamboni.cloudspill.server.ServerConfiguration;

import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;

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
/*
 * <html prefix="og: http://ogp.me/ns#">
  <head>
    <title>Test image</title>
    <meta property="og:title" content="Test image" />
    <meta property="og:type" content="article" />
    <meta property="og:url" content="http://jeera.gamboni.org/~tendays/test-page.html" />
    <meta property="og:image" content="http://jeera.gamboni.org/~tendays/thumbnail.png" />
    <style>
      .image {
      max-width: 90vw;
      max-height: 90vh;
      }
    </style>
  </head>
  <body>
    <img class="image" src="http://jeera.gamboni.org:4567/item/28?key=HzaWGICDCXNe9//nFCN4OQ==">
  </body>
</html>
 */

	public String getTitle() {
		return item.getUser() +"/"+ item.getFolder() +"/"+ item.getPath();
	}

	public static String getUrl(ServerConfiguration configuration, Item item, User user) {
		if (user == null) {
			if (item.isPublic()) {
				return configuration.getPublicUrl() + "/public/item/" + item.getId() + CloudSpillServer.ID_HTML_SUFFIX;
			} else {
				return configuration.getPublicUrl() + "/item/" + item.getId() + CloudSpillServer.ID_HTML_SUFFIX +
						accessKeyQueryString(item);
			}
		} else {
			return configuration.getPublicUrl() + "/item/" + item.getId() + CloudSpillServer.ID_HTML_SUFFIX;
		}
	}

	public String getPageUrl(User user) {
		return getUrl(this.configuration, this.item, user);
	}

	public Optional<String> getThumbnailUrl() {
		return Optional.of(getThumbnailUrl(item));
	}

	public String getImageUrl() {
		return getImageUrl(item);
	}

	public String getBody(User user) {
					return	unclosedTag("img class='image' src="+ quote(getImageUrl())) +
						tag("div", "class='metadata'", "By: "+ item.getUser() +
								dateLine(user)) +
								tag("div", "class='metadata'",
										MoreObjects.firstNonNull(item.getDescription(), "")) +
								tag("div", "class='metadata'",
										item.getTags().stream()
										.map(tag -> tagElement(tag, user))
										.collect(Collectors.joining(" ")));
	}

	private String tagElement(String tag, User user) {
		if (user == null) {
			return tag("span class='tag'", tag);
		} else {
			return tag("a class='tag' href="+ quote(getGalleryUrl(
					new SearchCriteria(ImmutableSet.of(tag),null, null))),
					tag);
		}
	}

	private String dateLine(User user) {
		if (item.getDate() == null) { return ""; }
		String dateString = "Date: "+ item.getDate()
				.format(DateTimeFormatter.ofPattern("YYYY-MM-dd HH:mm:ss"));
		return (user == null ? tag("span class='date'", dateString) : tag("a class='date' href="+
				quote(getGalleryUrl(new SearchCriteria(ImmutableSet.of(), item.getDate().toLocalDate(), item.getDate().toLocalDate()))),
				dateString));
	}
	
	
}
