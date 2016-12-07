package de.admir.goverdrive.core.error;

import java.util.List;

public interface CoreError {

    String getMessage();

    List<CoreError> getNestedErrors();

    CoreError addNestedError(CoreError error);
}
