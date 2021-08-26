package org.vibehistorian.vibecomposer.Popups;

import java.awt.Color;
import java.awt.Font;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

public class AboutPopup extends CloseablePopup {
	JTextArea textArea;
	JScrollPane scroll;

	public AboutPopup() {
		super("About", 1);
		textArea = new JTextArea(24, 80);
		textArea.setBackground(Color.BLACK);
		textArea.setForeground(Color.LIGHT_GRAY);
		textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		scroll = new JScrollPane(textArea, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
				JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
		textArea.append("Copyright \u00a9 Vibe Historian 2021");
		textArea.append("" + "\r\n"
				+ "This program is free software; you can redistribute it and/or modify\r\n"
				+ "it under the terms of the GNU General Public License as published by\r\n"
				+ "the Free Software Foundation; either version 2 of the License, or any\r\n"
				+ "later version.\r\n" + "\r\n"
				+ "This program is distributed in the hope that it will be useful, but\r\n"
				+ "WITHOUT ANY WARRANTY; without even the implied warranty of\r\n"
				+ "MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the\r\n"
				+ "GNU General Public License for more details.\r\n" + "\r\n"
				+ "You should have received a copy of the GNU General Public License\r\n"
				+ "along with this program; if not, write to the Free Software\r\n"
				+ "Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.");
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		frame.add(scroll);
		frame.pack();
		//frame.setVisible(true);

		System.out.println("Opened About page!");
	}

	public JFrame getFrame() {
		return frame;
	}

	@Override
	protected void addFrameWindowOperation() {
		// do nothing

	}
}
