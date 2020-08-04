package org.gamboni.cloudspill.shared.api;

import org.gamboni.cloudspill.shared.domain.ItemType;

import java.util.Date;

/**
 * @author tendays
 */
public class ItemMetadata {
    public final Date itemDate;
    public final ItemType itemType;
    public ItemMetadata(Date itemDate, ItemType itemType) {
        this.itemDate = itemDate;
        this.itemType = itemType;
    }

    /** Return an ItemMetadata taking non-null values from that, using values in this as a fallback when values in that are null).
     * If both are null, just keep null. */
    public ItemMetadata overrideWith(ItemMetadata that) {
        return new ItemMetadata(
                (that.itemDate == null) ? this.itemDate : that.itemDate,
                (that.itemType == null) ? this.itemType : that.itemType
        );
    }
}
