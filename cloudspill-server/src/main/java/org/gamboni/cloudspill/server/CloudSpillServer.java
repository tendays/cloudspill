/**
 * 
 */
package org.gamboni.cloudspill.server;

import static org.gamboni.cloudspill.util.Files.append;
import static spark.Spark.before;
import static spark.Spark.get;
import static spark.Spark.put;
import static spark.Spark.threadPool;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import javax.imageio.ImageIO;
import javax.inject.Inject;

import org.gamboni.cloudspill.domain.Domain;
import org.gamboni.cloudspill.domain.Item;
import org.gamboni.cloudspill.domain.ItemType;
import org.gamboni.cloudspill.util.Log;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;

import com.google.common.io.ByteStreams;
import com.google.inject.Guice;
import com.google.inject.Injector;

import spark.Spark;

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
    	
    	injector.getInstance(CloudSpillServer.class).run();
    }
    
    private interface TransactionBody<R> {
    	R run(Domain s) throws Exception;
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
    
    public void run() {
    	
    	File rootFolder = configuration.getRepositoryPath();
    	
    	/* Thumbnail construction is memory intensive... */
    	// TODO make this configurable
    	threadPool(4);
    	
    	/* Access logging */
    	before((req, res) -> {
    		Log.info(req.ip() +" "+ req.uri());
    	});
    	
    	/* Used by clients to ensure connectivity is available. In the future this may
    	 * also return a version string to ensure compatibility. */
    	get("/ping", (req, res) ->
    		// WARN: currently the frontend requires precisely this syntax, spaces included
    		"CloudSpill server.\n"
    		+ "Data-Version: "+ DATA_VERSION);
    	
    	/* Just for testing */
        get("/item/:id/path", (req, res) -> transacted(session -> {
        	Item item = session.get(Item.class, Long.parseLong(req.params("id")));
        	return item.getPath();
        }));
        
        /* Download a file */
        get("/item/:id", (req, res) -> transacted(session -> {
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
        get("/thumbs/:size/:id", (req, res) -> transacted(session -> {
        	Item item = session.get(Item.class, Long.parseLong(req.params("id")));
        	int size = Integer.parseInt(req.params("size"));
        	File file = item.getFile(rootFolder);
        	res.header("Content-Type", "image/jpeg");
        	// load an image
        	Image image = ImageIO.read(file);
        	
        	final ImageObserver imageObserver = (Image img, int infoflags, int x, int y, int newWidth, int height) ->
        		((infoflags | ImageObserver.ALLBITS) == ImageObserver.ALLBITS);
        	
			int width = image.getWidth(imageObserver);
			int height = image.getHeight(imageObserver);
			int min = Math.min(width, height);
			
			// Not clear if this happens in real life?
			if (min < 0) { throw new IllegalStateException("Asynchronous image io is not supported"); }
			
        	// Have the *smallest* dimension of the image be the requested 'size'
        	final int scaledWidth = width * size / min;
			final int scaledHeight = height * size / min;
			image = image.getScaledInstance(scaledWidth, scaledHeight, Image.SCALE_SMOOTH);
			
        	// Convert abstract Image into RenderedImage. 
        	BufferedImage renderedImage = new BufferedImage(size, size, BufferedImage.TYPE_3BYTE_BGR);
        	boolean[] ready = new boolean[]{false};
        	ready[0] = renderedImage.createGraphics().drawImage(image,
        			/* Center 'image' on 'renderedImage' (which may be smaller if 'image' is not square) */
        			(size - scaledWidth) / 2,
        			(size - scaledHeight) / 2,
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
        			}
        	);
        	
        	synchronized (ready) {
        		while (!ready[0]) {
        			ready.wait();
        		}
        	}
        	ImageIO.write(renderedImage, "jpeg", res.raw().getOutputStream());
        	return true;
        }));
        
        /* Get list of items whose id is larger than the given one. */
        get("item/since/:id", (req, res) -> transacted(domain -> {
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
        put("/item/:user/:folder/*", (req, res) -> transacted(session -> {
        	
        	/*if (req.bodyAsBytes() == null) {
        		Log.warn("Missing body");
        		res.status(400);
        		return null;
        	}*/
        	
        	String user = req.params("user");
        	String folder = req.params("folder");
			String path = req.splat()[0];
			Log.debug("user is "+ user +", folder is "+ folder +" and path is "+ path);
        	
			// Normalise given path
        	File folderPath = append(append(rootFolder, user), folder);
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
        		.add(Restrictions.eq("user", user))
        		.add(Restrictions.eq("folder", folder))
        		.add(Restrictions.eq("path", normalisedPath))
        		.list();
        	
			switch (existing.size()) {
			case 0:
				Item item = new Item();
				item.setFolder(folder);
				item.setPath(normalisedPath);
				item.setUser(user);
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
    }
