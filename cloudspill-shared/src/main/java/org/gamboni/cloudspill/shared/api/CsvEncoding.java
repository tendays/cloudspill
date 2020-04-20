package org.gamboni.cloudspill.shared.api;

/**
 * @author tendays
 */
public abstract class CsvEncoding {
    public static String unslash(String encodedValue) {
        StringBuilder out = new StringBuilder();
        int chunkStart = 0;
        int semiColon = encodedValue.indexOf('\\');
        while (semiColon > -1) {
            out.append(encodedValue.substring(chunkStart, semiColon));
            if (encodedValue.charAt(semiColon + 1) == 'n') {
                out.append('\n');
            } else if (encodedValue.charAt(semiColon + 1) == ',') {
                out.append(';');
            } else {
                out.append(encodedValue.charAt(semiColon + 1));
            }
            chunkStart = semiColon + 2;
            semiColon = encodedValue.indexOf('\\', chunkStart);
        }
        out.append(encodedValue.substring(chunkStart));
        return out.toString();
    }
}
