/**
 * 
 */
package org.gamboni.cloudspill.server.html;

import com.google.common.base.MoreObjects;

import org.gamboni.cloudspill.domain.BackendItem;
import org.gamboni.cloudspill.domain.User;
import org.gamboni.cloudspill.server.config.BackendConfiguration;
import org.gamboni.cloudspill.server.html.js.CommentSubmissionJs;
import org.gamboni.cloudspill.server.html.js.EditorSubmissionJs;
import org.gamboni.cloudspill.server.query.Java8SearchCriteria;
import org.gamboni.cloudspill.server.query.ServerSearchCriteria;
import org.gamboni.cloudspill.shared.api.CloudSpillApi;
import org.gamboni.cloudspill.shared.api.ItemCredentials;
import org.gamboni.cloudspill.shared.api.ItemSecurity;
import org.gamboni.cloudspill.shared.domain.ItemType;
import org.gamboni.cloudspill.shared.domain.Items;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
		final List<ItemCredentials> allCredentials;
		final boolean experimental;

		public Model(BackendItem item, Java8SearchCriteria<BackendItem> gallery, BackendItem prev, BackendItem next, User user, List<ItemCredentials> credentials,
					 boolean experimental) {
			super(credentials);
			this.user = user;
			this.item = item;
			this.gallery = gallery;
			this.prev = prev;
			this.next = next;
			this.allCredentials = credentials;
			this.experimental = experimental;
		}
	}

	public ImagePage(BackendConfiguration configuration) {
		super(configuration);
	}


	@Override
	protected HtmlFragment scripts() {
		return HtmlFragment.concatenate(
				tag("script", "type='text/javascript' src=" + quote(api.tagwidgetJS()), ""),
				tag("script", "type='text/javascript' src=" + quote(api.editorJS()), ""),
				tag("script", "type='text/javascript' src=" + quote(api.getUrl(new EditorSubmissionJs(configuration))), ""),
				tag("script", "type='text/javascript' src=" + quote(api.commentsJS()), ""),
				tag("script", "type='text/javascript' src=" + quote(api.getUrl(new CommentSubmissionJs(configuration))), ""));
	}

	@Override
	public String getTitle(Model model) {
		return model.item.getUser() +"/"+ model.item.getFolder() +"/"+ model.item.getPath();
	}

	@Override
	public String getPageUrl(Model model) {
		return api.getImagePageUrl(model.item.getServerId(), null, model.allCredentials);
	}

	@Override
	public Optional<String> getThumbnailUrl(Model model) {
		return Optional.of(api.getThumbnailUrl(model.item.getServerId(), model.allCredentials, CloudSpillApi.Size.IMAGE_THUMBNAIL));
	}

	private String getImageUrl(Model model) {
		return api.getImageUrl(model.item.getServerId(), model.allCredentials);
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
						tag("div", "class='section'",
						(model.getAuthStatus() == ItemCredentials.AuthenticationStatus.LOGGED_IN ?
									button("edit", "edit",
										"edit(" + model.item.getServerId() + ", '" + api.knownTags() + "', '" + api.getTagUrl(model.item.getServerId()) + "')") :
								HtmlFragment.EMPTY),
						(model.getAuthStatus() == ItemCredentials.AuthenticationStatus.LOGGED_IN && !Items.isPublic(model.item) ?
								tag("a", "class='share' href="+ quote(api.getPublicImagePageUrl(model.item)),
										tag("span",
												"[secret link to this "+ (model.item.getType() == ItemType.IMAGE ? "photo" : "video") +"]")) :
								HtmlFragment.EMPTY),
						tag("div", "By: " + model.item.getUser()),
						dateLine(model),
						tag("div", "id='description'", MoreObjects.firstNonNull(model.item.getDescription(), "")),
						tag("div", "class='tags'",
								new HtmlFragment(model.item.getTags().stream()
										.map(tag -> tagElement(tag, model.getAuthStatus()).toString())
										.collect(Collectors.joining(" "))))),
				model.experimental ?
				tag("div", "class='section comment-section'",
						tag("div", "id='comments' class='comments'",
								HtmlFragment.concatenate(
								model.item.getComments().stream()
						.map(c -> tag("div", "class='comment'",
								tag("div", "class='comment-author'", "By: "+ c.getAuthor()),
								tag("div", "class='comment-posted'", c.getPosted().atZone(ZoneId.systemDefault())
								.format(DateTimeFormatter.ofPattern(patternForPrecision("s")))),
								tag("div", "class='comment-text'", HtmlFragment.escape(c.getText()))
								))),
								/* 'new-comment' tag indicates where to insert newly created comments */
								tag("div", "id='new-comment' class='comment'",
										tag("div", "class='comment-author'",
												HtmlFragment.escape("By: "), tag("input", "id='new-comment-author'", "")),
										tag("div", "class='comment-text'",
												tag("textarea", "id='new-comment-text'", "")),
								button("new-comment", "Post comment",
										"newComment("+ model.item.getServerId() +")")))) :
						// non-experimental
						HtmlFragment.EMPTY));
	}

	private HtmlFragment neighbourLink(Model model, BackendItem item, String linkText, boolean right) {
		return (item == null) ? HtmlFragment.EMPTY : tag("a",
				"class="+ quote("siblink"+ (right? " right" : "")) +" href=" + quote(
						api.getImagePageUrl(item.getServerId(), model.gallery, ItemSecurity.getItemCredentials(item, model.getAuthStatus()))),
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
		return (model.getAuthStatus() == ItemCredentials.AuthenticationStatus.ANONYMOUS ?
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
