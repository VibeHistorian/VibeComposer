package org.vibehistorian.vibecomposer.Popups;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

public class DebugConsole {
	final JDialog frame = new JDialog();
	JTextArea textArea;
	JScrollPane scroll;
	long lastWrittenNs = 0;
	StringBuilder outCache = new StringBuilder();
	Timer timer = new Timer(1000, new ActionListener() {

		@Override
		public void actionPerformed(ActionEvent e) {
			if (outCache.length() > 0) {
				textArea.append(outCache.toString());
				outCache = new StringBuilder();
				frame.revalidate();
				scroll.getVerticalScrollBar().setValue(scroll.getVerticalScrollBar().getMaximum());
			}
		}

	});

	public DebugConsole() throws Exception {
		textArea = new JTextArea(24, 80);
		textArea.setBackground(Color.BLACK);
		textArea.setForeground(Color.LIGHT_GRAY);
		textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		scroll = new JScrollPane(textArea, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
				JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		frame.add(scroll);
		frame.pack();
		frame.setVisible(true);

	}

	public PrintStream createOutputStream() {
		OutputStream out = new OutputStream() {
			@Override
			public void write(int b) throws IOException {
				outCache.append(String.valueOf((char) b));
				timer.restart();
			}
		};
		PrintStream ps = new PrintStream(out);

		return ps;
	}

	public JDialog getFrame() {
		return frame;
	}
}
