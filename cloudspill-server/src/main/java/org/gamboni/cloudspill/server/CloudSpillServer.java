/**
 * 
 */
package org.gamboni.cloudspill.server;

import static org.gamboni.cloudspill.util.Files.append;
import static spark.Spark.get;
import static spark.Spark.put;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;
import javax.transaction.SystemException;

import org.gamboni.cloudspill.domain.Domain;
import org.gamboni.cloudspill.domain.Item;
import org.gamboni.cloudspill.util.Log;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;

import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import com.google.inject.Guice;
import com.google.inject.Injector;

/**
 * @author tendays
 *
 */
public class CloudSpillServer {
	
	@Inject SessionFactory sessionFactory;
	@Inject ServerConfiguration configuration;
	
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
    	
    	/* Used by clients to ensure connectivity is available. In the future this may
    	 * also return a version string to ensure compatibility. */
    	get("/ping", (req, res) ->
    	/*{	System.out.println("Received ping"); return */
    	"pong"
    	/*;}*/);
    	
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
        
        /* Get list of items whose id is larger than the given one. */
        get("item/since/:id", (req, res) -> transacted(domain -> {
        	StringBuilder result = new StringBuilder();
        	
			for (Item item : domain.selectItem()
        			.add(Restrictions.gt("id", Long.parseLong(req.params("id"))))
        			.addOrder(Order.asc("id"))
        			.list()) {
				// TODO quote or escape
				result.append(item.getId())
				.append(";")
				.append(item.getUser())
				.append(";")
				.append(item.getFolder())
				.append(";")
				.append(item.getPath())
				.append("\n");
			}
        	
        	return result.toString();
        }));
        
        /* Upload a file */
        put("/item/:user/:folder/*", (req, res) -> transacted(session -> {
        	if (req.bodyAsBytes() == null) {
        		Log.warn("Missing body");
        		res.status(400);
        		return null;
        	}
        	
        	String user = req.params("user");
        	String folder = req.params("folder");
			String path = req.splat()[0];
			System.out.println("user is "+ user +", folder is "+ folder +" and path is "+ path);
        	
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
				session.persist(item);
				session.flush(); // flush before writing to disk

				requestedTarget.getParentFile().mkdirs();

				Log.debug("Writing " + req.bodyAsBytes().length + " bytes to " + requestedTarget);
				try (FileOutputStream out = new FileOutputStream(requestedTarget)) {
					out.write(req.bodyAsBytes());
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
