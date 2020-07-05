package org.gamboni.cloudspill.server;

import com.google.common.base.Preconditions;

import java.util.function.Function;
import java.util.function.Supplier;

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

    /** If this is an error, publish it to the given Response object. Otherwise (this holds a value), transform it
     * using the given {@link ItemConsumer}.
     * @param res where to publish any error
     * @param onValue transformer if this has a value
     * @return either an error string, or whatever {@code onValue} returned
     * @throws Exception if thrown by {@code onValue}
     */
    public Object get(Response res, ItemConsumer<T> onValue) throws Exception {
        if (error != null) {
            return error.emit(res);
        } else {
            return onValue.accept(item);
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

    public T orElse(Supplier<T> supplier) {
        if (error == null) {
            return this.item;
        } else {
            return supplier.get();
        }
    }

    public interface HttpError {
        String emit(Response res);
    }

    public interface ItemConsumer<T> {
        Object accept(T item) throws Exception;
    }

    public boolean hasValue() {
        return (error == null);
    }
}
