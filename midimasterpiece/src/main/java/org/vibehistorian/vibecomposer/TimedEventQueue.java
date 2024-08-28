package org.vibehistorian.vibecomposer;

import java.awt.AWTEvent;
import java.awt.EventQueue;

public class TimedEventQueue extends EventQueue {
	@Override
	protected void dispatchEvent(AWTEvent event) {
		long startNano = System.nanoTime();
		super.dispatchEvent(event);
		long endNano = System.nanoTime();

		if (endNano - startNano > 5000000) {
			String evtString = event.toString();
			if (true || !evtString.contains("InvocationEvent")) {
				LG.i(((endNano - startNano) / 1000000) + "ms: " + evtString);
			}
		}
	}
}
