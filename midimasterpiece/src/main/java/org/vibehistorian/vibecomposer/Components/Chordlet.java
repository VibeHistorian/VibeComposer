package org.vibehistorian.vibecomposer.Components;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.stream.Collectors;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

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
	private int heightMargin = 2;
	private int usableHeight = 20;
	private int height = 24;
	private static final Font font = new Font("Tahoma", Font.PLAIN, 14);
	private Point dragP = null;
	private ChordletPanel cPanel = null;
	private static final Color KEYNESS_COLOR = new Color(210, 210, 210);
	private static final Color DARK_TEXT_COLOR = new Color(160, 255, 160);
	private static final String CLOSE_BTTN_ICON = " x";
	private static final int CLOSE_BTTN_WIDTH = 13;
	private static final int ROUNDED_ADJUSTMENT_WIDTH = 10;
	private long lastClickMs = 0;
	private long dragLimitMs = 0;
	private boolean canRemoveChordlet = false;

	public Chordlet(String chord, ChordletPanel chordletPanel) {
		setupChord(chord);
		setParentPanel(chordletPanel);
		setupListeners();
	}

	private void setupListeners() {
		// LMB - first letter/sharp change
		SwingUtils.addPopupMenu(this, (evt, e) -> {
			firstLetter = e.substring(0, 1);
			sharp = e.length() > 1;
			update();
		}, e -> {
			if (SwingUtilities.isRightMouseButton(e)) {
				return true;
			}
			return false;
		}, MidiUtils.SEMITONE_LETTERS, MidiUtils.SEMITONE_LETTERS.stream()
				.map(e -> Chordlet.getColorForChord(e)).collect(Collectors.toList()));

		// MMB - spice change
		SwingUtils.addPopupMenu(this, (evt, e) -> {
			spice = e;
			update();
		}, e -> {
			if (SwingUtilities.isMiddleMouseButton(e)) {
				return true;
			}
			return false;
		}, MidiUtils.SPICE_NAMES_LIST, null);

		addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if (SwingUtilities.isLeftMouseButton(e)) {
					if (!mouseOverCloseButton(e)) {
						long currentTimeMs = System.currentTimeMillis();
						if (currentTimeMs - lastClickMs < 500) {
							resetToBaseChord();
							lastClickMs = 0;
						} else {
							lastClickMs = currentTimeMs;
						}
					} else {
						canRemoveChordlet = true;
					}
					dragP = new Point(e.getPoint());
					repaint();
				}
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				if (SwingUtilities.isLeftMouseButton(e) && mouseOverCloseButton(e)
						&& canRemoveChordlet) {
					cPanel.removeChordlet(Chordlet.this, true);
				} else {
					canRemoveChordlet = false;
				}
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
				if (dragP != null) {
					processMouseDrag(e);
				}
			}

		});

		addMouseWheelListener(new MouseWheelListener() {

			@Override
			public void mouseWheelMoved(MouseWheelEvent e) {
				// based on diff in Y pos - get index "", "m", "m7".., get index+1 % spice size 
				int movement = e.getWheelRotation() > 0 ? -1 : 1;

				if (e.isControlDown()) {
					int dragSpiceIndex = MidiUtils.SPICE_NAMES_LIST.indexOf(spice);
					int newSpiceIndex = (dragSpiceIndex + movement
							+ MidiUtils.SPICE_NAMES_LIST.size() * 50)
							% MidiUtils.SPICE_NAMES_LIST.size();
					spice = MidiUtils.SPICE_NAMES_LIST.get(newSpiceIndex);
				} else if (e.isShiftDown()) {
					int firstLetterIndex = MidiUtils.SEMITONE_LETTERS
							.indexOf(firstLetter + sharpString());
					String newFirst = MidiUtils.SEMITONE_LETTERS.get(
							(firstLetterIndex + movement + MidiUtils.SEMITONE_LETTERS.size() * 50)
									% MidiUtils.SEMITONE_LETTERS.size());
					firstLetter = newFirst.substring(0, 1);
					sharp = newFirst.length() > 1;
				} else {

					if (inversion == null) {
						inversion = movement;
					} else if (inversion / movement > 0 && Math.abs(inversion + movement) > 5) {
						inversion = null;
					} else {
						inversion += movement;
					}
				}

				update();
			}
		});
	}

	protected boolean mouseOverCloseButton(MouseEvent e) {
		return mouseOverCloseButton(e.getX());
	}

	public boolean mouseOverCloseButton(int xPos) {
		return xPos > width - CLOSE_BTTN_WIDTH;
	}

	public void resetToBaseChord() {
		int firstLetterIndex = MidiUtils.CHORD_FIRST_LETTERS.indexOf(firstLetter);
		if (firstLetterIndex >= 0) {
			setupChord(MidiUtils.MAJOR_CHORDS.get(firstLetterIndex));
		} else {
			spice = "";
		}

		update();
	}

	private void setupChord(String chord) {
		if (chord == null || chord.length() == 0) {
			return;
		}

		calculateWidth(chord);
		firstLetter = chord.substring(0, 1).toUpperCase();
		sharp = isSharp(chord);
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

	}

	private String sharpString() {
		return sharp ? "#" : "";
	}

	private void processMouseDrag(MouseEvent evt) {
		if (System.currentTimeMillis() - dragLimitMs < 50 || dragP == null) {
			return;
		}
		dragLimitMs = System.currentTimeMillis();
		cPanel.handleMouseDrag(this, evt);
		/*
			int firstLetterIndex = dragFirstLetterIndex;
			firstLetterIndex -= ((evt.getPoint().y - dragY) / 20);
			firstLetter = MidiUtils.SEMITONE_LETTERS
				.get((firstLetterIndex + MidiUtils.SEMITONE_LETTERS.size() * 50)
						% MidiUtils.SEMITONE_LETTERS.size());*/
	}

	private void calculateWidth(String chordText) {
		width = SwingUtils.getDrawStringWidth(chordText) + ROUNDED_ADJUSTMENT_WIDTH
				+ CLOSE_BTTN_WIDTH;
	}

	public void update() {
		calculateWidth(getChordText());
		setPreferredSize(new Dimension(width, height));
		setSize(new Dimension(width, height));
		if (cPanel != null) {
			cPanel.revalidate();
			cPanel.repaint();
		}

	}

	public void reset() {
		dragP = null;
		repaint();
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
		int height = usableHeight;

		String chordText = getChordText();
		int[] mapped = MidiUtils.mappedChord(chordText);
		double chordOutOfKeyness = mapped != null ? 1 - MidiUtils.getChordKeyness(mapped) : 0;
		Color color = getColorForChord(chordText);
		Color color2 = OMNI.mixColor(color, Color.black, 0.25);
		GradientPaint gp = new GradientPaint(minX, 0, color2, maxX, 0, color);
		g.setPaint(gp);

		g.fillRoundRect(minX, heightMargin, maxX, height, 10, 10);
		//g.fillRect(minX, 0, maxX, height);
		if (chordOutOfKeyness > 0.05) {
			g.setColor(KEYNESS_COLOR);
			Stroke dashed = new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0,
					new float[] { 9 }, 0);
			g.setStroke(dashed);
			g.drawLine(maxX, heightMargin + 3, (int) (maxX - maxX * chordOutOfKeyness - 3),
					heightMargin + 3);
			g.setStroke(new BasicStroke());
		}
		g.setColor(DARK_TEXT_COLOR);
		g.drawString(chordText, minX + 3, height - 2);
		g.setColor(Color.black);
		g.drawString(CLOSE_BTTN_ICON, 9 + width - CLOSE_BTTN_WIDTH - ROUNDED_ADJUSTMENT_WIDTH,
				height - 2);
		g.setColor(dragP != null ? Color.white : Color.black);
		g.drawRoundRect(minX, heightMargin, maxX - 1, height, 10, 10);

		//g.dispose();
	}

	public Color getColor() {
		return getColorForChord(getChordText(), sharp);
	}

	public static Color getColorForChord(String chord, boolean isSharp) {
		int[] mapped = MidiUtils.mappedChord(chord);
		double chordKeyness = mapped != null ? MidiUtils.getChordKeyness(mapped) : 1;
		int chordIndex = MidiUtils.CHORD_FIRST_LETTERS.indexOf(chord.substring(0, 1));
		Color chordColor = MidiUtils.CHORD_COLORS.get(chordIndex);
		if (isSharp) {
			Color nextChordColor = MidiUtils.CHORD_COLORS.get((chordIndex + 1) % 7);
			chordColor = OMNI.mixColor(chordColor, nextChordColor, 0.5);
		}
		int range = 140;
		int minimumIntensity = 60;
		int chordkeyIntensity = (int) (chordKeyness * range);
		if (chordkeyIntensity > range - 2) {
			chordColor = OMNI.alphen(chordColor, 255);
		} else {
			chordColor = OMNI.alphen(chordColor, chordkeyIntensity + minimumIntensity);
		}
		return chordColor;
	}

	public static Color getColorForChord(String chord) {
		return getColorForChord(chord, isSharp(chord));
	}

	public static boolean isSharp(String chord) {
		return chord.length() > 1 && chord.substring(1, 2).equals("#");
	}

	public String getChordText() {
		return firstLetter + sharpString() + spice + (inversion != null ? ("." + inversion) : "");
	}

	public ChordletPanel getParentPanel() {
		return cPanel;
	}

	public void setParentPanel(ChordletPanel parent) {
		this.cPanel = parent;
	}

	public int getWidth() {
		return width;
	}

	public void prepareDragging(MouseEvent evt) {
		dragP = new Point(evt.getPoint());
	}

}
