/**
 * 
 */
package org.gamboni.cloudspill.server;

import static spark.Spark.get;

import javax.inject.Inject;

import org.gamboni.cloudspill.domain.Item;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import com.google.inject.Guice;
import com.google.inject.Injector;

/**
 * @author tendays
 *
 */
public class CloudSpillServer {
	
	@Inject SessionFactory sessionFactory;
	
    public static void main(String[] args) {
    	
    	Injector injector = Guice.createInjector(new ServerModule());
    	
    	injector.getInstance(CloudSpillServer.class).run();
    }
    
    public void run() {
        get("/item/:id", (req, res) -> {
        	try {
        		Session session = sessionFactory.openSession();
        		session.beginTransaction();
        		Item item = (Item) session.get(Item.class, Long.parseLong(req.params("id")));
        		session.close();
        		return item.getPath();
        	} catch (Throwable t) {
        		t.printStackTrace();
        		throw t;
        	}
        });
    }
}
