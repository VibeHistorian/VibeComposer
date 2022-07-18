package org.vibehistorian.vibecomposer.Components;


// Imports for the GUI classes.
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.JComponent;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.OMNI;
import org.vibehistorian.vibecomposer.SwingUtils;
import org.vibehistorian.vibecomposer.UndoManager;
import org.vibehistorian.vibecomposer.VibeComposerGUI;
import org.vibehistorian.vibecomposer.Panels.InstPanel;
import org.vibehistorian.vibecomposer.Panels.KnobPanel;
import org.vibehistorian.vibecomposer.Popups.KnobValuePopup;

/**
 * JKnob.java -
 * A knob component. The knob can be rotated by dragging
 * a spot on the knob around in a circle.
 * The knob will report its position in radians when asked.
 *
 * Author of the base version of this component:
 * 
 * @author Grant William Braught
 * @author Dickinson College
 * @version 12/4/2000
 */

public class JKnob extends JComponent
		implements MouseListener, MouseMotionListener, GloballyLockable {

	private static final long serialVersionUID = -4469347887690128306L;


	private static final int radius = 20;
	private static final int spotRadius = 3;
	private static final int arcWidth = spotRadius;
	private static final int arcCut = 30;
	private static final double cutOff = (Math.PI * (double) arcCut / 180.0);
	private static final double cutOffDouble = ((double) arcCut / 180.0);

	private double theta;
	private Color knobColor;
	private Color spotColor;
	public static final Color darkModeKnob = Color.GRAY;
	public static final Color lightModeKnob = new Color(180, 180, 180);

	private boolean pressedOnSpot;
	private long dragLimitMs = 0;

	private int min = 0;
	private int max = 100;
	private int diff = 100;
	private int defaultValue = 50;
	private int curr = 50;

	private int tickSpacing = 0;
	private List<Integer> tickThresholds = null;
	private JTextField textValue = new JTextField("" + curr);

	private boolean stretchAfterCustomInput = false;
	private boolean allowValuesOutsideRange = false;

	private boolean showTextInKnob = false;
	private String shownText = "";
	private boolean regenerating = true;
	private static final Font mainFont = new Font("Arial", Font.BOLD, 12);

	private Point startPoint = null;

	public static boolean fine = false;
	public static int fineStart = 50;
	public static boolean ctrlClick = false;

	private Consumer<? super Object> func = null;

	/**
	 * No-Arg constructor that initializes the position
	 * of the knob to 0 radians (Up).
	 */
	public JKnob() {
		this(0);
	}

	/**
	 * Constructor that initializes the position
	 * of the knob to the specified angle in radians.
	 *
	 * @param initAngle the initial angle of the knob.
	 */
	public JKnob(double initTheta) {
		this(initTheta, Color.gray, Color.black);
	}

	public JKnob(int min, int max, int curr) {
		this(min, max, curr, 0);
	}

	public JKnob(int min, int max, int curr, int tickSpacing) {
		this(0, Color.gray, Color.black);
		this.min = min;
		this.max = max;
		this.diff = max - min;
		this.defaultValue = curr;
		this.curr = curr;
		setAngle();
		if (tickSpacing > 0) {
			this.tickSpacing = tickSpacing;
			tickThresholds = new ArrayList<>();
			int counter = min;
			while (counter <= max) {
				tickThresholds.add(counter);
				counter += tickSpacing;
			}
		}
	}

	/**
	 * Constructor that initializes the position of the
	 * knob to the specified position and also allows the
	 * colors of the knob and spot to be specified.
	 *
	 * @param initAngle     the initial angle of the knob.
	 * @param initColor     the color of the knob.
	 * @param initSpotColor the color of the spot.
	 */
	public JKnob(double initTheta, Color initKnobColor, Color initSpotColor) {

		theta = initTheta;
		pressedOnSpot = false;
		knobColor = initKnobColor;
		spotColor = initSpotColor;
		textValue.setVisible(false);
		setSize(2 * radius, 2 * radius);
		this.addMouseListener(this);
		this.addMouseMotionListener(this);
		addMouseWheelListener(new MouseWheelListener() {

			@Override
			public void mouseWheelMoved(MouseWheelEvent e) {
				int val = 0;
				if (tickSpacing > 0) {
					int index = tickThresholds.indexOf(curr);
					if (index < 0) {
						int closest = MidiUtils.getClosestFromList(tickThresholds, curr);
						index = tickThresholds.indexOf(closest);
					}
					val = tickThresholds.get(
							OMNI.clamp(index - e.getWheelRotation(), 0, tickThresholds.size() - 1));
				} else {
					int scrollAmount = e.isShiftDown() ? 1 : Math.max(1, (max - min) / 20);
					val = OMNI.clamp(curr - e.getWheelRotation() * scrollAmount, min, max);
				}

				if (e.isControlDown()) {
					setValueGlobal(val);
				} else {
					setValue(val);
				}

				repaint();
			}
		});
	}


	/**
	 * Paint the JKnob on the graphics context given. The knob
	 * is a filled circle with a small filled circle offset
	 * within it to show the current angular position of the
	 * knob.
	 *
	 * @param g The graphics context on which to paint the knob.
	 */
	@Override
	public void paintComponent(Graphics g) {
		super.paintComponent(g);
		if (g instanceof Graphics2D) {
			Graphics2D g2d = (Graphics2D) g;
			g2d.setFont(mainFont);
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
					RenderingHints.VALUE_ANTIALIAS_ON);

			// Draw the knob.
			Color bgColorOval = (VibeComposerGUI.isDarkMode) ? darkModeKnob : lightModeKnob;
			g2d.setColor(!isEnabled() ? bgColorOval.darker() : bgColorOval);
			g2d.fillOval(0, 0, 2 * radius, 2 * radius);

			// Find the center of the spot.
			Point pt = getSpotCenter();
			int xc = (int) pt.getX();
			int yc = (int) pt.getY();

			// Draw outer circle


			// Draw arcs.

			//g2d.fillArc(0, 10, 2 * radius, 2 * (radius - 3), 270 - 20, 20 * 2);
			if (VibeComposerGUI.isDarkMode) {
				g2d.setColor(OMNI.alphen(VibeComposerGUI.darkModeUIColor, 100));
			} else {
				g2d.setColor(OMNI.alphen(Color.white, 180));
			}
			g2d.fillArc(0, 0, 2 * radius, 2 * radius, 270 - arcCut,
					-1 * (int) ((360 - 2 * arcCut) * toDouble(theta)));

			g2d.setColor((VibeComposerGUI.isDarkMode) ? darkModeKnob : lightModeKnob);
			g2d.fillArc(arcWidth, arcWidth, 2 * (radius - arcWidth), 2 * (radius - arcWidth),
					270 - arcCut, -1 * (int) ((360 - 2 * arcCut) * toDouble(theta)));

			// Draw value.
			g2d.setColor((VibeComposerGUI.isDarkMode) ? VibeComposerGUI.darkModeUIColor
					: VibeComposerGUI.lightModeUIColor.darker());
			Point cnt = getCenter();
			String valueString = String.valueOf(curr);

			g2d.drawString(valueString, cnt.x - 1 - valueString.length() * 3, cnt.y + 4);

			if (showTextInKnob) {
				if (VibeComposerGUI.isDarkMode) {
					g2d.setColor(OMNI.alphen(Color.white, 230));
				} else {
					g2d.setColor(OMNI.alphen(Color.black, 210));
				}
				String textBase = shownText;

				String text = textBase.substring(textBase.length() / 2) + textBase
						+ textBase.substring(0, textBase.length() / 2);

				int fakeTextStart = (textBase.length() + 1) / 2;
				int fakeTextEnd = (fakeTextStart * 2 + textBase.length() * 2 + 1) / 2;

				int FONT_SIZE = 9;

				Font font = new Font("Arial", Font.PLAIN, FONT_SIZE);
				FontRenderContext frc = g2d.getFontRenderContext();
				g2d.translate(8, -7); // Starting position of the text

				GlyphVector gv = font.createGlyphVector(frc, text);
				int length = gv.getNumGlyphs(); // Same as text.length()
				final double toRad = Math.PI / 180;
				for (int i = 0; i < length; i++) {
					if (i >= fakeTextStart && i < fakeTextEnd)
						continue;
					int r = 13;
					int[] coords = this.getPointXY(r, -360.0 / length * i * toRad + Math.PI / 2);
					gv.setGlyphPosition(i, new Point(0, 0));
					AffineTransform at = AffineTransform.getTranslateInstance(coords[0], coords[1]);
					at.rotate(2 * Math.PI * i / length);
					at.translate(r * Math.cos(Math.PI / 2 - 2 * Math.PI * i / length),
							r * Math.sin(Math.PI / 2 - 2 * Math.PI * i / length));
					at.translate(-FONT_SIZE / 2, 0);
					//at.rotate(Math.PI);
					Shape glyph = gv.getGlyphOutline(i);
					Shape transformedGlyph = at.createTransformedShape(glyph);
					g2d.fill(transformedGlyph);
				}
				g2d.translate(-8, 7); // Starting position of the text
			}

			// Draw the spot.
			if (VibeComposerGUI.isDarkMode) {
				g2d.setColor(spotColor);
			} else {
				g2d.setColor(Color.white);
			}
			//g2d.setColor(spotColor);
			//g2d.drawOval(0, 0, 2 * radius, 2 * radius);
			g2d.fillOval(xc - spotRadius, yc - spotRadius, 2 * spotRadius, 2 * spotRadius);

			g2d.dispose();
		}
	}

	private int[] getPointXY(int dist, double rad) {
		int[] coord = new int[2];
		coord[0] = (int) (dist * Math.cos(rad) + dist);
		coord[1] = (int) (-dist * Math.sin(rad) + dist);
		return coord;
	}

	/*@Override
	public Dimension getSize() {
		return new Dimension(2 * radius, 2 * radius);
	}*/

	/**
	 * Return the ideal size that the knob would like to be.
	 *
	 * @return the preferred size of the JKnob.
	 */
	@Override
	public Dimension getPreferredSize() {
		return new Dimension(2 * radius, 2 * radius);
	}

	/**
	 * Return the minimum size that the knob would like to be.
	 * This is the same size as the preferred size so the
	 * knob will be of a fixed size.
	 *
	 * @return the minimum size of the JKnob.
	 */
	@Override
	public Dimension getMinimumSize() {
		return new Dimension(2 * radius, 2 * radius);
	}

	/**
	 * Get the current anglular position of the knob.
	 *
	 * @return the current anglular position of the knob.
	 */


	public double getAngle() {
		return theta;
	}

	public void setAngle() {
		setTheta(toTheta(calculateDouble()));
	}

	public void setTheta(double thetaVal) {
		theta = thetaVal;
		updateAndGetValue();
	}

	public int updateAndGetValue() {
		double val = toDouble(theta);
		int intVal = min + (int) Math.round(val * diff);
		int smallestDiff = Integer.MAX_VALUE;
		int smallestInt = 0;
		if (tickSpacing > 0) {
			for (Integer i : tickThresholds) {
				int currentDiff = Math.abs(intVal - i);
				if (smallestDiff > currentDiff) {
					smallestDiff = currentDiff;
					smallestInt = i;
				} else if (smallestDiff != Integer.MAX_VALUE && smallestDiff < currentDiff) {
					curr = smallestInt;
					updateText();
					return smallestInt;
				}
			}
			curr = smallestInt;
			updateText();
			return smallestInt;
		}
		curr = intVal;
		updateText();
		return intVal;
	}

	private void updateText() {
		String valueString = String.valueOf(curr);
		if (!valueString.equals(textValue.getText())) {
			textValue.setText(valueString);
		}

		if (func != null) {
			func.accept(new Object());
		}
	}

	public void setValue(int val) {
		if (!isEnabled()) {
			return;
		}

		curr = val;

		setAngle();
		repaint();
	}

	/**
	 * Calculate the x, y coordinates of the center of the spot.
	 *
	 * @return a Point containing the x,y position of the center
	 *         of the spot.
	 */
	private Point getSpotCenter() {

		// Calculate the center point of the spot RELATIVE to the
		// center of the of the circle.

		int r = radius - spotRadius;

		int xcp = (int) (r * Math.sin(theta));
		int ycp = (int) (r * Math.cos(theta));

		// Adjust the center point of the spot so that it is offset
		// from the center of the circle.  This is necessary becasue
		// 0,0 is not actually the center of the circle, it is  the 
		// upper left corner of the component!
		int xc = radius + xcp;
		int yc = radius - ycp;

		// Create a new Point to return since we can't  
		// return 2 values!
		return new Point(xc, yc);
	}

	private Point getCenter() {
		return new Point(radius, radius);
	}

	/**
	 * Determine if the mouse click was on the spot or
	 * not. If it was return true, otherwise return
	 * false.
	 *
	 * @return true if x,y is on the spot and false if not.
	 */
	private boolean isOnCenter(Point pt) {
		return (pt.distance(getCenter()) < radius);
	}

	// Methods from the MouseListener interface.

	/**
	 * Empy method because nothing happens on a click.
	 *
	 * @param e reference to a MouseEvent object describing
	 *          the mouse click.
	 */
	@Override
	public void mouseClicked(MouseEvent e) {

	}

	/**
	 * Empty method because nothing happens when the mouse
	 * enters the Knob.
	 *
	 * @param e reference to a MouseEvent object describing
	 *          the mouse entry.
	 */
	@Override
	public void mouseEntered(MouseEvent e) {
	}

	/**
	 * Empty method because nothing happens when the mouse
	 * exits the knob.
	 *
	 * @param e reference to a MouseEvent object describing
	 *          the mouse exit.
	 */
	@Override
	public void mouseExited(MouseEvent e) {
	}

	/**
	 * When the mouse button is pressed, the dragging of the
	 * spot will be enabled if the button was pressed over
	 * the spot.
	 *
	 * @param e reference to a MouseEvent object describing
	 *          the mouse press.
	 */
	@Override
	public void mousePressed(MouseEvent e) {
		if (e.isControlDown()) {
			ctrlClick = true;
		}

		UndoManager.saveToHistory(this.parent(), updateAndGetValue());

		if (SwingUtilities.isLeftMouseButton(e)) {
			Point mouseLoc = e.getPoint();
			pressedOnSpot = isOnCenter(mouseLoc);

			fine = VibeComposerGUI.knobControlByDragging.isSelected() || e.isShiftDown();
			fineStart = curr;
			startPoint = new Point(MouseInfo.getPointerInfo().getLocation());
			SwingUtilities.convertPointFromScreen(startPoint, JKnob.this);
			recalc(e);
		} else if (SwingUtilities.isRightMouseButton(e)) {
			fineStart = curr;
			if (ctrlClick) {
				setValueGlobal(defaultValue);
			} else {
				setValue(defaultValue);
			}
		} else if (SwingUtilities.isMiddleMouseButton(e)) {
			if (e.isControlDown()) {
				if (e.isShiftDown()) {
					setEnabledGlobal(!isEnabled());
				} else {
					setEnabled(!isEnabled());
				}
			} else if (isEnabled()) {
				KnobValuePopup kvp = new KnobValuePopup(this, stretchAfterCustomInput, true);
				kvp.setRegenerating(regenerating);
			}
		}

	}

	/**
	 * When the button is released, the dragging of the spot
	 * is disabled.
	 *
	 * @param e reference to a MouseEvent object describing
	 *          the mouse release.
	 */
	@Override
	public void mouseReleased(MouseEvent e) {
		pressedOnSpot = false;
		if (isEnabled() && regenerating && !SwingUtilities.isMiddleMouseButton(e)
				&& VibeComposerGUI.canRegenerateOnChange() && (fineStart != curr)) {
			VibeComposerGUI.vibeComposerGUI.regenerate();
		}

		fine = false;
		ctrlClick = false;
		startPoint = null;
		fineStart = curr;
		//LG.d("Theta: " + (0.5 + (theta) / (2 * Math.PI)));
	}

	// Methods from the MouseMotionListener interface.

	/**
	 * Empty method because nothing happens when the mouse
	 * is moved if it is not being dragged.
	 *
	 * @param e reference to a MouseEvent object describing
	 *          the mouse move.
	 */
	@Override
	public void mouseMoved(MouseEvent e) {
	}

	/**
	 * Compute the new angle for the spot and repaint the
	 * knob. The new angle is computed based on the new
	 * mouse position.
	 *
	 * @param e reference to a MouseEvent object describing
	 *          the mouse drag.
	 */
	@Override
	public void mouseDragged(MouseEvent e) {
		/*if (System.currentTimeMillis() - dragLimitMs < 25) {
			return;
		}
		dragLimitMs = System.currentTimeMillis();*/
		if (pressedOnSpot || fine) {

			recalc(e);
		}
	}

	public void updateValueFromScreen(MouseEvent e) {
		if (!isEnabled()) {
			return;
		}
		Point xy = new Point(MouseInfo.getPointerInfo().getLocation());
		SwingUtilities.convertPointFromScreen(xy, JKnob.this);

		int yChange = startPoint.y - xy.y;
		int range = max - min;
		int newVal = fineStart + (range * yChange / getHeight());
		if (fine) {
			if (e.isShiftDown()) {
				newVal = (fineStart * 99 + newVal) / 100;
			} else {
				newVal = (fineStart * 9 + newVal) / 10;
			}

		}
		if (ctrlClick) {
			setValueGlobal(OMNI.clamp(newVal, min, max));
		} else {
			setValue(OMNI.clamp(newVal, min, max));
		}

		repaint();
	}

	private void recalc(MouseEvent e) {
		if (!isEnabled()) {
			return;
		}
		if (fine) {
			updateValueFromScreen(e);
			return;
		}

		int mx = e.getX();
		int my = e.getY();

		// Compute the x, y position of the mouse RELATIVE
		// to the center of the knob.
		int mxp = mx - radius;
		int myp = radius - my;

		// Compute the new angle of the knob from the
		// new x and y position of the mouse.  
		// Math.atan2(...) computes the angle at which
		// x,y lies from the positive y axis with cw rotations
		// being positive and ccw being negative.
		double thetaCalc = Math.atan2(mxp, myp);
		if (Math.PI - Math.abs(thetaCalc) <= cutOff) {
			if (thetaCalc > 0) {
				thetaCalc = Math.PI - cutOff;
			} else {
				thetaCalc = -Math.PI + cutOff;
			}
		}

		if (ctrlClick) {
			setThetaGlobal(thetaCalc);
		} else {
			setTheta(thetaCalc);
			repaint();
		}
	}

	public static double toDouble(double thetaValue) {
		thetaValue = thetaValue / (1 - cutOffDouble);
		return 0.5 + ((thetaValue) / (2 * Math.PI));
	}

	public static double toTheta(double doubleValue) {
		doubleValue = cutOffDouble / 2 + doubleValue * (1 - cutOffDouble);
		return (doubleValue - 0.5) * 2 * Math.PI;
	}

	public double calculateDouble() {
		int distanceFromMin = curr - min;
		return distanceFromMin / ((double) diff);
	}

	public int getMin() {
		return min;
	}

	public void setMin(int min) {
		this.min = min;
		diff = max - min;
	}

	public int getMax() {
		return max;
	}

	public void setMax(int max) {
		this.max = max;
		diff = max - min;
		if (curr > max) {
			curr = max;
		}
		setValue(curr);
	}

	public void setMaxRaw(int max) {
		this.max = max;
		diff = max - min;
	}

	/*public int getCurr() {
		return curr;
	}
	
	public void setCurr(int curr) {
		this.curr = curr;
	}*/

	public List<Integer> getTickThresholds() {
		return tickThresholds;
	}

	public void setTickThresholds(List<Integer> tickThresholds) {
		this.tickThresholds = tickThresholds;
	}

	public int getTickSpacing() {
		return tickSpacing;
	}

	public void setTickSpacing(int tickSpacing) {
		this.tickSpacing = tickSpacing;
	}

	public boolean isStretchAfterCustomInput() {
		return stretchAfterCustomInput;
	}

	public void setStretchAfterCustomInput(boolean stretchAfterCustomInput) {
		this.stretchAfterCustomInput = stretchAfterCustomInput;
	}

	public JTextField getTextValue() {
		return textValue;
	}

	public void setTextValue(JTextField textValue) {
		this.textValue = textValue;
	}

	public boolean isShowTextInKnob() {
		return showTextInKnob;
	}

	public void setShowTextInKnob(boolean showTextInKnob) {
		this.showTextInKnob = showTextInKnob;
		shownText = getName();
		if (shownText.length() >= 6) {
			shownText = shownText.substring(0, 6);
		} else {
			boolean prefix = true;
			// altenately append space around text, starting with prefix
			while (shownText.length() < 6) {
				shownText = ((prefix) ? " " : "") + shownText + ((!prefix) ? " " : "");
				prefix = !prefix;
			}
		}
		shownText = " " + shownText;
		shownText = shownText.toUpperCase();
	}

	public void setAllowValuesOutsideRange(boolean b) {
		allowValuesOutsideRange = true;
	}

	public boolean isRegenerating() {
		return regenerating;
	}

	public void setRegenerating(boolean regenerating) {
		this.regenerating = regenerating;
	}

	public void setFunc(Consumer<? super Object> func) {
		this.func = func;
	}

	public void removeFunc() {
		func = null;
	}

	public KnobPanel parent() {
		return (KnobPanel) getParent().getParent();
	}

	@Override
	public void setEnabledGlobal(boolean enabled) {
		InstPanel instParent = SwingUtils.getInstParent(this);
		if (instParent == null) {
			setEnabled(enabled);
			return;
		}
		for (InstPanel ip : VibeComposerGUI.getAffectedPanels(instParent.getPartNum())) {
			ip.findKnobsByName(getName()).forEach(e -> {
				e.setEnabled(enabled);
			});
		}
	}

	private void setValueGlobal(int val) {
		InstPanel instParent = SwingUtils.getInstParent(this);
		if (instParent == null) {
			setValue(val);
			return;
		}
		for (InstPanel ip : VibeComposerGUI.getAffectedPanels(instParent.getPartNum())) {
			ip.findKnobsByName(getName()).forEach(e -> {
				if (e.isEnabled()) {
					e.setValue(val);
					e.paintComponent(e.getGraphics());
				}
			});
		}
	}

	private void setThetaGlobal(double thetaVal) {
		InstPanel instParent = SwingUtils.getInstParent(this);
		if (instParent == null) {
			setTheta(thetaVal);
			repaint();
			return;
		}
		for (InstPanel ip : VibeComposerGUI.getAffectedPanels(instParent.getPartNum())) {
			ip.findKnobsByName(getName()).forEach(e -> {
				if (e.isEnabled()) {
					e.setTheta(thetaVal);
					e.paintComponent(e.getGraphics());
				}
			});
		}
	}

	public int getValueRaw() {
		return curr;
	}
}