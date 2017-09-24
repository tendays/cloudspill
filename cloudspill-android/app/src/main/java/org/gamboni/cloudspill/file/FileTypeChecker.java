package org.gamboni.cloudspill.file;

/**
 * Created by tendays on 25.06.17.
 */

public class FileTypeChecker {
    public static final int PREAMBLE_LENGTH = 12;

    private final byte[] preamble;

    public FileTypeChecker(byte[] preamble) {
        this.preamble = preamble;
    }

    public boolean isJpeg() {
        return at(0, 0xff, 0xd8, 0xff) &&
                (at(3, 0xe0) || at(3, 0xdb) || at(3, 0xe1));
    }

    public boolean isVideo() {
        // Source: http://www.garykessler.net/library/file_sigs.html

        // nn nn nn nn  66 74 79 70  71 74 20 20 - QuickTime movie file
        // 00 00 00 18  66 74 79 70  6d 70 34 32 - MPEG-4 video/QuickTime file
        // nn nn nn nn  66 74 79 70  69 73 6f 6d - ISO Base Media file (MPEG-4) v1
        return at(4, 0x66, 0x74, 0x79, 0x70) &&
                (at(8, 0x71, 0x74, 0x20, 0x20) ||
                at(8, 0x6d, 0x70, 0x34, 0x32) ||
                at(8, 0x69, 0x73, 0x6f, 0x6d));
    }

    private boolean at(int index, int... value) {
        for (int offset=0; offset<value.length; offset++) {
            if (preamble[index+offset] != (byte) (value[offset])) {
                return false;
            }
        }
        return true;
    }
}
