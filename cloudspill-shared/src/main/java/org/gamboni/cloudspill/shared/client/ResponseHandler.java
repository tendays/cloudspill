package org.gamboni.cloudspill.shared.client;

import org.gamboni.cloudspill.shared.api.Base64Encoder;
import org.gamboni.cloudspill.shared.api.ItemCredentials;

import java.io.IOException;
import java.net.HttpURLConnection;

/**
 * @author tendays
 */
public interface ResponseHandler {
    void handle(HttpURLConnection connection) throws IOException;
}
