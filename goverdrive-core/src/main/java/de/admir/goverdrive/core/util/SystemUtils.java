package de.admir.goverdrive.core.util;

import java.util.Collection;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public class SystemUtils {
    public static final String EMPTY_STRING = "";

    public static <T> T handleFatal(Callable<T> callable, Consumer<Throwable> cleanUp) {
        try {
            return callable.call();
        } catch (Exception e) {
            try {
                cleanUp.accept(e);
            } catch (Exception ee) {
                ee.printStackTrace();
            }
            System.err.println("Fatal error, exiting application");
            System.exit(1);
            // Unnecessary, but the compiler requires it
            return null;
        }
    }

    public static String joinStrings(List<String> strings, String delimiter, String prefix, String suffix) {
        StringJoiner joiner = new StringJoiner(delimiter, prefix, suffix);
        for (String str : strings)
            joiner.add(str);
        return joiner.toString();
    }

    public static String joinStrings(List<String> strings, String delimiter, String prefix) {
        return joinStrings(strings, delimiter, prefix, EMPTY_STRING);
    }

    public static String joinStrings(List<String> strings, String delimiter) {
        return joinStrings(strings, delimiter, EMPTY_STRING, EMPTY_STRING);
    }

    public static boolean isEmptyCollection(Collection<String> collection) {
        return collection == null || collection.size() == 0;
    }
}
