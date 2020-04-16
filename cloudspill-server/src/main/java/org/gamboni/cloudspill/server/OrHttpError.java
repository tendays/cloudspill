package org.gamboni.cloudspill.server;

import com.google.common.base.Preconditions;

import spark.Response;

/**
 * @author tendays
 */
public class OrHttpError<T> {
    private final T item;
    private final HttpError error;

    public OrHttpError(T item) {
        this.item = item;
        this.error = null;
    }

    public OrHttpError(HttpError error) {
        this.item = null;
        this.error = Preconditions.checkNotNull(error);
    }

    public Object get(Response res, ItemConsumer<T> onItem) throws Exception {
        if (error != null) {
            return error.emit(res);
        } else {
            return onItem.accept(item);
        }
    }


    public interface HttpError {
        String emit(Response res);
    }

    public interface ItemConsumer<T> {
        Object accept(T item) throws Exception;
    }
}
