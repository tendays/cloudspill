/**
 * 
 */
package org.gamboni.cloudspill.server;

import static org.gamboni.cloudspill.util.Files.append;
import static spark.Spark.before;
import static spark.Spark.get;
import static spark.Spark.post;
import static spark.Spark.put;
import static spark.Spark.threadPool;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.function.BiFunction;

import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.servlet.http.HttpServletResponse;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.gamboni.cloudspill.domain.Domain;
import org.gamboni.cloudspill.domain.Item;
import org.gamboni.cloudspill.domain.ItemType;
import org.gamboni.cloudspill.domain.User;
import org.gamboni.cloudspill.util.Log;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import org.mindrot.jbcrypt.BCrypt;

import com.google.common.collect.Iterables;
import com.google.common.io.ByteStreams;
import com.google.inject.Guice;
import com.google.inject.Injector;

import spark.Request;
import spark.Response;
import spark.Route;

/**
 * @author tendays
 *
 */
public class CloudSpillServer {
	
	@Inject SessionFactory sessionFactory;
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
    
    private interface TransactionBody<R> {
    	R run(Domain s) throws Exception;
    }
    
    private interface SecuredBody {
    	Object handle(Request request, Response response, Domain session, User user) throws Exception;
    }
    
    private Route secured(SecuredBody task) {
    	return (req, res) -> transacted(session -> {
    		final String authHeader = req.headers("Authorization");
    		final User user;
    		if (authHeader == null) {
    			Log.error("Missing Authorization header");
    			return unauthorized(res);
    			// Other option: user = null; // anonymous
    		} else if (authHeader.startsWith("Basic ")) {
    			final String token = authHeader.substring("Basic ".length()).trim();
    			final String credentials = new String(Base64.getDecoder().decode(token));
    			int colon = credentials.indexOf(':');
    			if (colon == -1) {
    				Log.error("Invalid Authorization header");
					return badRequest(res);
    			}
    			String username = credentials.substring(0, colon);
    			String password = credentials.substring(colon+1);
    			final List<User> users = session.selectUser().add(Restrictions.eq("name", username)).list();
    			if (users.isEmpty()) {
    				Log.error("Unknown user "+ username);
    				return forbidden(res, true);
    			}
    			user = Iterables.getOnlyElement(users);
    			final String queryHash = BCrypt.hashpw(password, user.getSalt());
    			if (!queryHash.equals(user.getPass())) {
    				Log.error("Invalid credentials for user "+ username);
    				return forbidden(res, true);
    			} else {
    				Log.info("User "+ username +" authenticated");
    			}
    		} else {
				Log.error("Unsupported Authorization scheme");
				return badRequest(res);
    		}
    		
    		return task.handle(req, res, session, user);
    	});
    }
    
    private Object notFound(Response res, long item) {
    	Log.error("Not found: item "+ item);
    	res.status(HttpServletResponse.SC_NOT_FOUND);
    	return "Not Found";
    }
    
    private Object gone(Response res, long item, File file) {
    	Log.error("Gone: "+ file +" for item "+ item);
    	res.status(HttpServletResponse.SC_GONE);
    	return "Gone";
    }

	private Object badRequest(Response res) {
		res.status(HttpServletResponse.SC_BAD_REQUEST);
		return "Bad Request";
	}

	private Object unauthorized(Response res) {
		res.status(HttpServletResponse.SC_UNAUTHORIZED);
		loginPrompt(res);
		return "Unauthorized";
	}

	private void loginPrompt(Response res) {
		res.header("WWW-Authenticate", "Basic realm=\"CloudSpill\"");
	}

	private Object forbidden(Response res, boolean loginPrompt) {
		if (loginPrompt) {
			return unauthorized(res);
			// I was hoping that a 403 with a www-authenticate would prompt the browser to show a login dialog, but it does not (Firefox)
			//loginPrompt(res);
		}
		res.status(HttpServletResponse.SC_FORBIDDEN);
		return "Forbidden";
	}
    
    private <R> R transacted(TransactionBody<R> task) throws Exception {
    	Session session = null;
    	Transaction tx = null;
    	try {
    		session = sessionFactory.openSession();
    		tx = session.beginTransaction();
    		R result = task.run(new Domain(session));
    		tx.commit();
    		tx = null;
    		Log.debug("Return value: "+ result);
			return result;
    	} catch (Throwable t) {
    		t.printStackTrace();
    		throw t;
    	} finally {
    		if (tx != null) { tx.rollback(); }
    		if (session != null) { session.close(); }
    	}
    }
    
    private static final <T> T requireNotNull(T value) {
    	if (value == null) {
    		throw new NullPointerException();
    	} else {
    		return value;
    	}
    }
    
    public void run() {
    	
    	File rootFolder = configuration.getRepositoryPath();
    	
    	/* Thumbnail construction is memory intensive... */
    	// TODO make this configurable
    	threadPool(6);
    	
    	/* Access logging */
    	before((req, res) -> {
    		Log.info(req.ip() +" "+ req.uri());
    	});
    	
		SecuredBody createUser = (req, res, session, user) -> {
			User u = new User();
			u.setName(requireNotNull(req.params("name")));

			String salt = BCrypt.gensalt();
			u.setSalt(salt);

			u.setPass(BCrypt.hashpw(requireNotNull(req.queryParams("pass")), salt));

			session.persist(u);

			return true;
		};

		post("/user/:name",
				configuration.allowAnonymousUserCreation() ?
						(req, res) -> transacted(session -> createUser.handle(req, res, session, /* user */null))
						: secured(createUser));

    	/* Used by clients to ensure connectivity is available and check API compatibility. */
    	get("/ping", secured((req, res, session, user) ->
    		// WARN: currently the frontend requires precisely this syntax, spaces included
    		"CloudSpill server.\n"
    		+ "Data-Version: "+ DATA_VERSION));
    	
    	/* Just for testing */
        get("/item/:id/path", secured((req, res, session, user) -> {
        	Item item = session.get(Item.class, Long.parseLong(req.params("id")));
        	return item.getPath();
        }));
        
        /* Download a file */
        get("/item/:id", secured((req, res, session, user) -> {
        	Item item = session.get(Item.class, Long.parseLong(req.params("id")));
        	File file = item.getFile(rootFolder);
        	res.header("Content-Type", "image/jpeg");
        	res.header("Content-Length", String.valueOf(file.length()));
        	try (FileInputStream stream = new FileInputStream(file)) {
        		ByteStreams.copy(stream, res.raw().getOutputStream());
        	}
        	return true;
        }));
        
        /* Download a thumbnail */
        get("/thumbs/:size/:id", secured((req, res, session, user) -> {
        	final long id = Long.parseLong(req.params("id"));
			Item item = session.get(Item.class, id);
        	if (item == null) {
        		return notFound(res, id);
        	}
        	int size = Integer.parseInt(req.params("size"));
        	File file = item.getFile(rootFolder);
        	
        	if (!file.exists()) {
        		return gone(res, item.getId(), file);
        	}
        	
        	res.header("Content-Type", "image/jpeg");
        	BufferedImage renderedImage = 
        	 (item.getType() == ItemType.IMAGE) ?        		
        	createImageThumbnail(file, size) :
        		createVideoThumbnail(file, size);
        	ImageIO.write(renderedImage, "jpeg", res.raw().getOutputStream());
        	return true;
        }));
        
        /* Get list of items whose id is larger than the given one. */
        get("item/since/:id", secured((req, res, domain, user) -> {
        	StringBuilder result = new StringBuilder();
        	
			for (Item item : domain.selectItem()
        			.add(Restrictions.gt("id", Long.parseLong(req.params("id"))))
        			.addOrder(Order.asc("id"))
        			.list()) {
				result.append(item.serialise()).append("\n");
			}
        	
        	return result.toString();
        }));
        
        /* Upload a file */
        put("/item/:user/:folder/*", secured((req, res, session, user) -> {
        	
        	/*if (req.bodyAsBytes() == null) {
        		Log.warn("Missing body");
        		res.status(400);
        		return null;
        	}*/
        	
        	String username = req.params("user");
        	if (!user.getName().equals(username)) {
        		Log.error("User "+ user.getName() +" attempted to upload to folder of user "+ username);
        		return forbidden(res, false);
        	}
        	String folder = req.params("folder");
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
        	
        	/* First see if the path already exists. */
        	List<Item> existing = session.selectItem()
        		.add(Restrictions.eq("user", username))
        		.add(Restrictions.eq("folder", folder))
        		.add(Restrictions.eq("path", normalisedPath))
        		.list();
        	
			switch (existing.size()) {
			case 0:
				Item item = new Item();
				item.setFolder(folder);
				item.setPath(normalisedPath);
				item.setUser(username);
				// TODO create shared artifact between backend and frontend to hold this kind of constants
				final String timestampHeader = req.headers("X-CloudSpill-Timestamp");
				if (timestampHeader != null) {
					item.setDate(Instant.ofEpochMilli(Long.valueOf(timestampHeader))
						.atOffset(ZoneOffset.UTC)
						.toLocalDateTime());
				}
				final String typeHeader = req.headers("X-CloudSpill-Type");
				if (typeHeader != null) {
					try {
						item.setType(ItemType.valueOf(typeHeader));
					} catch (IllegalArgumentException e) {
						Log.warn("Received invalid item type "+ typeHeader);
						// Then just leave it blank
					}
				}
				session.persist(item);
				session.flush(); // flush before writing to disk

				requestedTarget.getParentFile().mkdirs();
				// TODO return 40x error in case content-length is missing or invalid
				/*long contentLength = Long.parseLong(req.headers("Content-Length")); */
				Log.debug("Writing bytes to " + requestedTarget);
				try (InputStream in = req.raw().getInputStream();
						FileOutputStream out = new FileOutputStream(requestedTarget)) {
					long copied = ByteStreams.copy(in, out);
					Log.debug("Wrote "+ copied +" bytes to "+ requestedTarget);
/*					if (copied != contentLength) {
						throw new IllegalArgumentException("Expected "+ contentLength +" bytes, got "+ copied);
					}*/
				}
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
    
    private BufferedImage createVideoThumbnail(File file, int size) throws IOException {
    	FFmpegFrameGrabber g = new FFmpegFrameGrabber(file);
    	try {
			g.start();
		
			final BufferedImage frame = new Java2DFrameConverter().convert(g.grabImage());

			return resize(frame.getWidth(), frame.getHeight(), size, (scaledWidth, scaledHeight) -> 
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
		
		return resize(image.getWidth(imageObserver), image.getHeight(imageObserver), size, (scaledWidth, scaledHeight) -> 
			 image.getScaledInstance(scaledWidth, scaledHeight, Image.SCALE_SMOOTH));
	}
	
	private BufferedImage resize(int width, int height, int targetSize, BiFunction<Integer, Integer, Image> resizeFunction) {

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
		ready[0] = renderedImage.createGraphics().drawImage(scaledImage,
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
}
