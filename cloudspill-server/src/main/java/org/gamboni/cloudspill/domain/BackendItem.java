package org.gamboni.cloudspill.domain;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;

import org.gamboni.cloudspill.shared.api.Csv;
import org.gamboni.cloudspill.shared.domain.ItemType;
import org.gamboni.cloudspill.shared.domain.JpaItem;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import javax.persistence.MappedSuperclass;
import javax.persistence.Transient;
import javax.persistence.Version;

/**
 * @author tendays
 */
@MappedSuperclass
public abstract class BackendItem extends JpaItem {

    /* https://stackoverflow.com/questions/29943132/overriding-id-defined-in-a-mappedsuperclass-with-jpa
     * says it is impossible to specify that BackendItem has an id called "id" then specify the generation
     * strategy in a subclass. Item (for locally-created items) has a generation strategy but RemoteItem
     * does not, as it's the remote server that provides ids.
     */
    protected long id;
    Instant updated;

    @Transient
    public Long getServerId() {
        return id;
    }

    @Version
    public Instant getUpdated() {
        return updated;
    }
    public void setUpdated(Instant updated) {
        this.updated = updated;
    }

    public static final Csv<BackendItem> CSV = new Csv.Impl<BackendItem>()
            .add("id", i -> String.valueOf(i.id), (i, id) -> i.id = Long.parseLong(id))
            .add("user", BackendItem::getUser, BackendItem::setUser)
            .add("folder", BackendItem::getFolder, BackendItem::setFolder)
            .add("path", BackendItem::getPath, BackendItem::setPath)
            .add("date", i -> serialise(i.getDate()), (i, date) -> i.setDate(deserialiseDate(date)))
            .add("type", i -> i.getType().name(), (i, type) -> i.setType(ItemType.valueOf(type)))
            .add("tags",
                    i -> Joiner.on(",").join(i.getTags()),
                    (i, tags) -> i.setTags(ImmutableSet.copyOf(Splitter.on(",").omitEmptyStrings().split(tags))))
            .add("checksum", BackendItem::getChecksum, BackendItem::setChecksum)
            .add("description", BackendItem::getDescription, BackendItem::setDescription);

    /** Construct a CSV extractor based on the given header line (the first line in a CSV stream).
     * Use the {@link org.gamboni.cloudspill.shared.api.Csv.Extractor#deserialise(Object, String)} method
     * of the returned object with the remaining lines to deserialise actual Item objects.
     *
     * @param headerLine The first line of the CSV stream
     * @return an Extractor for deserialising remaining lines
     */
    public static Csv.Extractor<BackendItem> deserialise(String headerLine) {
        return CSV.extractor(headerLine);
    }

    private static String serialise(LocalDateTime time) {
        return (time == null) ? "" : Long.toString(time.atOffset(ZoneOffset.UTC).toInstant().toEpochMilli());
    }
    private static LocalDateTime deserialiseDate(String time) {
        return time.isEmpty() ? null : Instant.ofEpochMilli(Long.parseLong(time)).atOffset(ZoneOffset.UTC).toLocalDateTime();
    }
}
