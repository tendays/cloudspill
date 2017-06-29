package org.gamboni.cloudspill;

/**
 * Created by tendays on 25.06.17.
 */

public class FileTypeChecker {
    private final byte[] preamble;

    public FileTypeChecker(byte[] preamble) {
        this.preamble = preamble;
    }

    public boolean isJpeg() {
        return at(0, 0xff) && at(1, 0xd8) && at(2, 0xff) &&
                (at(3, 0xe0) || at(3, 0xdb) || at(3, 0xe1));
    }

    private boolean at(int index, int value) {
        return preamble[index] == (byte) value;
    }
}
