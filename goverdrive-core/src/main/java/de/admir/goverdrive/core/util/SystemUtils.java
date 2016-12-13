package de.admir.goverdrive.core.util;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

public class SystemUtils {

	public static <T> T handleFatal(Callable<T> callable, Consumer<Throwable> cleanUp) {
		try {
			return callable.call();
		} catch (Exception e) {
			try {
				cleanUp.accept(e);
			} catch (Exception ee) {
				ee.printStackTrace();
			}
			System.exit(1);
			// Unnecessary, but the compiler requires it
			return null;
		}
	}
}
