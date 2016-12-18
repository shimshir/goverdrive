package de.admir.goverdrive.java.core.error;

import java.util.List;

import lombok.ToString;


@ToString(callSuper = true)
public class IOError extends BaseError<IOError> {
    public IOError(String message, List<CoreError> nestedErrors) {
        super(message, nestedErrors);
    }

    public IOError(Throwable e) {
        super(e);
    }

    public IOError(String message) {
        super(message);
    }
}
