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

    public interface EmbedGetter<I, C> {
        C get(I item);
    }

    public interface Getter<I> {
        String get(I item);
    }
    public interface Setter<I> {
        void set(I item, String value);
    }

    public static class Impl<I> implements Csv<I> {
        private List<CsvColumn> columns = new ArrayList<>();
        private List<CsvEmbed<?>> embeds = new ArrayList<>();

        @Override
        public String header() {
            String nextSeparator = "";
            StringBuilder result = new StringBuilder();
            for (CsvColumn column : this.columns) {
                result.append(nextSeparator);
                result.append(column.name);
                nextSeparator = ";";
            }
            for (CsvEmbed<?> embed : embeds) {
                result.append(nextSeparator);
                result.append(embed.child.header());
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

                result.append(encode(column.getter.get(item)));
                nextSeparator = ";";
            }
            for (CsvEmbed<?> embed : embeds) {
                result.append(nextSeparator);
                result.append(embed.serialise(item));
                nextSeparator = ";";
            }
            return result.toString();
        }

        private String encode(String rawValue) {
            if (rawValue == null) {
                return "";
            } else if (rawValue.trim().isEmpty()) {
                return rawValue +" ";
            } else {
                return rawValue
                        .replace("\\", "\\\\")
                        .replace("\r", "") // assume all CR are part of a CRLF and only keep the LF
                        .replace("\n", "\\n")
                        .replace(";", "\\,");
            }
        }

        private String decode(String encodedValue) {
            if (encodedValue.isEmpty()) {
                return null;
            } else if (encodedValue.trim().isEmpty()) {
                return encodedValue.substring(1); // remove one space
            } else {
                return CsvEncoding.unslash(encodedValue);
            }
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
            List<Extractor<I>> embeddedExtractors = new ArrayList<>();
            for (CsvEmbed<?> embed : embeds) {
                embeddedExtractors.add(embed.extractor(headerLine));
            }

            return new Extractor<I>() {
                @Override
                public <J extends I> J deserialise(J item, String csv) {
                    int left = 0;
                    int right = csv.indexOf(";");
                    Iterator<Setter<I>> setters = extractorSetters.iterator();
                    while (right != -1 && setters.hasNext()) {
                        setters.next().set(item, decode(csv.substring(left, right)));
                        left = right+1;
                        right = csv.indexOf(";", left);
                    }
                    if (setters.hasNext()) {
                        setters.next().set(item, decode(csv.substring(left)));
                    } // else: malformed csv!

                    for (Extractor<I> embeddedExtractor : embeddedExtractors) {
                        embeddedExtractor.deserialise(item, csv);
                    }
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

        private class CsvEmbed<C> {

            private final Csv<C> child;
            private final EmbedGetter<I, C> getter;

            CsvEmbed(Csv<C> child, EmbedGetter<I,C> getter) {
                this.child = child;
                this.getter = getter;
            }

            String serialise(I item) {
                return child.serialise(getter.get(item));
            }

            Extractor<I> extractor(String headerLine) {
                final Extractor<C> childExtractor = child.extractor(headerLine);
                return new Extractor<I>() {
                    @Override
                    public <J extends I> J deserialise(J item, String csv) {
                        childExtractor.deserialise(getter.get(item), csv);
                        return item;
                    }
                };
            }
        }

        public Impl<I> add(String name, Getter<I> getter, Setter<I> setter) {
            this.columns.add(new CsvColumn(name, getter, setter));
            return this;
        }

        public <C> Impl<I> embed(Csv<C> child, EmbedGetter<I, C> getter) {
            this.embeds.add(new CsvEmbed<>(child, getter));
            return this;
        }
    }


}
