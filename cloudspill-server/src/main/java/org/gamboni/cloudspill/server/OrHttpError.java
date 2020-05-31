package org.gamboni.cloudspill.server;

import com.google.common.base.Preconditions;

import java.util.function.Function;

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

    public Object get(Response res) throws Exception {
        return this.get(res, value -> value);
    }

    @SuppressWarnings("unchecked") // changing type parameter is safe on error because item is null in that case
    public <U> OrHttpError<U> flatMap(Function<T, OrHttpError<U>> function) {
        if (error != null) {
            return (OrHttpError<U>)this;
        } else {
            return function.apply(item);
        }
    }

    @SuppressWarnings("unchecked") // changing type parameter is safe on error because item is null in that case
    public <U> OrHttpError<U> map(Function<T, U> function) {
        if (error != null) {
            return (OrHttpError<U>)this;
        } else {
            return new OrHttpError<>(function.apply(item));
        }
    }

    public interface HttpError {
        String emit(Response res);
    }

    public interface ItemConsumer<T> {
        Object accept(T item) throws Exception;
    }
}
