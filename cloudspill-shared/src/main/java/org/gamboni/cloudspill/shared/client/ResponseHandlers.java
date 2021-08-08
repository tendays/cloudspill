package org.gamboni.cloudspill.shared.client;

import org.gamboni.cloudspill.shared.api.Base64Encoder;
import org.gamboni.cloudspill.shared.api.ItemCredentials;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.List;

/**
 * @author tendays
 */
public abstract class ResponseHandlers {

    // TODO CloudSpillApi "view" methods should just support returning a value...
    public static class ResponseHandlerWithResult<T> implements ResponseHandler {
        private final SupplyingResponseHandler<T> delegate;
        private T result = null;

        public ResponseHandlerWithResult(SupplyingResponseHandler<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void handle(HttpURLConnection connection) throws IOException {
            this.result = this.delegate.handle(connection);
        }

        public T getResult() {
            return this.result;
        }
    }

    public interface SupplyingResponseHandler<T> {
        T handle(HttpURLConnection connection) throws IOException;
    }
    public static ResponseHandler withCredentials(ItemCredentials credentials, Base64Encoder base64Encoder, ResponseHandler continuation) {
        return withCredentials(Collections.singletonList(credentials), base64Encoder, continuation);
    }

    public static ResponseHandler withCredentials(List<ItemCredentials> credentials, Base64Encoder base64Encoder, ResponseHandler continuation) {
        return new ResponseHandler() {
            @Override
            public void handle(HttpURLConnection connection) throws IOException {
                for (ItemCredentials c : credentials) {
                    c.setHeaders(connection, base64Encoder);
                }
                continuation.handle(connection);
            }
        };
    }
}
