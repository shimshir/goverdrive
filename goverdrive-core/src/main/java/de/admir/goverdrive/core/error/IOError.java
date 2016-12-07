package de.admir.goverdrive.core.error;

import java.util.List;

public class IOError extends BaseError<IOError> {
    public IOError(String message, List<CoreError> nestedErrors) {
        super(message, nestedErrors);
    }

    public IOError(Exception e) {
        super(e);
    }

    public IOError(String message) {
        super(message);
    }
}
