package org.gamboni.cloudspill.server.query;

import org.gamboni.cloudspill.domain.Item;
import org.gamboni.cloudspill.shared.api.CloudSpillApi;

import java.util.List;

/** A collection of Items, similar to a gallery.
 *
 * @author tendays
 */
public interface ItemSet {
    /** Title of the set */
    String getTitle();
    /** Description of the set */
    String getDescription();
    /** Get the offset specified in this ItemSet (this is used to generate "previous page" and/or "next page" buttons). */
    int getOffset();
    /** Return a copy of this ItemSet where the offset is changed to the given value. */
    ItemSet atOffset(int newOffset);
    /** Total number of items */
    long itemCount();

    /** Get actual items.
     *
     * @param limit limit to how many items to retrieve.
     * @return a List containing at most {@code limit} items.
     */
    List<Item> getSlice(int limit);

    /** Get an URL allowing to load this item set through the given API */
    String getUrl(CloudSpillApi api);

    List<Item> getAllItems();
}
