package org.gamboni.cloudspill.server;

import java.io.File;
import java.util.Base64;
import java.util.List;

import javax.inject.Inject;
import javax.servlet.http.HttpServletResponse;

import org.gamboni.cloudspill.domain.Domain;
import org.gamboni.cloudspill.domain.User;
import org.gamboni.cloudspill.shared.util.Log;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.criterion.Restrictions;
import org.mindrot.jbcrypt.BCrypt;

import com.google.common.collect.Iterables;

import spark.Request;
import spark.Response;
import spark.Route;

public class AbstractServer {

	@Inject
	SessionFactory sessionFactory;

	protected interface TransactionBody<R> {
	    	R run(Domain s) throws Exception;
	    }

	protected interface SecuredBody {
	    	Object handle(Request request, Response response, Domain session, User user) throws Exception;
	    }

	protected static final <T> T requireNotNull(T value) {
		if (value == null) {
			throw new NullPointerException();
		} else {
			return value;
		}
	}

	protected Route secured(SecuredBody task) {
		return (req, res) -> transacted(session -> {
			final User user = authenticate(req, res, session);
			if (user == null) {
				return String.valueOf(res.status());
			} else {
				return task.handle(req, res, session, user);
			}
		});
	}

	protected User authenticate(Request req, Response res, Domain session) {
		return authenticate(req, res, session, true);
	}
	
	protected User optionalAuthenticate(Request req, Response res, Domain session) {
		return authenticate(req, res, session, false);
	}

	private User authenticate(Request req, Response res, Domain session, boolean required) {
		final String authHeader = req.headers("Authorization");
		final User user;
		if (authHeader == null) {
			if (required) {
				Log.error("Missing Authorization header");
				unauthorized(res);
			}
			return null;
			// Other option: user = null; // anonymous
		} else if (authHeader.startsWith("Basic ")) {
			final String token = authHeader.substring("Basic ".length()).trim();
			final String credentials = new String(Base64.getDecoder().decode(token));
			int colon = credentials.indexOf(':');
			if (colon == -1) {
				Log.error("Invalid Authorization header");
				badRequest(res);
				return null;
			}
			String username = credentials.substring(0, colon);
			String password = credentials.substring(colon+1);
			final List<User> users = session.selectUser().add(Restrictions.eq("name", username)).list();
			if (users.isEmpty()) {
				Log.error("Unknown user "+ username);
				forbidden(res, true);
				return null;
			}
			user = Iterables.getOnlyElement(users);
			final String queryHash = BCrypt.hashpw(password, user.getSalt());
			if (!queryHash.equals(user.getPass())) {
				Log.error("Invalid credentials for user "+ username);
				forbidden(res, true);
				return null;
			} else {
				Log.info("User "+ username +" authenticated");
			}
		} else {
			Log.error("Unsupported Authorization scheme");
			badRequest(res);
			return null;
		}
		return user;
	}

	protected Object notFound(Response res, long item) {
		Log.error("Not found: item "+ item);
		res.status(HttpServletResponse.SC_NOT_FOUND);
		return "Not Found";
	}

	protected Object gone(Response res, long item, File file) {
		Log.error("Gone: "+ file +" for item "+ item);
		res.status(HttpServletResponse.SC_GONE);
		return "Gone";
	}

	private Object badRequest(Response res) {
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

	protected String forbidden(Response res, boolean loginPrompt) {
		if (loginPrompt) {
			return unauthorized(res);
			// I was hoping that a 403 with a www-authenticate would prompt the browser to show a login dialog, but it does not (Firefox)
			//loginPrompt(res);
		}
		res.status(HttpServletResponse.SC_FORBIDDEN);
		return "Forbidden";
	}

	protected <R> R transacted(TransactionBody<R> task) throws Exception {
		Session session = null;
		Transaction tx = null;
		try {
			session = sessionFactory.openSession();
			tx = session.beginTransaction();
			R result = task.run(new Domain(session));
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