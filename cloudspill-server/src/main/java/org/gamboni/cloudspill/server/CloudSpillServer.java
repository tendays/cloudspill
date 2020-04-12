/**
 * 
 */
package org.gamboni.cloudspill.server;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.io.ByteStreams;
import com.google.inject.Guice;
import com.google.inject.Injector;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.gamboni.cloudspill.domain.CloudSpillEntityManagerDomain;
import org.gamboni.cloudspill.domain.GalleryPart;
import org.gamboni.cloudspill.domain.ServerDomain;
import org.gamboni.cloudspill.domain.Item;
import org.gamboni.cloudspill.domain.Item_;
import org.gamboni.cloudspill.domain.User;
import org.gamboni.cloudspill.server.html.GalleryListPage;
import org.gamboni.cloudspill.server.html.GalleryPage;
import org.gamboni.cloudspill.server.html.HtmlFragment;
import org.gamboni.cloudspill.server.html.ImagePage;
import org.gamboni.cloudspill.server.query.ItemSet;
import org.gamboni.cloudspill.server.query.Java8SearchCriteria;
import org.gamboni.cloudspill.server.query.LocalItemSet;
import org.gamboni.cloudspill.server.query.ServerSearchCriteria;
import org.gamboni.cloudspill.shared.api.CloudSpillApi;
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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.function.BiFunction;

import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.persistence.EntityManager;

import spark.Request;
import spark.Response;
import spark.Route;

import static org.gamboni.cloudspill.shared.api.CloudSpillApi.ID_HTML_SUFFIX;
import static org.gamboni.cloudspill.shared.util.Files.append;
import static spark.Spark.before;
import static spark.Spark.get;
import static spark.Spark.post;
import static spark.Spark.put;

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

	@Inject ServerConfiguration configuration;

	/** This constants holds the version of the data stored in the database.
	 * Each time new information is added to all items the number should be incremented.
	 * It will cause clients to refresh their database with an /item/since/ call.
	 */
	private static final int DATA_VERSION = 1;
	
    public static void main(String[] args) {
    	if (args.length != 1) {
    		Log.error("Usage: CloudSpillServer configPath");
    		System.exit(1);
    	}
    	Injector injector = Guice.createInjector(new ServerModule(args[0]));
    	
    	try {
    		injector.getInstance(CloudSpillServer.class).run();
    	} catch (Throwable t) {
    		t.printStackTrace();
    		System.exit(1);
    	}
    }
    
    private final Semaphore heavyTask = new Semaphore(6, true);


	protected String ping() {
		// WARN: currently the frontend requires precisely this syntax, spaces included
		return CloudSpillApi.PING_PREAMBLE + "\n"
				+ CloudSpillApi.PING_DATA_VERSION + ": "+ DATA_VERSION +"\n"
				+ CloudSpillApi.PING_PUBLIC_URL + ": "+ configuration.getPublicUrl();
	}

	@Override
	protected ItemSet doSearch(ServerDomain domain, Java8SearchCriteria<Item> criteria) {
		return new LocalItemSet(criteria, domain);
	}

	@Override
	protected ItemSet loadGallery(ServerDomain domain, long partId) {
		return new LocalItemSet(domain.get(GalleryPart.class, partId), domain);
	}

	@Override
	protected HtmlFragment galleryListPage(ServerDomain domain, User user) {
		return new GalleryListPage(configuration, domain).getHtml(user);
	}

    public void run() {
		CloudSpillApi api = new CloudSpillApi("");
    	File rootFolder = configuration.getRepositoryPath();

    	/* Upgrade database before serving */
    	Log.info("Upgrading database, please wait");
    	try {
    	transacted(session -> {
			final ServerDomain.Query<Item> itemQuery = session.selectItem();
			for (Item item : itemQuery.add(root -> session.criteriaBuilder.isNull(
					root.get(Item_.checksum))).list()) {
    			File file = item.getFile(rootFolder);
    			final MessageDigest md5 = MessageDigest.getInstance("MD5");
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
					Log.warn("Error reading "+ file +" for item "+ item.getId(), e);
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
    	
		SecuredBody createUser = (req, res, session, user) -> {
			User u = new User();
			u.setName(requireNotNull(req.params(CloudSpillApi.CREATE_USER_NAME)));

			String salt = BCrypt.gensalt();
			u.setSalt(salt);

			u.setPass(BCrypt.hashpw(requireNotNull(req.queryParams(CloudSpillApi.CREATE_USER_PASS)), salt));

			session.persist(u);

			return true;
		};
		
		post("/user/:name",
				configuration.allowAnonymousUserCreation() ?
						(req, res) -> transacted(session -> createUser.handle(req, res, session, /* user */null))
						: secured(createUser));
        
        /* Upload a file */
        put(api.upload(":user", ":folder", "*"), secured((req, res, session, user) -> {
        	
        	/*if (req.bodyAsBytes() == null) {
        		Log.warn("Missing body");
        		res.status(400);
        		return null;
        	}*/
        	
        	String username = req.params("user");
        	String folder = req.params("folder");
        	if (!user.getName().equals(username)) {
        		Log.error("User "+ user.getName() +" attempted to upload to folder of user "+ username);
        		return forbidden(res, false);
        	}
			String path = req.splat()[0];
			Log.debug("user is "+ username +", folder is "+ folder +" and path is "+ path);
        	
			// Normalise given path
        	File folderPath = append(append(rootFolder, username), folder);
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
        		.add(root -> session.criteriaBuilder.equal(root.get(Item_.user), username))
        		.add(root -> session.criteriaBuilder.equal(root.get(Item_.folder), folder))
        		.add(root -> session.criteriaBuilder.equal(root.get(Item_.path), finalNormalisedPath))
        		.list();
        	
			switch (existing.size()) {
			case 0:
				Item item = new Item();
				item.setFolder(folder);
				item.setPath(normalisedPath);
				item.setUser(username);
				final String timestampHeader = req.headers(CloudSpillApi.UPLOAD_TIMESTAMP_HEADER);
				if (timestampHeader != null) {
					item.setDate(Instant.ofEpochMilli(Long.valueOf(timestampHeader))
						.atOffset(ZoneOffset.UTC)
						.toLocalDateTime());
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
				final MessageDigest md5 = MessageDigest.getInstance("MD5");
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
				return existing.get(0).getId();

			default:
				res.status(500);
				Log.warn("Collision detected: " + existing);
				return null;
			}
        }));
    }

    /** Add the given comma-separated tags to the specified object. If a tag starts with '-' then it is removed instead.
     * <p>
     * NOTE: anybody can change tags of anybody's item.
     */
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

	protected void download(Response res, ServerDomain session, final Item item)
			throws IOException {
		File file = item.getFile(configuration.getRepositoryPath());
		res.header("Content-Type", "image/jpeg");
		res.header("Content-Length", String.valueOf(file.length()));
		try (FileInputStream stream = new FileInputStream(file)) {
			ByteStreams.copy(stream, res.raw().getOutputStream());
		}
	}

	protected Object thumbnail(Response res, ServerDomain session, final Item item, int size)
			throws InterruptedException, IOException {
		File file = item.getFile(configuration.getRepositoryPath());
		
		if (!file.exists()) {
			return gone(res, item.getId(), file);
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
		return true;
	}

	private StringBuilder itemsSince(ServerDomain domain, final Instant instant) {
		StringBuilder result = new StringBuilder();
		Instant timestamp = instant;
		final ServerDomain.Query<Item> itemQuery = domain.selectItem();
		for (Item item : itemQuery
				.add(root -> domain.criteriaBuilder.greaterThanOrEqualTo(
						root.get(Item_.updated), instant))
				.addOrder(root -> domain.criteriaBuilder.asc(root.get(Item_.updated)))
				.limit(500) // large datasets make the Android client crash
				.list()) {
			result.append(item.serialise()).append("\n");
			timestamp = item.getUpdated();
		}
		result.append("Timestamp:").append(timestamp.toEpochMilli()).append('\n');
		return result;
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
	
	protected Route securedItem(SecuredItemBody task) {
		return (req, res) -> transacted(session -> {
			// Work around what looks like Whatsapp bug: even though url encodes correctly + as %2B,
			// It is sent as raw + in the test query to construct thumbnail and reaches us as a space,
			// so we tolerate that and map spaces back to pluses, as spaces are anyway not allowed in
			// b64
			String key = req.queryParams("key");
			if (key != null) { key = key.replace(' ', '+'); }
			
			String idParam = req.params("id");
			if (idParam != null && idParam.endsWith(ID_HTML_SUFFIX)) {
				idParam = idParam.substring(0, idParam.length() - ID_HTML_SUFFIX.length());
			}
			
			/* Either we have a key, or user is authenticated. */
			final long id = Long.parseLong(idParam);
			final Item item = session.get(Item.class, id);
			
			if (item == null) {
				return notFound(res, id);
			}
			
			// NOTE: even if user is authenticated, refuse incorrect keys
			if (key != null && !key.equals(item.getChecksum())) {
				Log.warn("Bad key value. Expected "+ item.getChecksum() +", got "+ key);
				return forbidden(res, false);
			}

			User user;
			if (key == null) {
				user = authenticate(req, res, session);
				if (user == null) {
					return String.valueOf(res.status());
				}
			} else {
				user = optionalAuthenticate(req, res, session);
			}

			return task.handle(req, res, session, user, item);
		});
	}

	@Override
	protected ServerDomain createDomain(EntityManager e) {
		return new ServerDomain(e);
	}
}
