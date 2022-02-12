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
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

import javax.swing.JComponent;

import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.OMNI;
import org.vibehistorian.vibecomposer.SwingUtils;
import org.vibehistorian.vibecomposer.Panels.ChordletPanel;

public class Chordlet extends JComponent {

	private static final long serialVersionUID = 6300044827795197600L;
	private String firstLetter = "C";
	private String spice = "";
	private boolean sharp = false;
	private Integer inversion = null;
	private int width = 30;
	private int height = 18;
	private static final Font font = new Font("Tahoma", Font.PLAIN, 14);
	private Integer dragY = null;
	private Integer dragFirstLetterIndex = null;
	private ChordletPanel parent = null;

	public Chordlet(String chord, ChordletPanel chordletPanel) {
		setupChord(chord);
		setParentPanel(chordletPanel);
	}

	private void setupChord(String chord) {
		if (chord == null || chord.length() == 0) {
			return;
		}

		calculateWidth(chord);
		firstLetter = chord.substring(0, 1).toUpperCase();
		sharp = chord.contains("#");
		inversion = chord.contains(".") ? Integer.valueOf(chord.substring(chord.indexOf(".") + 1))
				: null;

		int spiceStartIndex = sharp ? 2 : 1;
		int spiceEndIndex = inversion != null ? chord.indexOf(".") : chord.length();
		if (spiceStartIndex < spiceEndIndex) {
			spice = chord.substring(spiceStartIndex, spiceEndIndex);
		} else {
			spice = "";
		}

		setPreferredSize(new Dimension(width, height));

		addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				// start drag - mouse Y pos
				dragY = e.getPoint().y;
				dragFirstLetterIndex = MidiUtils.SEMITONE_LETTERS.indexOf(firstLetter);
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				// stop drag - reset
				reset();
			}
		});

		addMouseMotionListener(new MouseMotionListener() {

			@Override
			public void mouseDragged(MouseEvent e) {
				processMouseDrag(e);
			}

			@Override
			public void mouseMoved(MouseEvent e) {
				if (dragY != null) {
					//processMouseDrag(e);
				}
			}

		});

		addMouseWheelListener(new MouseWheelListener() {

			@Override
			public void mouseWheelMoved(MouseWheelEvent e) {
				// based on diff in Y pos - get index "", "m", "m7".., get index+1 % spice size 
				int movement = e.getWheelRotation() > 0 ? 1 : -1;
				int dragSpiceIndex = MidiUtils.SPICE_NAMES_LIST.indexOf(spice);
				int newSpiceIndex = (dragSpiceIndex + movement
						+ MidiUtils.SPICE_NAMES_LIST.size() * 50)
						% MidiUtils.SPICE_NAMES_LIST.size();
				spice = MidiUtils.SPICE_NAMES_LIST.get(newSpiceIndex);
				update();
			}
		});
	}

	private String sharpString() {
		return sharp ? "#" : "";
	}

	private void processMouseDrag(MouseEvent evt) {
		int firstLetterIndex = dragFirstLetterIndex;
		firstLetterIndex -= ((evt.getPoint().y - dragY) / 5);
		firstLetter = MidiUtils.SEMITONE_LETTERS
				.get((firstLetterIndex + MidiUtils.SEMITONE_LETTERS.size() * 50)
						% MidiUtils.SEMITONE_LETTERS.size());
		update();
	}

	private void calculateWidth(String chordText) {
		width = SwingUtils.getDrawStringWidth(chordText) + 10;
	}

	public void update() {
		calculateWidth(getChordText());
		setPreferredSize(new Dimension(width, height));
		setSize(new Dimension(width, height));
		if (parent != null) {
			parent.revalidate();
			parent.repaint();
		}

	}

	public void reset() {
		dragY = null;
		dragFirstLetterIndex = null;
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
		Color color = getColorForChord(chordText);
		Color color2 = OMNI.mixColor(color, Color.black, 0.25);
		GradientPaint gp = new GradientPaint(minX, 0, color, maxX, 0, color2);
		g.setPaint(gp);

		g.fillRoundRect(minX, 0, maxX, height, 10, 10);
		//g.fillRect(minX, 0, maxX, height);
		g.setColor(Color.black);
		g.drawString(chordText, minX + 3, height - 3);
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
			Color nextChordColor = MidiUtils.CHORD_COLORS.get((chordIndex + 1) % 7);
			chordColor = OMNI.mixColor(chordColor, nextChordColor, 0.5);
		}
		chordColor = OMNI.alphen(chordColor, Math.min((int) (chordKeyness * 180) + 75, 255));
		return chordColor;
	}

	public static Color getColorForChord(String chord) {
		return getColorForChord(chord, chord.contains("#"));
	}

	public String getChordText() {
		return firstLetter + sharpString() + spice + (inversion != null ? ("." + inversion) : "");
	}

	public ChordletPanel getParentPanel() {
		return parent;
	}

	public void setParentPanel(ChordletPanel parent) {
		this.parent = parent;
	}

}
