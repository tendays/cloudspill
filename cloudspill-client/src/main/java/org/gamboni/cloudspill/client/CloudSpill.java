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

import org.gamboni.cloudspill.lambda.MetadataExtractor;
import org.gamboni.cloudspill.lambda.client.ApiInvokers;
import org.gamboni.cloudspill.shared.api.CloudSpillApi;
import org.gamboni.cloudspill.shared.api.ItemMetadata;
import org.gamboni.cloudspill.shared.api.PingResponseHandler;
import org.gamboni.cloudspill.shared.api.ServerInfo;
import org.gamboni.cloudspill.shared.client.ResponseHandler;
import org.gamboni.cloudspill.shared.client.ResponseHandlers;
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
import java.util.function.Consumer;

/**
 * @author tendays
 *
 */
public class CloudSpill {

	public static void main(String[] args) {
		OptionParser optionParser = new OptionParser(args);
		CloudSpillClientOptions config = CloudSpillClientOptions.from(optionParser);

			if (optionParser.consume("ping")) {
				optionParser.assertFinished();
				CloudSpillApi<ResponseHandler> api = config.getServerApi();
				api.ping(connection -> {

					final ServerInfo serverInfo = new PingResponseHandler() {
						protected void warn(String message) {
							System.err.println("WARN: " + message);
						}
					}.parse(api.getBaseUrl(), CharStreams.toString(new InputStreamReader(connection.getInputStream())));

					if (serverInfo.isOnline()) {
						System.out.println("Connection successful. Data version " + serverInfo.getVersion() + ", public URL " +
								api.getBaseUrl());
					} else {
						System.err.println("Connection failed");
						System.exit(1);
					}
				});
			} else if (optionParser.consume("upload")) {
				CloudSpillApi<ResponseHandler> api = config.getServerApi();
				String folder = config.require(config.folder, "Uploading requires a folder name");

				while (optionParser.hasNext()) {
					String path = optionParser.next();
					System.out.println(path);

					try (final BufferedInputStream fileInput = new BufferedInputStream(new FileInputStream(path))) {
						ItemMetadata metadata = MetadataExtractor.getItemMetadata(fileInput, new File(path));
						api.upload(config.require(config.user, "User parameter not set"),
								folder,
								unprefix(path, "./", "/"), ApiInvokers.upload(metadata, fileInput, id -> {
									System.out.println("Created new item with id " + id);
								}));
					} catch (IOException e) {
						System.err.println(e.getMessage());
					}
				}
			} else {
				optionParser.assertFinished(); // error if unknown command / option
				System.err.println("No command provided"); // error if no command at all
				System.exit(255);
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
