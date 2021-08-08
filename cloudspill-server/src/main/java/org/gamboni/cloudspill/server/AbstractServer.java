package org.gamboni.cloudspill.server;

import com.google.common.base.MoreObjects;
import com.google.common.collect.Iterables;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.gamboni.cloudspill.domain.CloudSpillEntityManagerDomain;
import org.gamboni.cloudspill.domain.ServerDomain;
import org.gamboni.cloudspill.domain.User;
import org.gamboni.cloudspill.domain.User_;
import org.gamboni.cloudspill.server.html.AbstractRenderer;
import org.gamboni.cloudspill.server.html.OutputModel;
import org.gamboni.cloudspill.shared.api.ItemCredentials;
import org.gamboni.cloudspill.shared.api.LoginState;
import org.gamboni.cloudspill.shared.domain.ClientUser;
import org.gamboni.cloudspill.shared.domain.InvalidPasswordException;
import org.gamboni.cloudspill.shared.domain.IsUser;
import org.gamboni.cloudspill.shared.util.Log;

import java.io.File;
import java.util.Base64;
import java.util.List;
import java.util.function.Function;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.servlet.FilterConfig;
import javax.servlet.http.HttpServletResponse;

import spark.Request;
import spark.Response;
import spark.Route;
import spark.embeddedserver.EmbeddedServers;
import spark.embeddedserver.jetty.EmbeddedJettyServer;
import spark.embeddedserver.jetty.JettyHandler;
import spark.http.matching.MatcherFilter;
import spark.route.Routes;
import spark.staticfiles.StaticFilesConfiguration;

public abstract class AbstractServer<S extends CloudSpillEntityManagerDomain> {

	protected static final String LOGIN_COOKIE_NAME = "cloudspill-login";
	@Inject
	EntityManagerFactory sessionFactory;

	protected interface TransactionBody<S extends CloudSpillEntityManagerDomain, R> {
	    	R run(S s) throws Exception;
	    }

	protected interface SecuredBody<S extends CloudSpillEntityManagerDomain> {
	    	Object handle(Request request, Response response, S session, ItemCredentials.UserCredentials user) throws Exception;
	    }

	protected static final <T> T requireNotNull(T value) {
		if (value == null) {
			throw new NullPointerException();
		} else {
			return value;
		}
	}

	protected interface QueryExecutor<I, S, O> {
		OrHttpError<O> execute(I input, ItemCredentials credentials, S session);
	}

	public enum ContentType {
		// TODO res.type(mime)
		JSON("application/json; charset=UTF-8"),
		CSV("text/csv; charset=UTF-8");
		public final String mime;

		ContentType(String mime) {
			this.mime = mime;
		}
	}

	protected interface Serialiser<O> {
		String serialise(O object, ContentType ct);
	}

	protected boolean isCsvRequested(Request req) {
		final String acceptHeader = req.headers("Accept");
		return acceptHeader != null && acceptHeader.equals("text/csv");
	}

	protected boolean isJsonRequested(Request req) {
		final String acceptHeader = req.headers("Accept");
		return acceptHeader != null && acceptHeader.equals("application/json");
	}

	/** Proposed common abstraction to use for all pages.
	 *
	 * This decomposition should allow the Forwarder to work automatically (aside from caching), after adding supporting
	 * methods in I/O and/or an additional {@link org.gamboni.cloudspill.shared.api.Csv} parameter
	 *
	 * @param parser extract all request parameters from the request into an "input model". If this stage fails, a 400 error will be returned
	 * @param executor actually process the request. This component has access to the input model, user credentials and database transaction, and returns
	 *                 a "model" in the MVC sense
	 * @param formatter convert the model into an HTML page. This step might be skipped if a CSV/JSON output is requested
	 * @param <I> input model type
	 * @param <O> view model type
	 * @return a Route suitable for passing to a callback-style method in CloudSpillApi
	 */
	protected <I, O extends OutputModel> Route page(Function<Request, I> parser, QueryExecutor<I, S, O> executor, Serialiser<O> serialiser, AbstractRenderer<O> formatter) {

		return (req, res) -> transacted(session -> optionalAuthenticate(req, session)
						.flatMap(credentials ->
		pageBody(parser, executor, serialiser, formatter, req, res, session, MoreObjects.firstNonNull(credentials, new ItemCredentials.PublicAccess())))
						.get(res));
	}

	/** Create a page available to logged-in users only. */
	protected <I, O extends OutputModel> Route securedPage(Function<Request, I> parser, QueryExecutor<I, S, O> executor, Serialiser<O> serialiser, AbstractRenderer<O> formatter) {
		return secured((req, res, session, user) ->
				pageBody(parser, executor, serialiser, formatter, req, res, session, user).get(res));
	}

	private <I, O extends OutputModel> OrHttpError<String> pageBody(Function<Request, I> parser, QueryExecutor<I, S, O> executor, Serialiser<O> serialiser, AbstractRenderer<O> formatter, Request req, Response res, S session, ItemCredentials credentials) {
		I input;
		try {
			input = parser.apply(req);
		} catch (Throwable t) {
			Log.error("Error parsing request", t);
			return badRequest();
		}
		return executor.execute(input, credentials, session)
				.map(model -> {
					if (isCsvRequested(req)) {
						ContentType ct = ContentType.CSV;
						res.type(ct.mime);
						return serialiser.serialise(model, ct);
					} else if (isJsonRequested(req)) {
						ContentType ct = ContentType.JSON;
						res.type(ct.mime);
						return serialiser.serialise(model, ct);
					} else {
						return formatter.render(model).toString();
					}
				});
	}

	/** Require an authenticated user to perform the given task. */
	protected Route secured(SecuredBody<S> task) {
		return (req, res) ->
				transacted(session ->
						authenticate(req, session)
								.get(res, user -> task.handle(req, res, session, user)));
	}

	protected OrHttpError<ItemCredentials.UserCredentials> authenticate(Request req, S session) {
		return authenticate(req, session, true);
	}
	
	protected OrHttpError<ItemCredentials.UserCredentials> optionalAuthenticate(Request req, S session) {
		return authenticate(req, session, false);
	}

	private OrHttpError<ItemCredentials.UserCredentials> authenticate(Request req, S session, boolean required) {
		return getUnverifiedCredentials(req, session).flatMap(credentials -> {
			if (required && credentials == null) {
				return new OrHttpError<>(res -> {
					Log.error("Missing Authorization header");
					return unauthorized(res);
				});
			}
			if (credentials != null) {
				try {
					credentials.match(new ItemCredentials.Matcher<InvalidPasswordException>() {
						@Override
						public void when(ItemCredentials.UserPassword password) throws InvalidPasswordException {
							credentials.user.verifyPassword(password.getPassword());
						}

						@Override
						public void when(ItemCredentials.UserToken token) throws InvalidPasswordException {
							final LoginState state = getUserTokenState(token.user, token.id, token.secret);
							if (state != LoginState.LOGGED_IN) {
								throw new InvalidPasswordException("User token #"+ token.id +" has not been validated");
							}
						}

						@Override
						public void when(ItemCredentials.PublicAccess pub) throws InvalidPasswordException {
							/* Check later when we know which Item is being accessed (if any) */
						}

						@Override
						public void when(ItemCredentials.ItemKey key) throws InvalidPasswordException {
							/* Check later when we know which Item is being accessed (if any) */
						}
					});
				} catch (InvalidPasswordException e) {
					return forbidden(false);
				}
			}
			return new OrHttpError<>(credentials);

		});
	}

	protected abstract LoginState getUserTokenState(IsUser user, long id, String secret);

	protected OrHttpError<ItemCredentials.UserCredentials> getUnverifiedCredentials(Request req, S session) {
		final String authHeader = req.headers("Authorization");
		if (authHeader == null || authHeader.startsWith(ItemCredentials.UserToken.AUTH_TYPE +" ")) {
			final String cookie = req.cookie(LOGIN_COOKIE_NAME);
			final String authString = (authHeader == null) ? null : authHeader.substring(ItemCredentials.UserToken.AUTH_TYPE.length() + 1);
			if (cookie == null && authString == null) {
				return new OrHttpError<>((ItemCredentials.UserCredentials) null);
			} else {
				final ItemCredentials.UserToken clientToken = ItemCredentials.UserToken.decodeCookie(
						MoreObjects.firstNonNull(cookie, authString)
				);
				String username = clientToken.user.getName();
				return new OrHttpError<>(
						getUser(username, session)
								.map(u -> new ItemCredentials.UserToken(u, clientToken.id, clientToken.secret))
								.orElse(() -> clientToken));
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
			return new OrHttpError<>(
					getUser(username, session)
							.map(u -> new ItemCredentials.UserPassword(u, password))
							.orElse(() ->
									new ItemCredentials.UserPassword(new ClientUser(username), password)));
		} else {
			return new OrHttpError<>(res -> {
				Log.error("Unsupported Authorization scheme");
				return badRequest(res);
			});
		}
	}

	protected abstract OrHttpError<User> getUser(String username, CloudSpillEntityManagerDomain session);

	protected OrHttpError<User> getUserFromDB(String username, CloudSpillEntityManagerDomain session) {
		final ServerDomain.Query<User> userQuery = session.selectUser();
		final List<User> users = userQuery.add(root ->
				session.criteriaBuilder.equal(root.get(User_.name), username))
				.list();
		if (users.isEmpty()) {
			return new OrHttpError<>(res -> {
				Log.error("Unknown user " + username);
				return forbidden(res, true);
			});
		} else {
			return new OrHttpError<>(Iterables.getOnlyElement(users));
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

	protected String badRequest(Response res) {
		res.status(HttpServletResponse.SC_BAD_REQUEST);
		return "Bad Request";
	}

	protected <T> OrHttpError<T> badRequest() {
		return new OrHttpError<>(this::badRequest);
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
			/* Routes may return null in case of non-2xx HTTP status code */
			final String resultString = String.valueOf(result);
			Log.debug("Transaction outcome: "+ (resultString.length() > 100 ? resultString.substring(0, 100).replace('\n', ' ') +"..." : resultString));
			return result;
		} catch (Throwable t) {
			t.printStackTrace();
			throw t;
		} finally {
			if (tx != null) { tx.rollback(); }
			if (session != null) { session.close(); }
		}
	}

	protected <R> OrHttpError<R> transactedOrError(TransactionBody<S, R> task) {
		try {
			return new OrHttpError<>(transacted(task));
		} catch (Throwable t) {
			/* NOTE: exception already logged by transacted() itself */
			return internalServerError();
		}
	}

	protected <R, E extends Throwable> OrHttpError<R> transactedOrError(TransactionBody<S, R> task, Class<E> allowed) throws E {
		try {
			return new OrHttpError<>(transacted(task));
		} catch (Throwable t) {
			if (allowed.isInstance(t)) {
				throw (E)t;
			} else {
				/* NOTE: exception already logged by transacted() itself */
				return internalServerError();
			}
		}
	}

	protected AbstractServer() {
		/* Experimental: starting work on setting up Jetty server. Ultimate purpose is to disable getRemoteAddr() checking X-Forwarded-For.
		EmbeddedServers.add(EmbeddedServers.Identifiers.JETTY,
				(Routes routeMatcher, StaticFilesConfiguration staticFilesConfiguration, boolean hasMultipleHandler) -> {
			MatcherFilter matcherFilter = new MatcherFilter(routeMatcher, staticFilesConfiguration, false, hasMultipleHandler);
			matcherFilter.init(null);
			JettyHandler handler = new JettyHandler(matcherFilter);
			return new EmbeddedJettyServer((int maxThreads, int minThreads, int threadTimeoutMillis) -> {
						Server server;
						if (maxThreads > 0 || minThreads > 0 || threadTimeoutMillis > 0) {
							int max = maxThreads > 0 ? maxThreads : 200;
							int min = minThreads > 0 ? minThreads : 8;
							int idleTimeout = threadTimeoutMillis > 0 ? threadTimeoutMillis : '\uea60';
							server = new Server(new QueuedThreadPool(max, min, idleTimeout));
						} else {
							server = new Server();
						}

						return server;
					}, handler);
		}); */
	}
}