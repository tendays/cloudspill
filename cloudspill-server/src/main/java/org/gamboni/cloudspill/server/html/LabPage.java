package org.gamboni.cloudspill.server.html;

import org.gamboni.cloudspill.server.config.BackendConfiguration;
import org.gamboni.cloudspill.shared.api.ItemCredentials;

/**
 * @author tendays
 */
public class LabPage extends AbstractPage {
    public LabPage(BackendConfiguration configuration) {
        super(configuration);
    }

    @Override
    protected String getTitle() {
        return "CloudSpill LAB";
    }

    @Override
    protected String getPageUrl() {
        return api.getBaseUrl() +"lab";
    }

    @Override
    protected HtmlFragment scripts() {
        return tag("script", "type='text/javascript' src="+ quote(api.uploadJS()), "");
    }

    @Override
    protected String bodyAttributes() {
        return "onload='setupDnd()'";
    }

    @Override
    protected HtmlFragment getBody(ItemCredentials.AuthenticationStatus authStatus) {
        /* Heavily inspired by https://css-tricks.com/drag-and-drop-file-uploading/ */
        // can we set 'action' to upload()? probably not, if client doesn't know the file's name (or generalise that service?)
        return tag("form", "id='form' method='POST' action='lab' enctype='multipart/form-data' " +
                        "ondragover='handleDragenter(event)' ondragenter='handleDragenter(event)' " +
                        "ondragleave='handleDragexit(event)' ondragend='handleDragexit(event)' " +
                        "ondrop='handleDrop(event)' onsubmit='handleSubmit(event)'",
                tag("div", "id='drop-target'",
                        unclosedTag("input type='file' name='files[]' id='fileInput' data-multiple-caption='{count} files selected' multiple"),
                        tag("label", "for='file'", "Drop files here to upload"),
                        tag("button", "type='submit'", "Upload")));
    }
}
