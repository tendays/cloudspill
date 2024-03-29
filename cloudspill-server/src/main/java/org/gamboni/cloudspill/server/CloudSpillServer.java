/**
 * 
 */
package org.gamboni.cloudspill.server;

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
import org.gamboni.cloudspill.lambda.MetadataExtractor;
import org.gamboni.cloudspill.server.config.ServerConfiguration;
import org.gamboni.cloudspill.server.html.GalleryListPage;
import org.gamboni.cloudspill.server.query.ItemQueryLoader;
import org.gamboni.cloudspill.server.query.ItemSet;
import org.gamboni.cloudspill.server.query.Java8SearchCriteria;
import org.gamboni.cloudspill.server.query.ServerSearchCriteria;
import org.gamboni.cloudspill.shared.api.CloudSpillApi;
import org.gamboni.cloudspill.shared.api.ItemCredentials;
import org.gamboni.cloudspill.shared.api.ItemMetadata;
import org.gamboni.cloudspill.shared.api.LoginState;
import org.gamboni.cloudspill.shared.domain.AccessDeniedException;
import org.gamboni.cloudspill.shared.domain.Comment;
import org.gamboni.cloudspill.shared.domain.InvalidPasswordException;
import org.gamboni.cloudspill.shared.domain.IsUser;
import org.gamboni.cloudspill.shared.domain.ItemType;
import org.gamboni.cloudspill.shared.domain.Items;
import org.gamboni.cloudspill.shared.query.QueryRange;
import org.gamboni.cloudspill.shared.util.ImageOrientationUtil;
import org.gamboni.cloudspill.shared.util.Log;
import org.mindrot.jbcrypt.BCrypt;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.function.BiFunction;

import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.Query;

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
		LoginState state = LoginState.WAITING_FOR_VALIDATION;
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
			User u = User.withName(requireNotNull(req.params(CloudSpillApi.CREATE_USER_NAME)));

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
	protected OrHttpError<Item> loadItem(ServerDomain session, long id, List<ItemCredentials> credentials) {
		Item item = session.get(Item.class, id);
		// NOTE: even if user is authenticated, refuse incorrect keys
		if (item == null) {
			return notFound(id);
		} else {
			try {
				verifyCredentials(credentials, item);
			} catch (AccessDeniedException e) {
				Log.error("Denied access to item "+ id +": "+ e.toString());
				return forbidden(false);
			}
			return new OrHttpError<>(item);
		}
	}

	@Override
	protected OrHttpError<Long> upload(ServerDomain session, ItemCredentials.UserCredentials credentials, InputStream inputStream, String folder, String path,
						  ItemMetadata metadata) throws IOException {
		// Normalise given path
		File folderPath = append(append(configuration.getRepositoryPath(), credentials.user.getName()), folder);
		File requestedTarget = append(folderPath, path);

		if (requestedTarget == null) {
			return badRequest();
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

				requestedTarget.getParentFile().mkdirs();
				// TODO return 40x error in case content-length is missing or invalid
				/*long contentLength = Long.parseLong(req.headers("Content-Length")); */
				final MessageDigest md5 = getMessageDigest();
				Log.debug("Writing bytes to " + requestedTarget);
				try (BufferedInputStream in = new BufferedInputStream(inputStream);
					 FileOutputStream out = new FileOutputStream(requestedTarget)) {

					//session.persist(item);
					// TODO checksum update below fails if we do this: session.flush(); // flush before writing to disk

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


				if (metadata.itemDate == null || metadata.itemType == null) {
					final ItemMetadata inferredMetadata = MetadataExtractor.getItemMetadata(null, requestedTarget);
					// client-provided metadata has higher priority
					metadata = inferredMetadata.overrideWith(metadata);
				}

				if (metadata.itemDate != null) {
					item.setDate(metadata.itemDate.toInstant().atZone(ZoneOffset.UTC).toLocalDateTime());
				}
				item.setDatePrecision("s");
				if (metadata.itemType != null) {
					item.setType(metadata.itemType);
				}

				item.setChecksum(
						new String(Base64.getEncoder().encode(md5.digest()), StandardCharsets.ISO_8859_1));

				session.persist(item);

				Log.debug("Returning id "+ item.getId());
				return new OrHttpError<>(item.getId());

			case 1:
				// throw away input data.
				// TODO we should ideally just return early but it makes Java clients crash
				ByteStreams.exhaust(inputStream);
				return new OrHttpError<>(existing.get(0).getId());

			default:
				Log.warn("Collision detected: " + existing);
				return internalServerError();
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
		} catch (AccessDeniedException e) {
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
	protected OrHttpError<ItemCredentials.UserToken> newToken(String username, String userAgent, String client) {
    	username = username.toLowerCase(Locale.ROOT);
    	String normalisedUsername = username;
    	return transactedOrError(session -> {
			final String secret = Security.newRandomString(255);
			UserAuthToken token = new UserAuthToken();
			token.setValue(secret);
			token.setValid(false);
			final User user = session.get(User.class, normalisedUsername);
			token.setUser(user);
			token.setDescription(client +" "+ userAgent +" at "+ LocalDateTime.now());
			token.setMachine(userAgent);
			token.setCreationDate(Instant.now());
			token.setIp(client);
			session.persist(token);

			return new ItemCredentials.UserToken(user, token.getId(), secret);
		});
	}

	@Override
	protected OrHttpError<LoginState> login(ItemCredentials.UserToken credentials) {
		return transactedOrError(session -> {
			final UserAuthToken token = session.get(UserAuthToken.class, credentials.id);
			if (token == null ||
					!token.getValue().equals(credentials.secret) ||
					!token.getUser().getName().equals(credentials.user.getName())) {
				throw new InvalidPasswordException("Incorrect secret for token #"+ credentials.id);
			}
			if (token.getValid()) {
				return LoginState.LOGGED_IN;
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
				while (System.currentTimeMillis() < deadline && watch.state == LoginState.WAITING_FOR_VALIDATION) {
					//  max() needed in case the deadline expires right after above condition check
					watchedTokens.wait(Math.max(1, deadline - System.currentTimeMillis()));
				}
				watch.watchCount --;

				if (watch.watchCount == 0) {
					watchedTokens.remove(token.getId());
				}
				return watch.state;
			}
		});
	}

    @Override
    protected OrHttpError<String> logout(ServerDomain session, ItemCredentials.UserToken credentials) {
        final UserAuthToken token = session.get(UserAuthToken.class, credentials.id);
        if (token == null || !token.getValue().equals(credentials.secret)) {
            return forbidden(false);
        }

        session.remove(token);
        session.flush(); // to acquire lock (would be better to do a for update earlier)
        synchronized(watchedTokens) {
            /* If anybody's waiting for this to be validated, let them deny access. */
            final TokenWatch tokenWatch = watchedTokens.get(credentials.id);
            if (tokenWatch != null) {
                tokenWatch.state = LoginState.INVALID_TOKEN;
                watchedTokens.notifyAll();
            }
        }
        return new OrHttpError<>( "ok");
    }

	@Override
	protected OrHttpError<Object> validateToken(ItemCredentials.UserCredentials credentials, ServerDomain session, String username, long tokenId) {
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
				tokenWatch.state = LoginState.LOGGED_IN;
				watchedTokens.notifyAll();
			}
		}
		return new OrHttpError<>( "ok");
	}

	@Override
	protected OrHttpError<Object> deleteToken(ItemCredentials.UserCredentials credentials, ServerDomain session, String username, long tokenId) {
		final UserAuthToken token = session.get(UserAuthToken.class, tokenId);
		if (token == null || !token.getUser().getName().equals(username)) {
			return forbidden(false);
		}

		session.remove(token);
		session.flush(); // to acquire lock (would be better to do a for update earlier)
		synchronized(watchedTokens) {
			/* If anybody's waiting for this to be validated, let them fail immediately. */
			final TokenWatch tokenWatch = watchedTokens.get(tokenId);
			if (tokenWatch != null) {
				tokenWatch.state = LoginState.INVALID_TOKEN;
				watchedTokens.notifyAll();
			}
		}
		return new OrHttpError<>( "ok");
	}


	@Override
	protected LoginState getUserTokenState(IsUser user, long id, String secret) {
    	return transactedOrError(session -> {
			final UserAuthToken token = session.get(UserAuthToken.class, id);
			if (token == null ||
					!token.getValue().equals(secret) ||
					!token.getUser().getName().equals(user.getName())) {
				return LoginState.INVALID_TOKEN;
			} else if (!token.getValid()) {
				return LoginState.WAITING_FOR_VALIDATION;
			} else {
				return LoginState.LOGGED_IN;
			}
		}).orElse(() -> LoginState.INVALID_TOKEN);
	}


	@Override
	protected OrHttpError<List<UserAuthToken>> listTokens(ServerDomain session, String name, ItemCredentials.UserCredentials credentials) {
    	if (!name.equals(credentials.user.getName()) && !credentials.user.hasGroup(User.ADMIN_GROUP)) {
    		return forbidden(false);
		}
    	return new OrHttpError<>(session.selectUserAuthToken()
				.add(uat -> session.criteriaBuilder.equal(uat.get(UserAuthToken_.user).get(User_.name), name))
				.list());
	}

	@Override
	protected ItemQueryLoader getQueryLoader(ServerDomain session, ItemCredentials credentials) {
		return criteria -> {
			final Item relativeTo;
            if (criteria.getRelativeTo() == null) {
                relativeTo = null;
            } else {
                relativeTo = session.get(Item.class, criteria.getRelativeTo());
                if (relativeTo == null) {
                    return badRequest();
                }
                try {
                    verifyCredentials(criteria.getItemCredentials(), relativeTo);
                } catch (AccessDeniedException e) {
                    return forbidden(false);
                }
            }

            if (relativeTo != null) {
                criteria = criteria.withRange(
                        criteria.getRange().shift(
                        criteriaToQuery(session, credentials, criteria)
                        .indexOf(relativeTo))
                .truncate());
            }

            final CloudSpillEntityManagerDomain.Query<Item> query = criteriaToQuery(session, credentials, criteria);

			return new OrHttpError<>(new ItemSet(
					query.getTotalCount(),
					query.range(criteria.getRange()).list(),
					criteria.buildTitle(),
					criteria.getDescription()));
		};
	}

    private CloudSpillEntityManagerDomain.Query<Item> criteriaToQuery(ServerDomain session, ItemCredentials credentials, Java8SearchCriteria<BackendItem> criteria) {
        final CloudSpillEntityManagerDomain.Query<Item> query = criteria.applyTo(session.selectItem(), credentials);
        // relativeTo calculation requires fully defined ordering, so we order by id after all other orderings
        query.addOrder(CloudSpillEntityManagerDomain.Ordering.asc(Item_.id));
        return query;
    }

    @Override
	protected Java8SearchCriteria<BackendItem> loadGallery(ServerDomain session, long partId, String providedKey) {
		return session.get(GalleryPart.class, partId).withKey(providedKey);
	}

	@Override
	protected OrHttpError<GalleryListPage.Model> galleryList(ItemCredentials credentials, ServerDomain domain) {
		final CloudSpillEntityManagerDomain.Query<GalleryPart> query = domain.selectGalleryPart();
		return new OrHttpError<>(new GalleryListPage.Model(credentials, configuration.getRepositoryName(), Lists.transform(
				query.addOrder(CloudSpillEntityManagerDomain.Ordering.desc(GalleryPart_.from)).list(),
				gp -> {
					final List<Item> sample = gp.applyTo(domain.selectItem(), credentials)
                            .range(QueryRange.limit(1)).list();
					return sample.isEmpty() ? new GalleryListPage.Element(gp, null, null) :
							new GalleryListPage.Element(gp, sample.get(0).getId(), sample.get(0).getChecksum());
				})));
	}

	@Override
	protected OrHttpError<String> title() {
		return new OrHttpError<>(configuration.getRepositoryName());
	}

	@Override
	protected OrHttpError<GalleryListPage.Model> dayList(ItemCredentials credentials, ServerDomain domain, int year) {
		final boolean isAdmin = credentials.hasGroup(User.ADMIN_GROUP);
		final Query query = domain.getEntityManager().createNativeQuery(
				"select id, date(date) as date, checksum from Item " +
						"where id in (select max(id) from Item where date >= ? and date < ? "+
						(isAdmin ? "" :
								" and ('public' in (select it.tags from Item_tags it where Item.id=it.Item_id) or Item.user=?)")
						+" group by date(date)) " +
						"order by date");
		query.setParameter(1, LocalDate.ofYearDay(year, 1));
		query.setParameter(2, LocalDate.ofYearDay(year+1, 1));
		if (!isAdmin) {
			query.setParameter(3, ((ItemCredentials.UserCredentials)credentials).user.getName());
		}
		return new OrHttpError<>(new GalleryListPage.Model(credentials, "Year "+ year, Lists.transform((List<Object[]>)query.getResultList(), row -> {
			long id = ((Number) row[0]).longValue();
			LocalDate date = ((java.sql.Date) row[1]).toLocalDate();
			String checksum = (String) row[2];
			return new GalleryListPage.Element(ServerSearchCriteria.ALL.at(date), id, checksum);
		})));
	}

	@Override
	protected OrHttpError<GalleryListPage.Model> tagGalleryList(ItemCredentials credentials, ServerDomain domain) {
		final boolean isAdmin = credentials.hasGroup(User.ADMIN_GROUP);
		final Query query = domain.getEntityManager().createNativeQuery(
				"select i, tags, checksum from " +
						"(select first.tags, max(id) i from Item_tags first, Item where first.Item_id = Item.id" +
						(isAdmin ? "" :
						" and ('public' in (select it.tags from Item_tags it where Item.id=it.Item_id) or Item.user=?)") +" group by first.tags) q, Item " +
								"where Item.id = i");
		if (!isAdmin) {
			query.setParameter(1, ((ItemCredentials.UserCredentials)credentials).user.getName());
		}
		return new OrHttpError<>(new GalleryListPage.Model(credentials, "All Tags", Lists.transform((List<Object[]>)query.getResultList(), row -> {
			long id = ((Number) row[0]).longValue();
			String tag = (String) row[1];
			String checksum = (String) row[2];
			return new GalleryListPage.Element(ServerSearchCriteria.ALL.withTag(tag), id, checksum);
		})));
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

	@Override
	protected OrHttpError<Instant> postComment(List<ItemCredentials> credentials, Long serverId, Comment comment) {
		return new OrHttpError<>(Instant.now());
	}
}
