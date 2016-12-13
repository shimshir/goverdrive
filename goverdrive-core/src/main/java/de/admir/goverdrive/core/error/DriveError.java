package de.admir.goverdrive.core.error;

import java.util.List;

public class DriveError extends BaseError<DriveError> {
    private DriveErrorType type = DriveErrorType.UNKNOWN;

    public DriveError(String message, List<CoreError> nestedErrors) {
        super(message, nestedErrors);
    }

    public DriveError(String message, List<CoreError> nestedErrors, DriveErrorType type) {
        super(message, nestedErrors);
        this.type = type;
    }

    public DriveError(Exception e) {
        super(e);
    }

    public DriveError(Exception e, DriveErrorType type) {
        super(e);
        this.type = type;
    }

    public DriveError(String message) {
        super(message);
    }

    public DriveError(String message, DriveErrorType type) {
        super(message);
        this.type = type;
    }

    public DriveErrorType getType() {
        return type;
    }

    public enum DriveErrorType {
        UNKNOWN, NESTED, FOLDER_NOT_FOUND, DUPLICATE_FOLDER, INVALID_PARENT, ILLEGAL_ARGUMENTS
    }
}
