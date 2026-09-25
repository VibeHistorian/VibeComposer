package org.vibehistorian.vibecomposer.Popups;

import org.vibehistorian.vibecomposer.GenerationGUI;

import org.vibehistorian.vibecomposer.Components.JKnob;
import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.OMNI;
import org.vibehistorian.vibecomposer.Panels.NumPanel;
import org.vibehistorian.vibecomposer.VibeComposerGUI;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

public class KnobValuePopup extends CloseablePopup {
	private JKnob knob = null;
	private NumPanel numPanel = null;
	private boolean stretchAfterCustomInput = false;
	private Integer customInput = null;
	private boolean regenerating = true;

	public KnobValuePopup(JKnob knob, boolean stretch, boolean allowValuesOutsideRange) {
		super("Knob Value Setting", 0);
		this.knob = knob;
		stretchAfterCustomInput = stretch;

		numPanel = new NumPanel("Knob", knob.updateAndGetValue(), knob.getMin(), knob.getMax());
		numPanel.getValueRect().setVisible(false);
		numPanel.setAllowValuesOutsideRange(allowValuesOutsideRange);
		numPanel.setParentPopup(this);
		numPanel.getTextfield().addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ENTER) {
					close();
				}
			}
		});

		frame.add(numPanel);
		frame.pack();
		frame.setVisible(true);

	}

	@Override
	public void close() {
		super.close();
	}

	protected void addFrameWindowOperation() {
		frame.addWindowListener(new WindowListener() {

			@Override
			public void windowOpened(WindowEvent e) {
				// Auto-generated method stub

			}

			@Override
			public void windowClosing(WindowEvent e) {
				if (frame.isVisible()) {
					try {
						customInput = Integer.valueOf(numPanel.getTextfield().getText());
					} catch (NumberFormatException ex) {
						LG.d("Invalid value: " + numPanel.getTextfield().getText());
					}
					if (customInput != null) {
						int val = customInput;
						if (stretchAfterCustomInput) {
							if (val > knob.getMax()) {
								knob.setMax(val);
							} else if (val < knob.getMin()) {
								knob.setMin(val);
							}
							knob.setValue(val);
							knob.repaint();
						} else {
							if (GenerationGUI.allowValuesOutOfRange.isSelected() ||
									(knob.getMin() <= val && val <= knob.getMax())) {
								knob.setValue(val);
							} else {
								knob.setValue(OMNI.clamp(val, knob.getMin(), knob.getMax()));
							}
							knob.repaint();
						}

						if (VibeComposerGUI.canRegenerateOnChange() && regenerating) {
							VibeComposerGUI.vibeComposerGUI.regenerate();
						}

					}
				}


			}

			@Override
			public void windowClosed(WindowEvent e) {
				// Auto-generated method stub

			}

			@Override
			public void windowIconified(WindowEvent e) {
				// Auto-generated method stub

			}

			@Override
			public void windowDeiconified(WindowEvent e) {
				// Auto-generated method stub

			}

			@Override
			public void windowActivated(WindowEvent e) {
				// Auto-generated method stub

			}

			@Override
			public void windowDeactivated(WindowEvent e) {
				// Auto-generated method stub

			}

		});

	}

	public boolean isRegenerating() {
		return regenerating;
	}

	public void setRegenerating(boolean regenerating) {
		this.regenerating = regenerating;
	}
}
