package de.admir.goverdrive.core.error;

import java.util.List;

public class ApiError extends BaseError<ApiError> {
    public ApiError(String message, List<CoreError> nestedErrors) {
        super(message, nestedErrors);
    }

    public ApiError(Exception e) {
        super(e);
    }

    public ApiError(String message) {
        super(message);
    }
}
