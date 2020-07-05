/**
 * 
 */
package org.gamboni.cloudspill.server;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.google.inject.Guice;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.gamboni.cloudspill.domain.BackendItem;
import org.gamboni.cloudspill.domain.CloudSpillEntityManagerDomain;
import org.gamboni.cloudspill.domain.GalleryPart;
import org.gamboni.cloudspill.domain.GalleryPart_;
import org.gamboni.cloudspill.domain.Item;
import org.gamboni.cloudspill.domain.Item_;
import org.gamboni.cloudspill.domain.ServerDomain;
import org.gamboni.cloudspill.domain.User;
import org.gamboni.cloudspill.domain.UserAuthToken;
import org.gamboni.cloudspill.domain.UserAuthToken_;
import org.gamboni.cloudspill.domain.User_;
import org.gamboni.cloudspill.server.config.ServerConfiguration;
import org.gamboni.cloudspill.server.html.GalleryListPage;
import org.gamboni.cloudspill.server.query.ItemQueryLoader;
import org.gamboni.cloudspill.server.query.ItemSet;
import org.gamboni.cloudspill.server.query.Java8SearchCriteria;
import org.gamboni.cloudspill.server.query.ServerSearchCriteria;
import org.gamboni.cloudspill.shared.api.CloudSpillApi;
import org.gamboni.cloudspill.shared.api.ItemCredentials;
import org.gamboni.cloudspill.shared.domain.InvalidPasswordException;
import org.gamboni.cloudspill.shared.domain.IsUser;
import org.gamboni.cloudspill.shared.domain.ItemType;
import org.gamboni.cloudspill.shared.util.ImageOrientationUtil;
import org.gamboni.cloudspill.shared.util.Log;
import org.mindrot.jbcrypt.BCrypt;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.function.BiFunction;

import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.Query;

import spark.Request;
import spark.Response;

import static org.gamboni.cloudspill.shared.util.Files.append;
import static spark.Spark.post;

/** Planned general API structure:
 * /public/* for stuff that does not require authentication
 * /api/* for everything that isn't media or html
 * /public/api/* both of the above
 *
 *
 *
 *
 * @author tendays
 */
public class CloudSpillServer extends CloudSpillBackend<ServerDomain> {

	/** This constants holds the version of the data stored in the database.
	 * Each time new information is added to all items the number should be incremented.
	 * It will cause clients to refresh their database with an /item/since/ call.
	 */
	private static final int DATA_VERSION = 1;

	@Inject	ServerConfiguration configuration;

	/** Maps UserAuthToken ids to a Boolean saying if they have been validated.
	 * This map only contains ids which have a pending login() call.
	 * All read or write access must be synchronized on the Map itself, with a wait() for reads and a notifyAll() for writes.
	 */
	private final Map<Long, TokenWatch> watchedTokens = new HashMap<>();

	private static class TokenWatch {
		int watchCount = 0;
		boolean valid = false;
	}

	private final Semaphore heavyTask = new Semaphore(6, true);

    public static void main(String[] args) {
        boolean forward = false;
        boolean allowAnonymousUserCreation = false;
        String configPath = null;

        for (String arg : args) {
            if (arg.equals("-forward")) {
                forward = true;
			} else if (arg.equals("-allowAnonymousUserCreation")) {
				allowAnonymousUserCreation = true;
            } else if (configPath == null) {
				configPath = arg;
            } else {
                exitWithUsage();
            }
        }
    	if (configPath == null) {
            exitWithUsage();
        }


        try {
            if (forward) {
                Guice.createInjector(new ForwarderModule(configPath)).getInstance(CloudSpillForwarder.class).run();
            } else {
                Guice.createInjector(new ServerModule(configPath)).getInstance(CloudSpillServer.class).run(allowAnonymousUserCreation);
            }
    	} catch (Throwable t) {
    		t.printStackTrace();
    		System.exit(1);
    	}
    }

    private static void exitWithUsage() {
        Log.error("Usage: CloudSpillServer [-forward] configPath");
        System.exit(1);
    }

    public void run(boolean allowAnonymousUserCreation) {
    	File rootFolder = configuration.getRepositoryPath();

    	/* Upgrade database before serving */
    	Log.info("Upgrading database, please wait");
    	try {
    	transacted(session -> {
			final ServerDomain.Query<Item> itemQuery = session.selectItem();
			for (BackendItem item : itemQuery.add(root -> session.criteriaBuilder.isNull(
					root.get(Item_.checksum))).list()) {
    			File file = item.getFile(rootFolder);
    			final MessageDigest md5 = getMessageDigest();
				try (InputStream in = new FileInputStream(file)) {
					
				    byte[] buf = new byte[8192];
				    while (true) {
				      int len = in.read(buf);
				      if (len == -1) {
				        break;
				      }
				      md5.update(buf, 0, len);
				    }
				} catch (IOException e) {
					Log.warn("Error reading "+ file +" for item "+ item.getServerId(), e);
					continue;
				}
				item.setChecksum(
						new String(Base64.getEncoder().encode(md5.digest()), StandardCharsets.ISO_8859_1));
    		}
    		return true;
    	});
    	} catch (Exception e) {
    		e.printStackTrace();
    		return;
    	}
    	Log.info("Database upgrade complete.");
    	
    	/* Thumbnail construction is memory intensive... */
    	// TODO make this configurable
    	//threadPool(6);

		setupRoutes(configuration);
    	
		SecuredBody<ServerDomain> createUser = (req, res, session, user) -> {
			User u = new User();
			u.setName(requireNotNull(req.params(CloudSpillApi.CREATE_USER_NAME)));

			String salt = BCrypt.gensalt();
			u.setSalt(salt);

			u.setPass(BCrypt.hashpw(requireNotNull(req.queryParams(CloudSpillApi.CREATE_USER_PASS)), salt));

			session.persist(u);

			return true;
		};
		
		post("/user/:name",
				allowAnonymousUserCreation ?
						(req, res) -> transacted(session -> createUser.handle(req, res, session, /* user */null))
						: secured(createUser));
    }

	@Override
	protected OrHttpError<Item> loadItem(ServerDomain session, long id, ItemCredentials credentials) {
		Item item = session.get(Item.class, id);
		// NOTE: even if user is authenticated, refuse incorrect keys
		if (item == null) {
			return notFound(id);
		} else {
			try {
				verifyCredentials(credentials, item);
			} catch (InvalidPasswordException e) {
				return forbidden(false);
			}
			return new OrHttpError<>(item);
		}
	}

	@Override
	protected Long upload(Request req, Response res, ServerDomain session, ItemCredentials.UserCredentials credentials, String folder, String path) throws IOException {
		// Normalise given path
		File folderPath = append(append(configuration.getRepositoryPath(), credentials.user.getName()), folder);
		File requestedTarget = append(folderPath, path);

		if (requestedTarget == null) {
			res.status(400);
			return null;
		}

		// Path to put into the database
		String normalisedPath = requestedTarget.getPath().substring(folderPath.getCanonicalPath().length());
		if (normalisedPath.startsWith("/")) {
			normalisedPath = normalisedPath.substring(1);
		}
		String finalNormalisedPath = normalisedPath;

		/* First see if the path already exists. */
		final ServerDomain.Query<Item> itemQuery = session.selectItem();
		List<Item> existing = itemQuery
				.add(root -> session.criteriaBuilder.equal(root.get(Item_.user), credentials.user.getName()))
				.add(root -> session.criteriaBuilder.equal(root.get(Item_.folder), folder))
				.add(root -> session.criteriaBuilder.equal(root.get(Item_.path), finalNormalisedPath))
				.list();

		switch (existing.size()) {
			case 0:
				Item item = new Item();
				item.setFolder(folder);
				item.setPath(normalisedPath);
				item.setUser(credentials.user.getName());
				final String timestampHeader = req.headers(CloudSpillApi.UPLOAD_TIMESTAMP_HEADER);
				if (timestampHeader != null) {
					item.setDate(Instant.ofEpochMilli(Long.valueOf(timestampHeader))
							.atOffset(ZoneOffset.UTC)
							.toLocalDateTime());
					item.setDatePrecision("s");
				}
				final String typeHeader = req.headers(CloudSpillApi.UPLOAD_TYPE_HEADER);
				if (typeHeader != null) {
					try {
						item.setType(ItemType.valueOf(typeHeader));
					} catch (IllegalArgumentException e) {
						Log.warn("Received invalid item type "+ typeHeader);
						// Then just leave it blank
					}
				}
				//session.persist(item);
				// TODO checksum update below fails if we do this: session.flush(); // flush before writing to disk


				requestedTarget.getParentFile().mkdirs();
				// TODO return 40x error in case content-length is missing or invalid
				/*long contentLength = Long.parseLong(req.headers("Content-Length")); */
				final MessageDigest md5 = getMessageDigest();
				Log.debug("Writing bytes to " + requestedTarget);
				try (InputStream in = req.raw().getInputStream();
					 FileOutputStream out = new FileOutputStream(requestedTarget)) {

					byte[] buf = new byte[8192];
					long copied = 0;
					while (true) {
						int len = in.read(buf);
						if (len == -1) {
							break;
						}
						md5.update(buf, 0, len);
						out.write(buf, 0, len);
						copied += len;
					}

					Log.debug("Wrote "+ copied +" bytes to "+ requestedTarget);
/*					if (copied != contentLength) {
						throw new IllegalArgumentException("Expected "+ contentLength +" bytes, got "+ copied);
					}*/
				}
				item.setChecksum(
						new String(Base64.getEncoder().encode(md5.digest()), StandardCharsets.ISO_8859_1));

				session.persist(item);

				Log.debug("Returning id "+ item.getId());
				return item.getId();

			case 1:
				// throw away input data.
				// TODO we should ideally just return early but it makes Java clients crash
				ByteStreams.exhaust(req.raw().getInputStream());
				return existing.get(0).getId();

			default:
				res.status(500);
				Log.warn("Collision detected: " + existing);
				return null;
		}
	}

	private MessageDigest getMessageDigest() {
    	try {
			return MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
    		throw new RuntimeException(e);
		}
	}

	@Override
	protected OrHttpError<String> ping(ServerDomain session, ItemCredentials.UserCredentials credentials) {
    	try {
    		verifyCredentials(credentials, null);
		} catch (InvalidPasswordException e) {
    		return forbidden(false);
		}
		// WARN: currently the frontend requires precisely this syntax, spaces included
		return new OrHttpError<>(CloudSpillApi.PING_PREAMBLE + "\n"
				+ CloudSpillApi.PING_DATA_VERSION + ": "+ DATA_VERSION +"\n"
				+ CloudSpillApi.PING_PUBLIC_URL + ": "+ configuration.getPublicUrl());
	}

	@Override
	protected OrHttpError<User> getUser(String username, CloudSpillEntityManagerDomain session) {
		return getUserFromDB(username, session);
	}

	@Override
	protected OrHttpError<ItemCredentials.UserToken> newToken(String username, String description) {
    	return transactedOrError(session -> {
			String chars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
			final SecureRandom random = new SecureRandom();
			final String secret = random.ints(255).map(n -> chars.charAt(Math.abs(n) % (chars.length())))
					.collect(StringBuilder::new, (builder, chr) -> builder.append((char) chr),
							(a, b) -> {
								throw new UnsupportedOperationException();
							}).toString();
			UserAuthToken token = new UserAuthToken();
			token.setValue(secret);
			token.setValid(false);
			final User user = session.get(User.class, username);
			token.setUser(user);
			token.setDescription(description);
			session.persist(token);

			return new ItemCredentials.UserToken(user, token.getId(), secret);
		});
	}

	@Override
	protected OrHttpError<Boolean> login(ItemCredentials.UserToken credentials) {
		return transactedOrError(session -> {
			final UserAuthToken token = session.get(UserAuthToken.class, credentials.id);
			if (token == null ||
					!token.getValue().equals(credentials.secret) ||
					!token.getUser().getName().equals(credentials.user.getName())) {
				throw new InvalidPasswordException();
			}
			if (token.getValid()) {
				return true;
			}
			synchronized (watchedTokens) {
				TokenWatch watch = watchedTokens.compute(token.getId(), (__, w) -> {
					if (w == null) {
						w = new TokenWatch();
					}
					w.watchCount++;
					return w;
				});
				/* "Long-polling": wait at most one minute */
				final long deadline = System.currentTimeMillis() + 60_000;
				while (System.currentTimeMillis() < deadline && !watch.valid) {
					//  max() needed in case the deadline expires right after above condition check
					watchedTokens.wait(Math.max(1, deadline - System.currentTimeMillis()));
				}
				watch.watchCount --;

				if (watch.watchCount == 0) {
					watchedTokens.remove(token.getId());
				}
				return watch.valid;
			}
		});
	}

	@Override
	protected OrHttpError<Object> validateToken(ServerDomain session, String username, long tokenId) {
		final UserAuthToken token = session.get(UserAuthToken.class, tokenId);
		if (token == null || !token.getUser().getName().equals(username)) {
			return forbidden(false);
		}

		token.setValid(true);
		session.flush(); // to acquire lock (would be better to do a for update earlier)
		synchronized(watchedTokens) {
			/* If anybody's waiting for this to be validated, let them grant access. */
			final TokenWatch tokenWatch = watchedTokens.get(tokenId);
			if (tokenWatch != null) {
				tokenWatch.valid = true;
				watchedTokens.notifyAll();
			}
		}
		return new OrHttpError<>( "ok");
	}


	@Override
	protected void verifyUserToken(IsUser user, long id, String secret) throws InvalidPasswordException {
    	transactedOrError(session -> {
			final UserAuthToken token = session.get(UserAuthToken.class, id);
			if (token == null ||
					!token.getValue().equals(secret) ||
					!token.getUser().getName().equals(user.getName())) {
				throw new InvalidPasswordException();
			}

			if (!token.getValid() || !token.getValue().equals(secret)) {
				throw new InvalidPasswordException();
			}
			return null;
		}, InvalidPasswordException.class);
	}


	@Override
	protected OrHttpError<List<UserAuthToken>> listInvalidTokens(ServerDomain session, ItemCredentials.UserCredentials user) {
    	return new OrHttpError<>(session.selectUserAuthToken()
				.add(uat -> session.criteriaBuilder.equal(uat.get(UserAuthToken_.user).get(User_.name), user.user.getName()))
				.add(uat -> session.criteriaBuilder.equal(uat.get(UserAuthToken_.valid), false))
				.list());
	}

	@Override
	protected ItemQueryLoader getQueryLoader(ServerDomain session, ItemCredentials credentials) {
		return criteria -> {
			final CloudSpillEntityManagerDomain.Query<Item> query = criteria.applyTo(session.selectItem(), credentials.getAuthStatus());
			return new OrHttpError<>(new ItemSet(
					query.getTotalCount(),
					query.offset(criteria.getOffset()).limit(criteria.getLimit()).list(),
					criteria.buildTitle(),
					criteria.getDescription()));
		};
	}

	@Override
	protected Java8SearchCriteria<BackendItem> loadGallery(ServerDomain session, long partId) {
		return session.get(GalleryPart.class, partId);
	}

	@Override
	protected OrHttpError<GalleryListData> galleryList(ItemCredentials credentials, ServerDomain domain) {
		final CloudSpillEntityManagerDomain.Query<GalleryPart> query = domain.selectGalleryPart();
		return new OrHttpError<>(new GalleryListData(configuration.getRepositoryName(), Lists.transform(
				query.addOrder(root -> domain.criteriaBuilder.desc(root.get(GalleryPart_.from))).list(),
				gp -> {
					final List<Item> sample = gp.applyTo(domain.selectItem(), credentials.getAuthStatus()).limit(1).list();
					return sample.isEmpty() ? new GalleryListPage.Element(gp, null, null) :
							new GalleryListPage.Element(gp, sample.get(0).getId(), sample.get(0).getChecksum());
				})));
	}

	@Override
	protected OrHttpError<String> title() {
		return new OrHttpError<>(configuration.getRepositoryName());
	}

	@Override
	protected OrHttpError<GalleryListData> dayList(ItemCredentials credentials, ServerDomain domain, int year) {
		final Query query = domain.getEntityManager().createNativeQuery(
				"select id, date(date) as date, checksum from Item " +
						"where id in (select max(id) from Item where date >= ? && date < ? group by date(date)) " +
						"order by date");
		query.setParameter(1, LocalDate.ofYearDay(year, 1));
		query.setParameter(2, LocalDate.ofYearDay(year+1, 1));
		return new OrHttpError<>(new GalleryListData("Year "+ year, Lists.transform((List<Object[]>)query.getResultList(), row -> {
			long id = ((Number) row[0]).longValue();
			LocalDate date = ((java.sql.Date) row[1]).toLocalDate();
			String checksum = (String) row[2];
			return new GalleryListPage.Element(ServerSearchCriteria.ALL.at(date), id, checksum);
		})));
	}

	protected void putTags(ServerDomain session, long id, String tags) {
		final Item item = itemForUpdate(session, id);

		final Set<String> existingTags = item.getTags();
		Splitter.on(',').split(tags).forEach(t -> {
			if (t.startsWith("-")) {
				existingTags.remove(t.substring(1).trim());
			} else {
				existingTags.add(t.trim());
			}
		});
	}

	private Item itemForUpdate(ServerDomain session, long id) {
		final ServerDomain.Query<Item> itemQuery = session.selectItem();
		final Item item = Iterables.getOnlyElement(
				itemQuery.add(root -> session.criteriaBuilder.equal(root.get(Item_.id), id)).forUpdate().list());
		Log.debug("Loaded item "+ id +" for update, at timestamp "+ item.getUpdated().toString());
		session.reload(item);
		Log.debug("After reload, item "+ id +" has timestamp "+ item.getUpdated().toString());
		return item;
	}

	@Override
	protected void download(Response res, ServerDomain session, ItemCredentials credentials, final BackendItem item)
			throws IOException {
		File file = item.getFile(configuration.getRepositoryPath());
		res.header("Content-Type", item.getType().asMime());
		res.header("Content-Length", String.valueOf(file.length()));
		try (FileInputStream stream = new FileInputStream(file)) {
			ByteStreams.copy(stream, res.raw().getOutputStream());
		}
	}

	@Override
	protected Object thumbnail(Response res, ServerDomain session, ItemCredentials credentials, final BackendItem item, int size)
			throws InterruptedException, IOException {
		File file = item.getFile(configuration.getRepositoryPath());
		
		if (!file.exists()) {
			return gone(res, item.getServerId(), file);
		}
		
		res.header("Content-Type", "image/jpeg");
		heavyTask.acquire();
		try {
		BufferedImage renderedImage = 
		 (item.getType() == ItemType.IMAGE) ?        		
		createImageThumbnail(file, size) :
			createVideoThumbnail(file, size);
		ImageIO.write(renderedImage, "jpeg", res.raw().getOutputStream());
		} finally {
			heavyTask.release();
		}
		return "";
	}

    private BufferedImage createVideoThumbnail(File file, int size) throws IOException {
    	try (FFmpegFrameGrabber g = new FFmpegFrameGrabber(file)) {
			g.start();
		
			final BufferedImage frame = new Java2DFrameConverter().convert(g.grabImage());

			return resize(frame.getWidth(), frame.getHeight(), size, 1, (scaledWidth, scaledHeight) -> 
				frame.getScaledInstance(scaledWidth, scaledHeight, Image.SCALE_DEFAULT));
			
    	} catch (FrameGrabber.Exception e) {
    		throw new RuntimeException(e);
		}
    }

	private BufferedImage createImageThumbnail(File file, int size) throws IOException {
		// load an image
		Image image = ImageIO.read(file);
		
		final ImageObserver imageObserver = (Image img, int infoflags, int x, int y, int newWidth, int height) ->
			((infoflags | ImageObserver.ALLBITS) == ImageObserver.ALLBITS);

		return resize(image.getWidth(imageObserver), image.getHeight(imageObserver), size, ImageOrientationUtil.getExifRotation(file), (scaledWidth, scaledHeight) ->
			 image.getScaledInstance(scaledWidth, scaledHeight, Image.SCALE_SMOOTH));
	}
	
	private BufferedImage resize(int width, int height, int targetSize, int orientation, BiFunction<Integer, Integer, Image> resizeFunction) {

		int min = Math.min(width, height);
		
		// Not clear if this happens in real life?
		if (min < 0) { throw new IllegalStateException("Asynchronous image io is not supported"); }
		
		// Have the *smallest* dimension of the image be the requested 'size'
		final int scaledWidth = width * targetSize / min;
		final int scaledHeight = height * targetSize / min;
		Image scaledImage = resizeFunction.apply(scaledWidth, scaledHeight);
		
		// Convert abstract Image into RenderedImage.
		BufferedImage renderedImage = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_3BYTE_BGR);
		boolean[] ready = new boolean[] { false };
		final Graphics2D graphics = renderedImage.createGraphics();
		if (orientation == 6) {
			final AffineTransform transform = graphics.getTransform();
			transform.translate(targetSize, 0);
			transform.rotate(Math.toRadians(90));
			graphics.setTransform(transform);
		} else if (orientation == 3) {
			final AffineTransform transform = graphics.getTransform();
			transform.translate(targetSize, targetSize);
			transform.rotate(Math.toRadians(180));
			graphics.setTransform(transform);
		} else if (orientation == 8) {
			final AffineTransform transform = graphics.getTransform();
			transform.translate(0, targetSize);
			transform.rotate(Math.toRadians(270));
			graphics.setTransform(transform);
		}
		ready[0] = graphics.drawImage(scaledImage,
				/*
				 * Center 'image' on 'renderedImage' (which may be smaller
				 * if 'image' is not square)
				 */
				(targetSize - scaledWidth) / 2, (targetSize - scaledHeight) / 2,
				(these, infoflags, parameters, are, not, needed) -> {
					if ((infoflags | ImageObserver.ALLBITS) == ImageObserver.ALLBITS) {
						synchronized (ready) {
							ready[0] = true;
							ready.notify();
						}
						return false;
					} else {
						return true; // need more data
					}
				});

		synchronized (ready) {
			try {
				while (!ready[0]) {
					ready.wait();
				}
			} catch (InterruptedException e) {
			}
		}
		return renderedImage;
	}
	

	@Override
	protected ServerDomain createDomain(EntityManager e) {
		return new ServerDomain(e);
	}
}
