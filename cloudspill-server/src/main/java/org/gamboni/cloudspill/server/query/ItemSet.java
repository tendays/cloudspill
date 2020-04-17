package org.gamboni.cloudspill.server.query;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import org.gamboni.cloudspill.domain.BackendItem;
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
    List<? extends BackendItem> getSlice(int limit);

    /** Get an URL allowing to load this item set through the given API */
    String getUrl(CloudSpillApi api);

    List<? extends BackendItem> getAllItems();

    /** Return a singleton ItemSet, containing a single already known Item */
    static ItemSet of(BackendItem item) {
        return new ItemSet() {
            @Override
            public String getTitle() {
                return item.getUser() +"/"+ item.getFolder() +"/"+ item.getPath();
            }

            @Override
            public String getDescription() {
                return item.getDescription();
            }

            @Override
            public int getOffset() {
                return 0;
            }

            @Override
            public ItemSet atOffset(int newOffset) {
                Preconditions.checkArgument(newOffset == 0);
                return this;
            }

            @Override
            public long itemCount() {
                return 1;
            }

            @Override
            public List<? extends BackendItem> getSlice(int limit) {
                return (limit == 0) ? ImmutableList.of() : getAllItems();
            }

            @Override
            public String getUrl(CloudSpillApi api) {
                return api.getPublicImagePageUrl(item);
            }

            @Override
            public List<? extends BackendItem> getAllItems() {
                return ImmutableList.of(item);
            }
        };
    }
}
