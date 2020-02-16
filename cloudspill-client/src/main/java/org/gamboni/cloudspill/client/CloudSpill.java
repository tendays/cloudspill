/**
 * 
 */
package org.gamboni.cloudspill.client;

import com.drew.imaging.FileType;
import com.drew.imaging.FileTypeDetector;
import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataReader;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;

import org.gamboni.cloudspill.shared.api.CloudSpillApi;
import org.gamboni.cloudspill.shared.api.PingResponseHandler;
import org.gamboni.cloudspill.shared.api.ServerInfo;
import org.gamboni.cloudspill.shared.domain.ItemType;
import org.gamboni.cloudspill.shared.util.FileTypeChecker;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLConnection;
import java.util.Date;

/**
 * @author tendays
 *
 */
public class CloudSpill {

	public static void main(String[] args) {
		OptionParser optionParser = new OptionParser(args);
		CloudSpillClientOptions config = CloudSpillClientOptions.from(optionParser);

		try {
			if (optionParser.consume("ping")) {
				optionParser.assertFinished();
				final URLConnection connection = config.openConnection(CloudSpillApi.PING);

				final ServerInfo serverInfo = new PingResponseHandler() {
					protected void warn(String message) {
						System.err.println("WARN: " + message);
					}
				}.parse(CharStreams.toString(new InputStreamReader(connection.getInputStream())));

				if (serverInfo.isOnline()) {
					System.out.println("Connection successful. Data version " + serverInfo.getVersion() + ", public URL " + serverInfo.getPublicUrl());
				} else {
					System.err.println("Connection failed");
					System.exit(1);
				}
			} else if (optionParser.consume("upload")) {
				String folder = config.require(config.folder, "Uploading requires a folder name");

				while (optionParser.hasNext()) {
					String path = optionParser.next();
					System.out.println(path);

					try {
						Date itemDate;
						ItemType itemType;
						try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(path))) {
							final FileType fileType = FileTypeDetector.detectFileType(in);
							final String mimeType = fileType.getMimeType();
							if (mimeType != null && mimeType.startsWith("image/")) {
								itemType = ItemType.IMAGE;
							} else if (mimeType != null && mimeType.startsWith("video/")) {
								itemType = ItemType.VIDEO;
							} else {
								itemType = ItemType.UNKNOWN;
							}

							final Metadata metadata = ImageMetadataReader.readMetadata(new File(path));
							final ExifSubIFDDirectory exif = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
							if (exif != null && exif.getDateDigitized() != null) {
								itemDate = exif.getDateDigitized();
							} else {
								itemDate = new Date(new File(path).lastModified());
							}
						} catch (ImageProcessingException e) {
							e.printStackTrace();
							System.err.println("Failed reading image metadata");
							itemDate = new Date(new File(path).lastModified());
							itemType = new FileTypeChecker(ByteStreams.toByteArray(
									ByteStreams.limit(new FileInputStream(path), FileTypeChecker.PREAMBLE_LENGTH))).getType();
						}

						try (final FileInputStream fileInput = new FileInputStream(path)) {

							final HttpURLConnection connection = config.openConnection(CloudSpillApi.upload(config.require(config.user, "User parameter not set"),
									folder,
									unprefix(path, "./", "/")));
							connection.setDoOutput(true);
							connection.setRequestMethod("PUT");
							connection.setRequestProperty(CloudSpillApi.UPLOAD_TIMESTAMP_HEADER, Long.toString(itemDate.getTime()));
							connection.setRequestProperty(CloudSpillApi.UPLOAD_TYPE_HEADER, itemType.name());

							ByteStreams.copy(fileInput, connection.getOutputStream());

							final String response = CharStreams.toString(new InputStreamReader(connection.getInputStream()));

							System.out.println("Created new item with id " + Long.parseLong(response));
						}
					} catch (IOException e) {
						System.err.println(e.getMessage());
					}
				}
			} else {
				optionParser.assertFinished(); // error if unknown command / option
				System.err.println("No command provided"); // error if no command at all
				System.exit(255);
			}
		} catch (IOException e) {
			System.err.println(e.getMessage());
			System.exit(1);
		}
	}

	private static String unprefix(String path, String... prefixes) {
		for (String prefix : prefixes) {
			if (path.startsWith(prefix)) {
				return path.substring(prefix.length());
			}
		}
		return path;
	}
}
