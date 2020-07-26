package org.gamboni.cloudspill.shared.client;

import org.gamboni.cloudspill.shared.api.Base64Encoder;
import org.gamboni.cloudspill.shared.api.ItemCredentials;

import java.io.IOException;
import java.net.HttpURLConnection;

/**
 * @author tendays
 */
public abstract class ResponseHandlers {

    public static ResponseHandler withCredentials(ItemCredentials credentials, Base64Encoder base64Encoder, ResponseHandler continuation) {
        return new ResponseHandler() {
            @Override
            public void handle(HttpURLConnection connection) throws IOException {
                credentials.setHeaders(connection, base64Encoder);
                continuation.handle(connection);
            }
        };
    }
}
