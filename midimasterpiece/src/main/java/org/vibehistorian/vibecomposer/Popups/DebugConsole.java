package org.vibehistorian.vibecomposer.Popups;

import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.Timer;

import org.vibehistorian.vibecomposer.LG;

public class DebugConsole {
	final JFrame frame = new JFrame();
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

		redirectOut();

		LG.d("Started debug console..");
	}

	public PrintStream redirectOut() {
		OutputStream out = new OutputStream() {
			@Override
			public void write(int b) throws IOException {
				outCache.append(String.valueOf((char) b));
				timer.restart();
			}
		};
		PrintStream ps = new PrintStream(out);

		//System.setOut(ps);
		System.setErr(ps);

		return ps;
	}

	public JFrame getFrame() {
		return frame;
	}
}
