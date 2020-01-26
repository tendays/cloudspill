/**
 * 
 */
package org.gamboni.cloudspill.server.html;

import java.time.format.DateTimeFormatter;
import java.util.Optional;

import org.gamboni.cloudspill.domain.Item;
import org.gamboni.cloudspill.server.ServerConfiguration;

import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.inject.Inject;

/**
 * @author tendays
 *
 */
public class ImagePage extends AbstractPage {
	private final ServerConfiguration configuration;
	private final Item item;
	
	private ImagePage(ServerConfiguration configuration, Item item) {
		super(configuration.getCss());

		this.configuration = configuration;
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

	public String getPageUrl() {
		return configuration.getPublicUrl() + "/item/html/"+ item.getId() + accessKeyQueryString();
	}

	public Optional<String> getThumbnailUrl() {
		return Optional.of(configuration.getPublicUrl() + "/thumbs/300/"+ item.getId() + accessKeyQueryString());
	}

	public String getImageUrl() {
		return configuration.getPublicUrl() +"/item/"+ item.getId() + accessKeyQueryString();
	}

	private String accessKeyQueryString() {
		return "?key="+ item.getChecksum().replace("+", "%2B");
	}

	public String getBody() {
					return	unclosedTag("img class='image' src="+ quote(getImageUrl())) +
						tag("div", "class='metadata'", "By: "+ item.getUser() +
								dateLine()) +
								tag("div", "class='metadata'",
										MoreObjects.firstNonNull(item.getDescription(), "")) +
								tag("div", "class='metadata'",
								"Tags: "+ Joiner.on(", ").join(item.getTags()));
	}

	private String dateLine() {
		if (item.getDate() == null) { return ""; }
		
		return "<br>Date: "+ item.getDate()
		.format(DateTimeFormatter.ofPattern("YYYY-MM-dd HH:mm:ss"));
	}
	
	
}
