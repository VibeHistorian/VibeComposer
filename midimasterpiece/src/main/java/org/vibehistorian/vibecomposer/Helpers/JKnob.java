package org.vibehistorian.vibecomposer.Helpers;


// Imports for the GUI classes.
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.vibehistorian.vibecomposer.VibeComposerGUI;
import org.vibehistorian.vibecomposer.Popups.CloseablePopup;
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

public class JKnob extends JComponent implements MouseListener, MouseMotionListener {

	private static final long serialVersionUID = -4469347887690128306L;


	private static final int radius = 20;
	private static final int spotRadius = 3;
	private static final int arcCut = 20;
	private static final double cutOff = (Math.PI * (double) arcCut / 180.0);
	private static final double cutOffDouble = ((double) arcCut / 180.0);

	private double theta;
	private Color knobColor;
	private Color spotColor;
	public static final Color darkModeKnob = Color.GRAY;
	public static final Color lightModeKnob = new Color(180, 180, 180);

	private boolean pressedOnSpot;

	private int min = 0;
	private int max = 100;
	private int diff = 100;
	private int defaultValue = 50;
	private int curr = 50;

	private int tickSpacing = 0;
	private List<Integer> tickThresholds = null;
	private JTextField textValue = new JTextField("" + curr);

	private boolean stretchAfterCustomInput = false;
	private boolean showTextInKnob = false;
	private String shownText = "";


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
		this.addMouseListener(this);
		this.addMouseMotionListener(this);
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
		g.setFont(new Font("Arial", Font.BOLD, 12));
		if (g instanceof Graphics2D) {
			Graphics2D g2d = (Graphics2D) g;
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
					RenderingHints.VALUE_ANTIALIAS_ON);
			// Draw the knob.
			g2d.setColor((VibeComposerGUI.isDarkMode) ? darkModeKnob : lightModeKnob);
			g2d.fillOval(0, 0, 2 * radius, 2 * radius);


			// Find the center of the spot.
			Point pt = getSpotCenter();
			int xc = (int) pt.getX();
			int yc = (int) pt.getY();

			// Draw outer circle


			// Draw the spot.
			if (VibeComposerGUI.isDarkMode) {
				g2d.setColor(spotColor);
			} else {
				g2d.setColor(VibeComposerGUI.lightModeUIColor.darker());
			}
			//g2d.setColor(spotColor);
			//g2d.drawOval(0, 0, 2 * radius, 2 * radius);
			g2d.fillOval(xc - spotRadius, yc - spotRadius, 2 * spotRadius, 2 * spotRadius);

			// Draw arc.
			//g2d.fillArc(0, 10, 2 * radius, 2 * (radius - 3), 270 - arcCut, arcCut * 2);

			// Draw value.
			g2d.setColor((VibeComposerGUI.isDarkMode) ? VibeComposerGUI.darkModeUIColor
					: VibeComposerGUI.lightModeUIColor.darker());
			Point cnt = getCenter();
			String valueString = String.valueOf(getValue());
			if (!valueString.equals(textValue.getText())) {
				textValue.setText(valueString);
			}

			g2d.drawString(valueString, cnt.x - 1 - valueString.length() * 3, cnt.y + 4);

			if (showTextInKnob) {
				if (VibeComposerGUI.isDarkMode) {
					g2d.setColor(OMNI.alphen(Color.white, 190));
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
			}


		}
	}

	private int[] getPointXY(int dist, double rad) {
		int[] coord = new int[2];
		coord[0] = (int) (dist * Math.cos(rad) + dist);
		coord[1] = (int) (-dist * Math.sin(rad) + dist);
		return coord;
	}

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
		theta = toTheta(calculateDouble());
	}

	public int getValue() {
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
					return smallestInt;
				}
			}
			curr = smallestInt;
			return smallestInt;
		}
		curr = intVal;
		return intVal;
	}

	public void setValue(int val) {
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
		if (e.getButton() == MouseEvent.BUTTON1) {
			Point mouseLoc = e.getPoint();
			pressedOnSpot = isOnCenter(mouseLoc);
			recalc(e);
		} else if (SwingUtilities.isRightMouseButton(e)) {
			curr = defaultValue;
			setAngle();
			repaint();
		} else if (SwingUtilities.isMiddleMouseButton(e)) {
			CloseablePopup popup = new KnobValuePopup(this, stretchAfterCustomInput);
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
		//System.out.println("Theta: " + (0.5 + (theta) / (2 * Math.PI)));
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
		if (pressedOnSpot) {

			recalc(e);
		}
	}

	private void recalc(MouseEvent e) {
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
		if (Math.PI - Math.abs(thetaCalc) > cutOff) {
			theta = thetaCalc;
		} else {
			if (thetaCalc > 0) {
				theta = Math.PI - cutOff;
			} else {
				theta = -Math.PI + cutOff;
			}
		}

		repaint();
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

	public int getCurr() {
		return curr;
	}

	public void setCurr(int curr) {
		this.curr = curr;
	}

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
}