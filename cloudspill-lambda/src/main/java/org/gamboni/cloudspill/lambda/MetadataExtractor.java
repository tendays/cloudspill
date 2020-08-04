package org.gamboni.cloudspill.lambda;

import com.drew.imaging.FileType;
import com.drew.imaging.FileTypeDetector;
import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.google.common.io.ByteStreams;

import org.gamboni.cloudspill.shared.api.ItemMetadata;
import org.gamboni.cloudspill.shared.domain.ItemType;
import org.gamboni.cloudspill.shared.util.FileTypeChecker;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Date;

/**
 * @author tendays
 */
public abstract class MetadataExtractor {
    public static ItemMetadata getItemMetadata(BufferedInputStream stream, File file) throws IOException {
        Date itemDate;
        ItemType itemType;
        try {
            final FileType fileType = FileTypeDetector.detectFileType(stream);
            final String mimeType = fileType.getMimeType();
            if (mimeType != null && mimeType.startsWith("image/")) {
                itemType = ItemType.IMAGE;
            } else if (mimeType != null && mimeType.startsWith("video/")) {
                itemType = ItemType.VIDEO;
            } else {
                itemType = ItemType.UNKNOWN;
            }

            final Metadata metadata = (file == null) ? ImageMetadataReader.readMetadata(stream) : ImageMetadataReader.readMetadata(file);
            final ExifSubIFDDirectory exif = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            if (exif != null && exif.getDateDigitized() != null) {
                itemDate = exif.getDateDigitized();
            } else {
                itemDate = (file == null) ? null : new Date(file.lastModified());
            }
        } catch (ImageProcessingException e) {
            e.printStackTrace();
            System.err.println("Failed reading image metadata");
            itemDate = (file == null) ? null : new Date(file.lastModified());

            stream.mark(FileTypeChecker.PREAMBLE_LENGTH);
            itemType = new FileTypeChecker(ByteStreams.toByteArray(
                    ByteStreams.limit(stream, FileTypeChecker.PREAMBLE_LENGTH))).getType();
            stream.reset();
        }

        return new ItemMetadata(itemDate, itemType);
    }

}
