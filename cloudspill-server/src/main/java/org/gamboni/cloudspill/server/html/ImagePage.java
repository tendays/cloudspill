/**
 * 
 */
package org.gamboni.cloudspill.server.html;

import com.google.common.base.CaseFormat;
import com.google.common.base.MoreObjects;

import org.gamboni.cloudspill.domain.BackendItem;
import org.gamboni.cloudspill.domain.User;
import org.gamboni.cloudspill.server.config.BackendConfiguration;
import org.gamboni.cloudspill.server.html.js.EditorSubmissionJs;
import org.gamboni.cloudspill.server.query.ServerSearchCriteria;
import org.gamboni.cloudspill.shared.api.CloudSpillApi;
import org.gamboni.cloudspill.shared.api.ItemCredentials;
import org.gamboni.cloudspill.shared.domain.ItemType;
import org.gamboni.cloudspill.shared.query.QueryRange;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author tendays
 *
 */
public class ImagePage extends AbstractPage {
	private final BackendItem item, prev, next;
	private final User user;
	private final Long galleryPart;

	public ImagePage(BackendConfiguration configuration, BackendItem item, Long galleryPart, BackendItem prev, BackendItem next, User user, ItemCredentials credentials) {
		super(configuration, credentials);

		this.user = user;
		this.item = item;
		this.galleryPart = galleryPart;
		this.prev = prev;
		this.next = next;
	}


	@Override
	protected HtmlFragment scripts() {
		return HtmlFragment.concatenate(
				tag("script", "type='text/javascript' src=" + quote(api.editorJS()), ""),
				tag("script", "type='text/javascript' src=" + quote(api.getUrl(new EditorSubmissionJs(configuration))), ""));
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
		return HtmlFragment.concatenate(
				neighbourLink(prev, "<", false),
				neighbourLink(next, ">", true),
				(item.getType() == ItemType.VIDEO ?
						tag("video", "controls class='image' src=" + quote(getImageUrl()), "") :
						unclosedTag("img class='image' src=" + quote(getImageUrl()))),
				tag("div", "class='metadata'",
						(authStatus == ItemCredentials.AuthenticationStatus.LOGGED_IN ?
								tag("div", "class='button' id='edit' onclick="+
										quote("edit("+ item.getServerId() +", '"+ api.knownTags() +"', '"+ api.getTagUrl(item.getServerId()) +"')"), "edit") : HtmlFragment.EMPTY),
						tag("div", "By: " + item.getUser()),
						dateLine(authStatus),
						tag("div", "id='description'", MoreObjects.firstNonNull(item.getDescription(), "")),
						tag("div", "class='tags'",
								new HtmlFragment(item.getTags().stream()
										.map(tag -> tagElement(tag, authStatus).toString())
										.collect(Collectors.joining(" "))))));
	}

	private HtmlFragment neighbourLink(BackendItem item, String linkText, boolean right) {
		return item == null ? HtmlFragment.EMPTY : tag("a",
				"class="+ quote("siblink"+ (right? " right" : "")) +" href=" + quote(api.galleryPart(galleryPart, null, QueryRange.ALL) + "/" + item.getServerId() + api.ID_HTML_SUFFIX),
				tag("div", linkText));
	}

	private HtmlFragment tagElement(String tag, ItemCredentials.AuthenticationStatus authStatus) {
		if (authStatus == ItemCredentials.AuthenticationStatus.ANONYMOUS) {
			return tag("span class='tag'", tag);
		} else {
			return tag("a", "class='tag' data-tag="+ quote(tag) +" href="+ quote(
					ServerSearchCriteria.ALL.withTag(tag).getUrl(api)),
					tag);
		}
	}

	@Override
	protected String copyrightNotice() {
		return capitalise(item.getType().name()) +" © "+
				(this.item.getDate() == null ? LocalDateTime.now() : this.item.getDate()).getYear() +" "+
				(this.user == null ? capitalise(this.item.getUser()) : this.user.getFullName()) +
				". All rights reserved.";
	}

	private String capitalise(String value) {
		return value.substring(0, 1).toUpperCase() + value.substring(1).toLowerCase();
	}

	private HtmlFragment dateLine(ItemCredentials.AuthenticationStatus authStatus) {
		if (item.getDate() == null) { return HtmlFragment.EMPTY; }

		String dateString = item.getDate()
				.format(DateTimeFormatter.ofPattern(patternForPrecision(item.getDatePrecision())));
		return (authStatus == ItemCredentials.AuthenticationStatus.ANONYMOUS ? tag("div","class='date'", dateString) : tag("a", "class='date' href="+
				quote(ServerSearchCriteria.ALL.at(item.getDate().toLocalDate()).getUrl(api)),
				dateString));
	}

	private String patternForPrecision(String datePrecision) {
		switch (datePrecision) {
			case "Y": return "YYYY";
			case "M": return "YYYY-MM";
			case "d": return "YYYY-MM-dd";
			case "H":
			case "m": return "YYYY-MM-dd HH:mm";
			default: return "YYYY-MM-dd HH:mm:ss";
		}
	}


}
