package de.admir.goverdrive.java.core.error;

import java.util.ArrayList;
import java.util.List;

import lombok.ToString;


@ToString
public abstract class BaseError<E extends BaseError> implements CoreError {
    private String message;
    private List<CoreError> nestedErrors;

    protected BaseError() {
    }

    protected BaseError(String message, List<CoreError> nestedErrors) {
        this.message = message;
        this.nestedErrors = nestedErrors;
    }

    protected BaseError(Throwable e) {
        this.message = e.toString();
    }

    protected BaseError(String message) {
        this.message = message;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public List<CoreError> getNestedErrors() {
        return nestedErrors;
    }

    @Override
    @SuppressWarnings("unchecked")
    public E addNestedError(CoreError error) {
        if (nestedErrors == null)
            nestedErrors = new ArrayList<>();
        nestedErrors.add(error);
        return (E) this;
    }
}
