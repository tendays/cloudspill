package org.gamboni.cloudspill.shared.api;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author tendays
 */
public interface Csv<I> {
    String header();

    String serialise(I item);

    Extractor<I> extractor(String header);

    public interface Extractor<I> {
        /** Read values from the given csv file line, and write them into the given item. */
        <J extends I> J deserialise(J item, String csv);
    }

    public interface Getter<I> {
        String get(I item);
    }
    public interface Setter<I> {
        void set(I item, String value);
    }

    public static class Impl<I> implements Csv<I> {
        private List<CsvColumn> columns = new ArrayList<>();

        @Override
        public String header() {
            String nextSeparator = "";
            StringBuilder result = new StringBuilder();
            for (CsvColumn column : this.columns) {
                result.append(nextSeparator);
                result.append(column.name);
                nextSeparator = ";";
            }
            return result.toString();
        }

        @Override
        public String serialise(I item) {
            String nextSeparator = "";
            StringBuilder result = new StringBuilder();
            for (CsvColumn column : this.columns) {
                result.append(nextSeparator);

                // TODO quote or escape
                result.append(column.getter.get(item));
                nextSeparator = ";";
            }
            return result.toString();
        }

        private Setter<I> findSetter(String name) {
            for (CsvColumn column : columns) {
                if (column.name.equals(name)) {
                    return column.setter;
                }
            }
            return new Setter<I>() {
                @Override
                public void set(Object item, String value) {
                }
            };
        }

        @Override
        public Extractor<I> extractor(String headerLine) {
            List<Setter<I>> extractorSetters = new ArrayList<>();
            for (String header : headerLine.split(";")) {
                extractorSetters.add(findSetter(header));
            }

            return new Extractor<I>() {
                @Override
                public <J extends I> J deserialise(J item, String csv) {
                    int left = 0;
                    int right = csv.indexOf(";");
                    Iterator<Setter<I>> setters = extractorSetters.iterator();
                    while (right != -1 && setters.hasNext()) {
                        setters.next().set(item, csv.substring(left, right));
                        left = right+1;
                        right = csv.indexOf(";", left);
                    }
                    if (setters.hasNext()) {
                        setters.next().set(item, csv.substring(left));
                    } // else: malformed csv!
                    return item;
                }
            };
        }

        private class CsvColumn {
            final String name;
            final Getter<I> getter;
            final Setter<I> setter;

            private CsvColumn(String name, Getter<I> getter, Setter<I> setter) {
                this.name = name;
                this.getter = getter;
                this.setter = setter;
            }
        }

        public Impl<I> add(String name, Getter<I> getter, Setter<I> setter) {
            this.columns.add(new CsvColumn(name, getter, setter));
            return this;
        }
    }


}
