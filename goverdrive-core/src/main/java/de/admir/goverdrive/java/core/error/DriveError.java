package de.admir.goverdrive.java.core.error;

import java.util.List;

import lombok.ToString;


@ToString(callSuper = true)
public class DriveError extends BaseError<DriveError> {
    private DriveErrorType type = DriveErrorType.UNKNOWN;

    public DriveError(String message, List<CoreError> nestedErrors) {
        super(message, nestedErrors);
    }

    public DriveError(String message, List<CoreError> nestedErrors, DriveErrorType type) {
        super(message, nestedErrors);
        this.type = type;
    }

    public DriveError(Throwable e) {
        super(e);
    }

    public DriveError(Throwable e, DriveErrorType type) {
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
        UNKNOWN, NESTED, FOLDER_NOT_FOUND, DUPLICATE_FOLDER, DUPLICATE_FILE, INVALID_PARENT, ILLEGAL_ARGUMENTS
    }
}
