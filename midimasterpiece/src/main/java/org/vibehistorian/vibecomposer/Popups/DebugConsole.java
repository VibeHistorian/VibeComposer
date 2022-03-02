package org.vibehistorian.vibecomposer.Popups;

import java.awt.Color;
import java.awt.Font;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import org.vibehistorian.vibecomposer.LG;

public class DebugConsole {
	final JFrame frame = new JFrame();
	JTextArea textArea;
	JScrollPane scroll;

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
				textArea.append(String.valueOf((char) b));
				frame.revalidate();
				scroll.getVerticalScrollBar().setValue(scroll.getVerticalScrollBar().getMaximum());
			}
		};
		PrintStream ps = new PrintStream(out);

		System.setOut(ps);
		System.setErr(ps);

		return ps;
	}

	public JFrame getFrame() {
		return frame;
	}
}
