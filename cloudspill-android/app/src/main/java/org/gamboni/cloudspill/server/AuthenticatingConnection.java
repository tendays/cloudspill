package org.gamboni.cloudspill.server;

import android.content.Context;
import android.util.Base64;

import org.gamboni.cloudspill.ui.SettingsActivity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/** {@link HttpURLConnection} wrapper injecting authentication headers.
 *
 * @author tendays
 */
public class AuthenticatingConnection {
    private final Context context;
    private final String url;
    private final RequestMethod method;
    private final Map<RequestHeader, String> headers = new HashMap<>();
    private Integer chunkedStreamingMode = null;
    private boolean doOutput = false;

    public interface Connected {
        InputStream getInput() throws IOException;
        OutputStream getOutput() throws IOException;
    }

    public interface Session {
        void run(Connected connected) throws IOException;
    }

    public enum RequestMethod { GET, POST, PUT }

    public enum RequestHeader {
        TIMESTAMP("X-CloudSpill-Timestamp"),
        TYPE("X-CloudSpill-Type");

        private final String httpHeader;

        private RequestHeader(String httpHeader) {
            this.httpHeader = httpHeader;
        }
    }

    public AuthenticatingConnection(Context context, RequestMethod method, String url) {
        this.context = context;
        this.url = url;
        this.method = method;
    }

    public AuthenticatingConnection setHeader(RequestHeader header, String value) {
        this.headers.put(header, value);
        return this;
    }

    public AuthenticatingConnection setChunkedStreamingMode(int chunklen) {
        this.chunkedStreamingMode = chunklen;
        return this;
    }

    public AuthenticatingConnection setDoOutput() {
        this.doOutput = true;
        return this;
    }

    public void connect(Session session) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod(this.method.name());
            // include authentication header
            final String credentials = SettingsActivity.getUser(context) + ":" + SettingsActivity.getPassword(context);
            connection.setRequestProperty("Authorization", "Basic "+ Base64.encodeToString(credentials.getBytes(), Base64.NO_WRAP));
            for (Map.Entry<RequestHeader, String> entry : this.headers.entrySet()) {
                connection.setRequestProperty(entry.getKey().httpHeader, entry.getValue());
            }
            if (this.chunkedStreamingMode != null) {
                connection.setChunkedStreamingMode(chunkedStreamingMode);
            }
            if (this.doOutput) {
                connection.setDoOutput(true);
            }

            final HttpURLConnection finalConnection = connection;
            session.run(new Connected() {
                @Override
                public InputStream getInput() throws IOException {
                    return finalConnection.getInputStream();
                }

                @Override
                public OutputStream getOutput() throws IOException {
                    return finalConnection.getOutputStream();
                }
            });
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
