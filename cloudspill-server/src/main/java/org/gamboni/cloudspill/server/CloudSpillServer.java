/**
 * 
 */
package org.gamboni.cloudspill.server;

import static spark.Spark.get;
import static spark.Spark.put;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

import javax.inject.Inject;

import org.gamboni.cloudspill.domain.Domain;
import org.gamboni.cloudspill.domain.Item;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;

import com.google.inject.Guice;
import com.google.inject.Injector;

/**
 * @author tendays
 *
 */
public class CloudSpillServer {
	
	@Inject SessionFactory sessionFactory;
	
	File rootFolder = new File("/tmp/repository");
	
	String user = "tendays"; // TODO authentication
	
    public static void main(String[] args) {
    	
    	Injector injector = Guice.createInjector(new ServerModule());
    	
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
    	/* Just for testing */
        get("/item/:id/path", (req, res) -> transacted(session -> {
        	Item item = session.get(Item.class, Long.parseLong(req.params("id")));
        	return item.getPath();
        }));
        
        /* Download a file */
        get("/item/:id", (req, res) -> true);
        
        /* Get list of items whose id is larger than the given one. */
        get("item/since/:id", (req, res) -> transacted(domain -> {
        	StringBuilder result = new StringBuilder();
        	
			for (Item item : domain.selectItem()
        			.add(Restrictions.gt("id", Long.parseLong(req.params("id"))))
        			.addOrder(Order.asc("id"))
        			.list()) {
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
        put("/item/:folder/*", (req, res) -> transacted(session -> {
        	String folder = req.params("folder");
			String path = req.splat()[0];
			System.out.println("folder is "+ folder +" and path is "+ path);
        	
			// Normalise given path
        	File requestedTarget = new File(new File(rootFolder, folder), path).getCanonicalFile();
        	
        	if (!requestedTarget.getPath().startsWith(rootFolder.getCanonicalPath())) {
        		res.status(400);
        		return null;
        	}
        	
        	// Path to put into the database
        	String normalisedPath = requestedTarget.getPath().substring(rootFolder.getCanonicalPath().length());
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

				System.out.println("Writing " + req.bodyAsBytes().length + " bytes to " + requestedTarget);
				try (FileOutputStream out = new FileOutputStream(requestedTarget)) {
					out.write(req.bodyAsBytes());
				}

				return item.getId();

			case 1:
				return existing.get(0).getId();

			default:
				res.status(500);
				System.out.println("Collision detected: " + existing);
				return null;
			}
        }));
        
        /* get all files more recent than the given value. */
        get("/item/since/:ts", (req, res) -> true);
    }
}
