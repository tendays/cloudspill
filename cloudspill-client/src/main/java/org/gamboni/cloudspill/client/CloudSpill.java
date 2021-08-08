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
import org.gamboni.cloudspill.shared.api.ApiElementMatcher;
import org.gamboni.cloudspill.shared.api.Base64Encoder;
import org.gamboni.cloudspill.shared.api.CloudSpillApi;
import org.gamboni.cloudspill.shared.api.ItemCredentials;
import org.gamboni.cloudspill.shared.api.ItemMetadata;
import org.gamboni.cloudspill.shared.api.LoginState;
import org.gamboni.cloudspill.shared.api.PingResponseHandler;
import org.gamboni.cloudspill.shared.api.ServerInfo;
import org.gamboni.cloudspill.shared.client.ResponseHandler;
import org.gamboni.cloudspill.shared.client.ResponseHandlers;
import org.gamboni.cloudspill.shared.domain.ClientUser;
import org.gamboni.cloudspill.shared.domain.ItemType;
import org.gamboni.cloudspill.shared.util.FileTypeChecker;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * @author tendays
 *
 */
public class CloudSpill {

	private static final File TOKEN_FILE = new File(new File(System.getProperty("user.home")), ".cloudspill");
	private static final Base64Encoder BASE_64_ENCODER = Base64.getEncoder()::encodeToString;

	public static Optional<ItemCredentials.UserToken> loadToken() {
		if (TOKEN_FILE.exists()) {
			try (Reader reader = new FileReader(TOKEN_FILE)) {
				ItemCredentials.UserToken token = ItemCredentials.UserToken.decodeCookie(CharStreams.toString(reader));
				System.out.println("Found existing user token");
				return Optional.of(token);
			} catch (IOException e) {
				System.err.println("Unable to read token file "+ TOKEN_FILE +": "+ e);
				return Optional.empty(); // pretend there's no file if it could not be read
			}
		} else {
			return Optional.empty();
		}
	}

	public static void main(String[] args) {
		OptionParser optionParser = new OptionParser(args);
		CloudSpillClientOptions config = CloudSpillClientOptions.from(optionParser);
		try {
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
			} else if (optionParser.consume("login")) {
				optionParser.assertFinished();
				CloudSpillApi<ResponseHandler> api = config.getServerApi();

				final ItemCredentials.UserToken token = getUserToken(config);

				System.out.println("User: "+ token.user.getName());
				System.out.println("Your token number: "+ token.id);
				System.out.println("Opening session...");

				HttpURLConnection loginConnection = (HttpURLConnection)new URL(api.login()).openConnection();
				token.setHeaders(loginConnection, BASE_64_ENCODER);
				loginConnection.setRequestMethod(ApiElementMatcher.HttpMethod.POST.name());
				loginConnection.connect();
				try (Reader loginReader = new InputStreamReader(loginConnection.getInputStream())) {
					LoginState state = LoginState.valueOf(CharStreams.toString(loginReader));
					System.out.println(state);
				} finally {
					loginConnection.disconnect();
				}
			} else if (optionParser.consume("upload")) {
				CloudSpillApi<ResponseHandler> api = config.getServerApi();
				final String username = config.getUsername();
				String folder = config.require(config.folder);

				while (optionParser.hasNext()) {
					String path = optionParser.next();
					System.out.println(path);

					try (final BufferedInputStream fileInput = new BufferedInputStream(new FileInputStream(path))) {
						ItemMetadata metadata = MetadataExtractor.getItemMetadata(fileInput, new File(path));
						api.upload(username,
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
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	private static ItemCredentials.UserToken getUserToken(CloudSpillClientOptions config) throws IOException {
		CloudSpillApi<ResponseHandler> api = config.getServerApi();

		return loadToken().orElseGet(() -> {
			System.out.println("Requesting new user token");
			final String username = config.require(config.user);
			try {
				HttpURLConnection newTokenConnection = (HttpURLConnection) new URL(api.newToken(
						username)).openConnection();
				try {
					newTokenConnection.setRequestMethod(ApiElementMatcher.HttpMethod.POST.name());
					newTokenConnection.connect();
					try (Reader in = new InputStreamReader(newTokenConnection.getInputStream());
						 Writer out = new FileWriter(TOKEN_FILE)) {
						String response = CharStreams.toString(in);
						final ItemCredentials.UserToken token = ItemCredentials.UserToken.decodeLoginParam(new ClientUser(username), response);
						out.write(token.encodeCookie());
						return token;
					}
				} finally {
					newTokenConnection.disconnect();
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});
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
