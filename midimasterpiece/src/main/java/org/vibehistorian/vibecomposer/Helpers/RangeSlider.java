package org.vibehistorian.vibecomposer.Helpers;

import java.awt.Dimension;
import java.util.List;

import javax.swing.JSlider;

/**
 * An extension of JSlider to select a range of values using two thumb controls.
 * The thumb controls are used to select the lower and upper value of a range
 * with predetermined minimum and maximum values.
 * 
 * <p>
 * Note that RangeSlider makes use of the default BoundedRangeModel, which
 * supports an inner range defined by a value and an extent. The upper value
 * returned by RangeSlider is simply the lower value plus the extent.
 * </p>
 */
public class RangeSlider extends JSlider {

	private static final long serialVersionUID = -5548772458719043736L;
	private int tickStart = 0;
	private boolean displayValues = true;
	private boolean lowerDragging = false;
	private boolean upperDragging = false;
	private int lowerDraggingSnapToTicks = 0;
	private int upperDraggingSnapToTicks = 0;
	private List<Integer> customMajorTicks = null;
	private List<Integer> customMinorTicks = null;
	private boolean draggableRange = true;


	/**
	 * Constructs a RangeSlider with default minimum and maximum values of 0
	 * and 100.
	 */
	public RangeSlider() {
		initSlider();
	}

	/**
	 * Constructs a RangeSlider with the specified default minimum and maximum
	 * values.
	 */
	public RangeSlider(int min, int max) {
		super(min, max);
		initSlider();
	}

	/**
	 * Initializes the slider by setting default properties.
	 */
	private void initSlider() {
		setOpaque(false);
		setPreferredSize(new Dimension(100, 35));
		setOrientation(HORIZONTAL);
	}

	/**
	 * Overrides the superclass method to install the UI delegate to draw two
	 * thumbs.
	 */
	@Override
	public void updateUI() {
		setUI(new RangeSliderUI(this));
		// Update UI for slider labels.  This must be called after updating the
		// UI of the slider.  Refer to JSlider.updateUI().
		updateLabelUIs();
	}

	/**
	 * Returns the lower value in the range.
	 */
	@Override
	public int getValue() {
		return super.getValue();
	}

	/**
	 * Sets the lower value in the range.
	 */
	@Override
	public void setValue(int value) {
		int oldValue = getValue();
		if (oldValue == value) {
			return;
		}

		// Compute new value and extent to maintain upper value.
		int oldExtent = getExtent();
		int newValue = OMNI.clamp(value, getMinimum(), oldValue + oldExtent);
		int newExtent = oldExtent + oldValue - newValue;

		// Set new value and extent, and fire a single change event.
		getModel().setRangeProperties(newValue, newExtent, getMinimum(), getMaximum(),
				getValueIsAdjusting());
	}

	/**
	 * Returns the upper value in the range.
	 */
	public int getUpperValue() {
		return getValue() + getExtent();
	}

	/**
	 * Sets the upper value in the range.
	 */
	public void setUpperValue(int value) {
		// Compute new extent.
		int lowerValue = getValue();
		int newExtent = OMNI.clamp(value - lowerValue, 0, getMaximum() - lowerValue);

		// Set extent to set upper value.
		setExtent(newExtent);
	}

	public int getTickStart() {
		return tickStart;
	}

	public void setTickStart(int setTickStart) {
		this.tickStart = setTickStart;
	}

	public boolean isDisplayValues() {
		return displayValues;
	}

	public void setDisplayValues(boolean displayValues) {
		this.displayValues = displayValues;
	}

	public boolean isLowerDragging() {
		return lowerDragging;
	}

	public void setLowerDragging(boolean lowerDragging) {
		this.lowerDragging = lowerDragging;
	}

	public boolean isUpperDragging() {
		return upperDragging;
	}

	public void setUpperDragging(boolean upperDragging) {
		this.upperDragging = upperDragging;
	}

	public int getLowerDraggingSnapToTicks() {
		return lowerDraggingSnapToTicks;
	}

	public void setLowerDraggingSnapToTicks(int lowerDraggingSnapToTicks) {
		this.lowerDraggingSnapToTicks = lowerDraggingSnapToTicks;
	}

	public int getUpperDraggingSnapToTicks() {
		return upperDraggingSnapToTicks;
	}

	public void setUpperDraggingSnapToTicks(int upperDraggingSnapToTicks) {
		this.upperDraggingSnapToTicks = upperDraggingSnapToTicks;
	}

	public List<Integer> getCustomMajorTicks() {
		return customMajorTicks;
	}

	public void setCustomMajorTicks(List<Integer> customMajorTicks) {
		this.customMajorTicks = customMajorTicks;
	}

	public List<Integer> getCustomMinorTicks() {
		return customMinorTicks;
	}

	public void setCustomMinorTicks(List<Integer> customMinorTicks) {
		this.customMinorTicks = customMinorTicks;
	}

	public boolean isDraggableRange() {
		return draggableRange;
	}

	public void setDraggableRange(boolean draggableRange) {
		this.draggableRange = draggableRange;
	}


}
