package org.gamboni.cloudspill.shared.util;

/**
 * @author tendays
 */

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.MetadataException;
import com.drew.metadata.exif.ExifIFD0Directory;

import org.gamboni.cloudspill.shared.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;


public class ImageOrientationUtil {
    public static int getExifRotation(File f) {
        try (InputStream in = new FileInputStream(f)) {
            return getExifRotation(in);
        } catch (IOException e) {
            Log.error(e.getMessage());
            return 1;
        }
    }

    public static int getExifRotationDegrees(File f) {
        return getDegrees(getExifRotation(f));
    }

    public static int getExifRotationDegrees(InputStream i) {
        return getDegrees(getExifRotation(i));
    }

    public static int getExifRotation(InputStream in) {
        try {
            final ExifIFD0Directory directory = ImageMetadataReader.readMetadata(in).getFirstDirectoryOfType(ExifIFD0Directory.class);
            return (directory == null) ? 1 : directory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
        } catch (ImageProcessingException | MetadataException | IOException e) {
            Log.error(e.getMessage());
            return 1;
        }
    }

    public static int getDegrees(int orientation) {
        if (orientation == 6) {
            return 90;
        } else if (orientation == 3) {
            return 180;
        } else if (orientation == 8) {
            return 270;
        } else {
            return 0;
        }
    }
}