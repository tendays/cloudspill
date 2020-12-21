/**
 * 
 */
package org.gamboni.cloudspill.server.html;

import com.google.common.base.CaseFormat;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;

import org.gamboni.cloudspill.domain.BackendItem;
import org.gamboni.cloudspill.domain.User;
import org.gamboni.cloudspill.server.config.BackendConfiguration;
import org.gamboni.cloudspill.server.html.js.EditorSubmissionJs;
import org.gamboni.cloudspill.server.query.Java8SearchCriteria;
import org.gamboni.cloudspill.server.query.ServerSearchCriteria;
import org.gamboni.cloudspill.shared.api.CloudSpillApi;
import org.gamboni.cloudspill.shared.api.ItemCredentials;
import org.gamboni.cloudspill.shared.domain.ItemType;
import org.gamboni.cloudspill.shared.domain.Items;
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
public class ImagePage extends AbstractRenderer<ImagePage.Model> {

	public static class Model extends OutputModel {
		final BackendItem item, prev, next;
		final User user;
		final Java8SearchCriteria<BackendItem> gallery;

		public Model(BackendItem item, Java8SearchCriteria<BackendItem> gallery , BackendItem prev, BackendItem next, User user, ItemCredentials credentials) {
			super(credentials);
			this.user = user;
			this.item = item;
			this.gallery = gallery;
			this.prev = prev;
			this.next = next;
		}
	}

	public ImagePage(BackendConfiguration configuration) {
		super(configuration);
	}


	@Override
	protected HtmlFragment scripts() {
		return HtmlFragment.concatenate(
				tag("script", "type='text/javascript' src=" + quote(api.editorJS()), ""),
				tag("script", "type='text/javascript' src=" + quote(api.getUrl(new EditorSubmissionJs(configuration))), ""));
	}

	@Override
	public String getTitle(Model model) {
		return model.item.getUser() +"/"+ model.item.getFolder() +"/"+ model.item.getPath();
	}

	@Override
	public String getPageUrl(Model model) {
		return api.getPublicImagePageUrl(model.item);
	}

	@Override
	public Optional<String> getThumbnailUrl(Model model) {
		return Optional.of(api.getThumbnailUrl(model.item, CloudSpillApi.Size.IMAGE_THUMBNAIL));
	}

	private String getImageUrl(Model model) {
		return api.getImageUrl(model.item.getServerId(), ImmutableList.of(model.credentials));
	}

	@Override
	public HtmlFragment getBody(Model model) {
		return HtmlFragment.concatenate(
				neighbourLink(model, model.prev, "<", false),
				neighbourLink(model, model.next, ">", true),
				(model.item.getType() == ItemType.VIDEO ?
						tag("video", "controls class='image' src=" + quote(getImageUrl(model)), "") :
						unclosedTag("img class='image' src=" + quote(getImageUrl(model)))),
				tag("div", "class='metadata'",
						(model.credentials.getAuthStatus() == ItemCredentials.AuthenticationStatus.LOGGED_IN ?
									button("edit", "edit",
										"edit(" + model.item.getServerId() + ", '" + api.knownTags() + "', '" + api.getTagUrl(model.item.getServerId()) + "')") :
								HtmlFragment.EMPTY),
						(model.credentials.getAuthStatus() == ItemCredentials.AuthenticationStatus.LOGGED_IN && !Items.isPublic(model.item) ?
								tag("a", "class='share' href="+ quote(api.getPublicImagePageUrl(model.item)),
										tag("span",
												"[secret link to this "+ (model.item.getType() == ItemType.IMAGE ? "photo" : "video") +"]")) :
								HtmlFragment.EMPTY),
						tag("div", "By: " + model.item.getUser()),
						dateLine(model),
						tag("div", "id='description'", MoreObjects.firstNonNull(model.item.getDescription(), "")),
						tag("div", "class='tags'",
								new HtmlFragment(model.item.getTags().stream()
										.map(tag -> tagElement(tag, model.credentials.getAuthStatus()).toString())
										.collect(Collectors.joining(" "))))));
	}

	private HtmlFragment neighbourLink(Model model, BackendItem item, String linkText, boolean right) {
		return item == null ? HtmlFragment.EMPTY : tag("a",
				"class="+ quote("siblink"+ (right? " right" : "")) +" href=" + quote(model.gallery.getUrl(api) + "/" + item.getServerId() + api.ID_HTML_SUFFIX),
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
	protected String copyrightNotice(Model model) {
		return capitalise(model.item.getType().name()) +" © "+
				(model.item.getDate() == null ? LocalDateTime.now() : model.item.getDate()).getYear() +" "+
				((model.user == null || model.user.getFullName() == null) ? capitalise(model.item.getUser()) : model.user.getFullName()) +
				". All rights reserved.";
	}

	private String capitalise(String value) {
		return value.substring(0, 1).toUpperCase() + value.substring(1).toLowerCase();
	}

	private HtmlFragment dateLine(Model model) {
		if (model.item.getDate() == null) { return HtmlFragment.EMPTY; }

		String dateString = model.item.getDate()
				.format(DateTimeFormatter.ofPattern(patternForPrecision(model.item.getDatePrecision())));
		return (model.credentials.getAuthStatus() == ItemCredentials.AuthenticationStatus.ANONYMOUS ?
					tag("div","class='date'", dateString) :
					tag("a", "class='date' href="+ quote(ServerSearchCriteria.ALL.at(model.item.getDate().toLocalDate()).getUrl(api)),
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
