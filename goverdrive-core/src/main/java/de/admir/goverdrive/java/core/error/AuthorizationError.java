package de.admir.goverdrive.java.core.error;
import java.util.List;

public class AuthorizationError extends BaseError<AuthorizationError> {
    public AuthorizationError(String message, List<CoreError> nestedErrors) {
        super(message, nestedErrors);
    }

    public AuthorizationError(Exception e) {
        super(e);
    }

    public AuthorizationError(String message) {
        super(message);
    }
}
