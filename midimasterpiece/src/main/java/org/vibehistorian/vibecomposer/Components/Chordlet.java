package org.vibehistorian.vibecomposer.Components;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

import javax.swing.JComponent;

import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.OMNI;
import org.vibehistorian.vibecomposer.SwingUtils;

public class Chordlet extends JComponent {

	private static final long serialVersionUID = 6300044827795197600L;
	private String firstLetter = "C";
	private String spice = "";
	private boolean sharp = false;
	private Integer inversion = null;
	private int width = 30;
	private int height = 15;
	private static final Font font = new Font("Tahoma", Font.PLAIN, 12);

	public Chordlet(String chord) {
		setupChord(chord);
	}

	private void setupChord(String chord) {
		if (chord == null || chord.length() == 0) {
			return;
		}
		width = SwingUtils.getDrawStringWidth(chord) + 7;
		firstLetter = chord.substring(0, 1).toUpperCase();
		sharp = chord.contains("#");
		inversion = chord.contains(".") ? Integer.valueOf(chord.split(".")[1]) : null;

		int spiceStartIndex = sharp ? 2 : 1;
		int spiceEndIndex = inversion != null ? chord.indexOf(".") : chord.length();
		if (spiceStartIndex < spiceEndIndex) {
			spice = chord.substring(spiceStartIndex, spiceEndIndex);
		}

		setPreferredSize(new Dimension(width, height));
		setMinimumSize(new Dimension(width, height));

		addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				// start drag - mouse Y pos
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				// stop drag - reset
			}

			@Override
			public void mouseDragged(MouseEvent e) {
				// based on diff in Y pos - get index "", "m", "m7".., get index+1 % spice size 
			}
		});

		addMouseWheelListener(new MouseWheelListener() {

			@Override
			public void mouseWheelMoved(MouseWheelEvent e) {
				// TODO cycle first letters - get index in C,C#,D.. list, get index+1 % 12

			}
		});
	}

	@Override
	public void paintComponent(Graphics guh) {
		if (guh == null) {
			return;
		}
		Graphics2D g = (Graphics2D) guh;
		setFont(font);
		//super.paintComponent(g);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		//g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		//g.setColor(color);
		int minX = 0;
		int maxX = this.getSize().width;
		int height = this.getSize().height;

		String chordText = getChordText();
		Color color = OMNI.mixColor(getColorForChord(chordText), Color.black, 0.1);
		Color color2 = OMNI.mixColor(color, Color.black, 0.25);
		GradientPaint gp = new GradientPaint(minX, 0, color, maxX, 0, color2);
		g.setPaint(gp);

		g.fillRoundRect(minX, 0, maxX, height, 10, 10);
		//g.fillRect(minX, 0, maxX, height);
		g.setColor(Color.black);
		g.drawString(chordText, minX + 4, height - 3);
		g.drawRoundRect(minX, 0, maxX, height, 10, 10);

		//g.dispose();
	}

	public Color getColor() {
		return getColorForChord(getChordText(), sharp);
	}

	public static Color getColorForChord(String chord, boolean isSharp) {
		int[] mapped = MidiUtils.mappedChord(chord);
		double chordKeyness = MidiUtils.getChordKeyness(mapped);
		int chordIndex = MidiUtils.CHORD_FIRST_LETTERS.indexOf(chord.substring(0, 1)) - 1;
		Color chordColor = MidiUtils.CHORD_COLORS.get(chordIndex);
		if (isSharp) {
			Color nextChordColor = MidiUtils.CHORD_COLORS.get(chordIndex + 1 % 7);
			chordColor = OMNI.mixColor(chordColor, nextChordColor, 0.5);
		}
		chordColor = OMNI.alphen(chordColor, Math.min((int) (chordKeyness * 255), 255));
		return chordColor;
	}

	public static Color getColorForChord(String chord) {
		int[] mapped = MidiUtils.mappedChord(chord);
		double chordKeyness = MidiUtils.getChordKeyness(mapped);
		int chordIndex = MidiUtils.CHORD_FIRST_LETTERS.indexOf(chord.substring(0, 1)) - 1;
		Color chordColor = MidiUtils.CHORD_COLORS.get(chordIndex);
		if (chord.contains("#")) {
			Color nextChordColor = MidiUtils.CHORD_COLORS.get(chordIndex + 1 % 7);
			chordColor = OMNI.mixColor(chordColor, nextChordColor, 0.5);
		}
		chordColor = OMNI.alphen(chordColor, Math.min((int) (chordKeyness * 255), 255));
		return chordColor;
	}

	public String getChordText() {
		return firstLetter + (sharp ? "#" : "") + spice
				+ (inversion != null ? ("." + inversion) : "");
	}

}
