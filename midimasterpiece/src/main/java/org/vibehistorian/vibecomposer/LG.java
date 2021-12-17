package org.vibehistorian.vibecomposer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LG {
	private static final Logger LOGGER = LoggerFactory.getLogger(VibeComposerGUI.class);

	public static void d(String msg) {
		LOGGER.debug(msg);
	}

	public static void i(String msg) {
		LOGGER.info(msg);
	}

	public static void e(String msg) {
		LOGGER.error(msg);
	}

	public static void w(String msg) {
		LOGGER.warn(msg);
	}
}
