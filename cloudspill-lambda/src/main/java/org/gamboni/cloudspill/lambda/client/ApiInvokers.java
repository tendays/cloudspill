package org.gamboni.cloudspill.lambda.client;

import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;

import org.gamboni.cloudspill.shared.api.CloudSpillApi;
import org.gamboni.cloudspill.shared.api.ItemMetadata;
import org.gamboni.cloudspill.shared.client.ResponseHandler;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.function.Consumer;

/** Standard client implementations of CloudSpillApi methods.
 *
 * @author tendays
 */
public abstract class ApiInvokers {
    public static ResponseHandler upload(ItemMetadata metadata, InputStream input, Consumer<Long> result) {
        return connection -> {
            if (metadata.itemDate != null) {
                connection.setRequestProperty(CloudSpillApi.UPLOAD_TIMESTAMP_HEADER, Long.toString(metadata.itemDate.getTime()));
            }
            if (metadata.itemType != null) {
                connection.setRequestProperty(CloudSpillApi.UPLOAD_TYPE_HEADER, metadata.itemType.name());
            }

            connection.setDoOutput(true);

            ByteStreams.copy(input, connection.getOutputStream());

            final String response = CharStreams.toString(new InputStreamReader(connection.getInputStream()));

            result.accept(Long.parseLong(response));
        };
    }
}
