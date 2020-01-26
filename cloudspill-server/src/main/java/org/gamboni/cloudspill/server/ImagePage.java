/**
 * 
 */
package org.gamboni.cloudspill.server;

import java.time.format.DateTimeFormatter;

import org.gamboni.cloudspill.domain.Item;

import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.inject.Inject;

/**
 * @author tendays
 *
 */
public class ImagePage {
	
	private final Item item;
	private final ServerConfiguration configuration;
	
	private ImagePage(ServerConfiguration configuration, Item item) {
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
	private static String tag(String name, String attributes, String content) {
		return "<"+ name +" "+ attributes +">"+ content +"</"+ name +">";
	}
	
	private static String tag(String name, String content) {
		return "<"+ name +">"+ content +"</"+ name +">";
	}
	
	private static String slashedTag(String name) {
		return "<"+ name +"/>";
	}
	
	private static String unclosedTag(String name) {
		return "<"+ name +">";
	}
	
	private static String meta(String property, String content) {
		return slashedTag("meta property="+ quote(property) +" content="+ quote(content));
	}
	
	private static String quote(String text) {
		return "\""+ text
				.replace("&", "&amp;")
		.replace("<", "&lt;")
		.replace("\"", "&quot;")
		+ "\"";
	}

	public String getTitle() {
		return item.getUser() +"/"+ item.getFolder() +"/"+ item.getPath();
	}

	public String getPageUrl() {
		return configuration.getPublicUrl() + "/item/html/"+ item.getId() + accessKeyQueryString();
	}

	public String getThumbnailUrl() {
		return configuration.getPublicUrl() + "/thumbs/300/"+ item.getId() + accessKeyQueryString();
	}

	public String getImageUrl() {
		return configuration.getPublicUrl() +"/item/"+ item.getId() + accessKeyQueryString();
	}

	private String accessKeyQueryString() {
		return "?key="+ item.getChecksum().replace("+", "%2B");
	}
	
	public String getHtml() {
		return tag("html", "prefix=\"og: http://ogp.me/ns#\"",
				tag("head",
						tag("title", getTitle()) +
						meta("og:title", getTitle()) +
						meta("og:type", "article") +
						meta("og:url", getPageUrl()) +
						meta("og:image", getThumbnailUrl()) +
						slashedTag("link rel=\"stylesheet\" type=\"text/css\" href=" +
						quote(configuration.getCss()))
						) +
				tag("body",
						tag("h1", "", getTitle()) +
						unclosedTag("img class='image' src="+ quote(getImageUrl())) +
						tag("div", "class='metadata'", "By: "+ item.getUser() +
								dateLine()) +
								tag("div", "class='metadata'",
										MoreObjects.firstNonNull(item.getDescription(), "")) +
								tag("div", "class='metadata'",
								"Tags: "+ Joiner.on(", ").join(item.getTags())))
				);
	}

	private String dateLine() {
		if (item.getDate() == null) { return ""; }
		
		return "<br>Date: "+ item.getDate()
		.format(DateTimeFormatter.ofPattern("YYYY-MM-dd HH:mm:ss"));
	}
	
	
}
