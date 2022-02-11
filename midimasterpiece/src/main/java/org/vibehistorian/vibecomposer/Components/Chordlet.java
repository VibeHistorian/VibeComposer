package org.vibehistorian.vibecomposer.Components;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JComponent;

import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.OMNI;

public class Chordlet extends JComponent {

	private static final long serialVersionUID = 6300044827795197600L;
	private String chordText = "C";
	private int width = 30;
	private int height = 30;

	public Chordlet(String chord) {
		chordText = chord;
		setupChord(chordText);
	}

	private void setupChord(String chord) {
		width = chord.length() * 10;
		setPreferredSize(new Dimension(width, height));
		setMinimumSize(new Dimension(width, height));
	}

	@Override
	public void paintComponent(Graphics guh) {
		if (guh == null) {
			return;
		}
		Graphics2D g = (Graphics2D) guh;
		super.paintComponent(g);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

		Color color = OMNI.mixColor(getColorForChord(chordText), Color.black, 0.1);

		//g.setColor(color);
		int minX = 0;
		int maxX = this.getSize().width;

		int height = this.getSize().height;
		Color color2 = OMNI.mixColor(color, Color.black, 0.25);
		GradientPaint gp = new GradientPaint(minX, 0, color, maxX, 0, color2);
		g.setPaint(gp);

		g.fillRect(minX, 0, maxX, height);
		g.setColor(Color.black);
		g.drawString(chordText, minX + 1, height / 2);
		g.drawRect(minX, 0, maxX, height);

		//g.dispose();
	}

	private Color getColorForChord(String chord) {
		int[] mapped = MidiUtils.mappedChord(chord);
		double chordKeyness = MidiUtils.getChordKeyness(mapped);
		int chordIndex = MidiUtils.CHORD_FIRST_LETTERS.indexOf(chord.substring(0, 1)) - 1;
		Color chordColor = MidiUtils.CHORD_COLORS.get(chordIndex);
		if (chord.length() > 1 && chord.substring(1, 2).equals("#")) {
			Color nextChordColor = MidiUtils.CHORD_COLORS.get(chordIndex + 1 % 7);
			chordColor = OMNI.mixColor(chordColor, nextChordColor, 0.5);
		}
		chordColor = OMNI.alphen(chordColor, Math.min((int) (chordKeyness * 255), 255));
		LG.d("returned color: " + chordColor + ", alpha: " + chordColor.getAlpha());
		return chordColor;
	}

	public String getChordText() {
		return chordText;
	}

	public void setChordText(String chordText) {
		this.chordText = chordText;
	}

}
