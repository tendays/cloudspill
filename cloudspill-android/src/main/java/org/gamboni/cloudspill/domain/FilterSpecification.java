package org.gamboni.cloudspill.domain;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

import org.gamboni.cloudspill.shared.query.SearchCriteria;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Filter;

/**
 * @author tendays
 */

public class FilterSpecification implements SearchCriteria, Parcelable {

    public enum Sort {
        DATE_DESC, DATE_ASC
    }

    public final Date from;
    public final Date to;
    public final Set<String> tags;
    public final String by;
    public final Sort sort;

    private static final long NULL_DATE = -1L;
    private static final String NULL_STRING = "";
    public static final Parcelable.Creator<FilterSpecification> CREATOR = new Creator<FilterSpecification>() {
        @Override
        public FilterSpecification createFromParcel(Parcel source) {
            return new FilterSpecification(
                    readDate(source),
                    readDate(source),
                    readString(source),
                    Sort.values()[source.readInt()],
                    new HashSet<>(source.createStringArrayList())
            );
        }

        private Date readDate(Parcel source) {
            long v = source.readLong();
            return (v == NULL_DATE) ? null : new Date(v);
        }

        private String readString(Parcel source) {
            String string = source.readString();
            return string.equals(NULL_STRING) ? null : string;
        }

        @Override
        public FilterSpecification[] newArray(int size) {
            return new FilterSpecification[size];
        }
    };

    public FilterSpecification(Date from, Date to, String by, Sort sort, Set<String> tags) {
        this.from = from;
        this.to = to;
        this.tags = tags;
        Objects.requireNonNull(sort);
        this.by = by;
        this.sort = sort;
    }

    public static FilterSpecification defaultFilter() {
        return new FilterSpecification(null, null, null, Sort.DATE_DESC, Collections.<String>emptySet());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(asLong(from));
        dest.writeLong(asLong(to));
        dest.writeString(asString(by));
        dest.writeInt(sort.ordinal());
        dest.writeStringList(new ArrayList<>(tags));
    }

    @Override
    public String getStringFrom() {
        return new SimpleDateFormat("yyyy-MM-dd").format(this.from);
    }

    @Override
    public String getStringTo() {
        return new SimpleDateFormat("yyyy-MM-dd").format(this.to);
    }

    @Override
    public String getUser() {
        return by;
    }

    @Override
    public Set<String> getTags() {
        return this.tags;
    }

    @Override
    public int getOffset() {
        return 0;
    }

    private long asLong(Date date) {
        return (date == null) ? NULL_DATE : date.getTime();
    }

    private String asString(String string) {
        return (string == null) ? NULL_STRING : string;
    }

    public String toString() {
        StringBuilder result = new StringBuilder();
        if (from != null) {
            result.append(from).append(" <= date");
            if (to != null) {
                result.append(" <= ").append(to);
            }
        } else if (to != null) {
            result.append("date <= ").append(to);
        }
        if (by != null) {
            if (result.length() != 0) {
                result.append(", ");
            }
            result.append("by ").append(by);
        }
        if (result.length() != 0) {
            result.append(", ");
        }
        result.append(sort);
        if (!tags.isEmpty()) {
            result.append("tags in "+ tags);
        }
        return result.toString();
    }
}
