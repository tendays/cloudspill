package org.gamboni.cloudspill.server;

import java.io.File;
import java.util.Base64;
import java.util.List;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.servlet.http.HttpServletResponse;

import org.gamboni.cloudspill.domain.CloudSpillEntityManagerDomain;
import org.gamboni.cloudspill.domain.ServerDomain;
import org.gamboni.cloudspill.domain.User;
import org.gamboni.cloudspill.domain.User_;
import org.gamboni.cloudspill.shared.api.ItemCredentials;
import org.gamboni.cloudspill.shared.util.Log;

import com.google.common.collect.Iterables;

import spark.Request;
import spark.Response;
import spark.Route;

public abstract class AbstractServer<S extends CloudSpillEntityManagerDomain> {

	@Inject
	EntityManagerFactory sessionFactory;

	protected interface TransactionBody<S extends CloudSpillEntityManagerDomain, R> {
	    	R run(S s) throws Exception;
	    }

	protected interface SecuredBody<S extends CloudSpillEntityManagerDomain> {
	    	Object handle(Request request, Response response, S session, ItemCredentials.UserPassword user) throws Exception;
	    }

	protected static final <T> T requireNotNull(T value) {
		if (value == null) {
			throw new NullPointerException();
		} else {
			return value;
		}
	}

	protected Route secured(SecuredBody<S> task) {
		return (req, res) ->
				transacted(session ->
						authenticate(req, session)
								.get(res, user -> task.handle(req, res, session, user)));
	}

	protected OrHttpError<ItemCredentials.UserPassword> authenticate(Request req, S session) {
		return authenticate(req, session, true);
	}
	
	protected OrHttpError<ItemCredentials.UserPassword> optionalAuthenticate(Request req, S session) {
		return authenticate(req, session, false);
	}

	private OrHttpError<ItemCredentials.UserPassword> authenticate(Request req, S session, boolean required) {
		final String authHeader = req.headers("Authorization");
		if (authHeader == null) {
			if (required) {
				return new OrHttpError<>(res -> {
					Log.error("Missing Authorization header");
					return unauthorized(res);
				});
			} else {
				return new OrHttpError<>((ItemCredentials.UserPassword)null);
			}
		} else if (authHeader.startsWith("Basic ")) {
			final String token = authHeader.substring("Basic ".length()).trim();
			final String credentials = new String(Base64.getDecoder().decode(token));
			int colon = credentials.indexOf(':');
			if (colon == -1) {
				return new OrHttpError<>(res -> {
					Log.error("Invalid Authorization header");
					return badRequest(res);
				});
			}
			String username = credentials.substring(0, colon);
			String password = credentials.substring(colon+1);
            final ServerDomain.Query<User> userQuery = session.selectUser();
            final List<User> users = userQuery.add(root ->
                    session.criteriaBuilder.equal(root.get(User_.name), username))
                    .list();
			if (users.isEmpty()) {
				return new OrHttpError<>(res -> {
					Log.error("Unknown user " + username);
					return forbidden(res, true);
				});
			}
			User user = Iterables.getOnlyElement(users);
			user.verifyPassword(password);
			return new OrHttpError<>(new ItemCredentials.UserPassword(user, password));
		} else {
			return new OrHttpError<>(res -> {
				Log.error("Unsupported Authorization scheme");
				return badRequest(res);
			});
		}
	}

	protected String notFound(Response res, long item) {
		Log.error("Not found: item "+ item);
		res.status(HttpServletResponse.SC_NOT_FOUND);
		return "Not Found";
	}

	protected <T> OrHttpError<T> notFound(long item) {
		return new OrHttpError<>(res -> notFound(res, item));
	}

	protected String gone(Response res, long item, File file) {
		Log.error("Gone: "+ file +" for item "+ item);
		res.status(HttpServletResponse.SC_GONE);
		return "Gone";
	}

	private String badRequest(Response res) {
		res.status(HttpServletResponse.SC_BAD_REQUEST);
		return "Bad Request";
	}

	private String unauthorized(Response res) {
		res.status(HttpServletResponse.SC_UNAUTHORIZED);
		loginPrompt(res);
		return "Unauthorized";
	}

	private void loginPrompt(Response res) {
		res.header("WWW-Authenticate", "Basic realm=\"CloudSpill\"");
	}

	protected String internalServerError(Response res) {
		res.status(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		return "Internal Error";
	}

	protected <T> OrHttpError<T> internalServerError() {
		return new OrHttpError<>(res -> internalServerError(res));
	}

	protected String forbidden(Response res, boolean loginPrompt) {
		if (loginPrompt) {
			return unauthorized(res);
			// I was hoping that a 403 with a www-authenticate would prompt the browser to show a login dialog, but it does not (Firefox)
			//loginPrompt(res);
		}
		res.status(HttpServletResponse.SC_FORBIDDEN);
		return "Forbidden";
	}

	protected <T> OrHttpError<T> forbidden(boolean loginPrompt) {
		return new OrHttpError<>(res -> forbidden(res, loginPrompt));
	}

	protected abstract S createDomain(EntityManager e);

	protected <R> R transacted(TransactionBody<S, R> task) throws Exception {
		EntityManager session = null;
		EntityTransaction tx = null;
		try {
			session = sessionFactory.createEntityManager();
			tx = session.getTransaction();
			tx.begin();
			R result = task.run(createDomain(session));
			tx.commit();
			tx = null;
			final String resultString = result.toString();
			Log.debug("Return value: "+ (resultString.length() > 100 ? resultString.substring(0, 100).replace('\n', ' ') +"..." : resultString));
			return result;
		} catch (Throwable t) {
			t.printStackTrace();
			throw t;
		} finally {
			if (tx != null) { tx.rollback(); }
			if (session != null) { session.close(); }
		}
	}

	public AbstractServer() {
		super();
	}

}