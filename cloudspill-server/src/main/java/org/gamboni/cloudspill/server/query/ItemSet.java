package org.gamboni.cloudspill.server.query;

import com.google.common.collect.ImmutableList;

import org.gamboni.cloudspill.domain.BackendItem;

import java.util.List;

/** A collection of Items, similar to a gallery.
 *
 * @author tendays
 */
public class ItemSet {
    /** Total number of items */
    public final long totalCount;

    public final ImmutableList<? extends BackendItem> rows;

    public final String title;
    public final String description;

    public ItemSet(long totalCount, List<? extends BackendItem> rows, String title, String description) {
        this.totalCount = totalCount;
        this.rows = ImmutableList.copyOf(rows);
        this.title = title;
        this.description = description;
    }

    public static ItemSet of(BackendItem item) {
        return new ItemSet(1, ImmutableList.of(item), item.getTitle(), "");
    }
}
