package de.admir.goverdrive.core.error;

import java.util.ArrayList;
import java.util.List;

import lombok.ToString;

@ToString
abstract class BaseError<E extends BaseError> implements CoreError {
    private String message;
    private List<CoreError> nestedErrors;

    BaseError() {
    }

    BaseError(String message, List<CoreError> nestedErrors) {
        this.message = message;
        this.nestedErrors = nestedErrors;
    }

    BaseError(Exception e) {
        this.message = e.toString();
    }

    BaseError(String message) {
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
