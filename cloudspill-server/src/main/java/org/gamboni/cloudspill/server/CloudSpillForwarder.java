package org.gamboni.cloudspill.server;

import com.google.inject.Guice;
import com.google.inject.Injector;

import org.gamboni.cloudspill.domain.ForwarderDomain;
import org.gamboni.cloudspill.domain.Item;
import org.gamboni.cloudspill.domain.User;
import org.gamboni.cloudspill.server.config.ForwarderConfiguration;
import org.gamboni.cloudspill.server.config.ServerConfiguration;
import org.gamboni.cloudspill.server.html.HtmlFragment;
import org.gamboni.cloudspill.server.query.ItemSet;
import org.gamboni.cloudspill.server.query.Java8SearchCriteria;
import org.gamboni.cloudspill.shared.util.Log;

import java.io.IOException;

import javax.inject.Inject;
import javax.persistence.EntityManager;

import spark.Request;
import spark.Response;
import spark.Route;

/**
 * @author tendays
 */
public class CloudSpillForwarder extends CloudSpillBackend<ForwarderDomain> {

    @Inject
    ForwarderConfiguration configuration;

    public static void main(String[] args) {
        if (args.length != 1) {
            Log.error("Usage: CloudSpillForwarder configPath");
            System.exit(1);
        }
        Injector injector = Guice.createInjector(new ForwarderModule(args[0]));

        try {
            injector.getInstance(CloudSpillForwarder.class).run();
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }

    private void run() {
        setupRoutes(configuration);
    }

    @Override
    protected Long upload(Request req, Response res, ForwarderDomain session, User user, String folder, String path) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void putTags(ForwarderDomain session, long id, String body) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected Object thumbnail(Response res, ForwarderDomain session, Item item, int size) throws InterruptedException, IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void download(Response res, ForwarderDomain session, Item item) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected String ping() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected ItemSet doSearch(ForwarderDomain session, Java8SearchCriteria<Item> criteria) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected ItemSet loadGallery(ForwarderDomain session, long partId) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected HtmlFragment galleryListPage(ForwarderDomain domain, User user) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected Route securedItem(SecuredItemBody<ForwarderDomain> task) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected ForwarderDomain createDomain(EntityManager e) {
        throw new UnsupportedOperationException();
    }
}
