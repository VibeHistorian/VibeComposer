package org.vibehistorian.vibecomposer.Panels;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.apache.commons.lang3.StringUtils;
import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.OMNI;
import org.vibehistorian.vibecomposer.Components.VeloRect;
import org.vibehistorian.vibecomposer.Popups.CloseablePopup;

public class NumPanel extends JPanel {

	private static final long serialVersionUID = -2145278227995141172L;

	private JTextField text = null;
	private JLabel label = null;
	private VeloRect valueRect = null;
	boolean needToReset = false;
	private int naturalMax = 100;
	private int naturalMin = 0;
	private int buttonPresses = 0;
	private int defaultValue = 50;
	private boolean allowValuesOutsideRange = false;
	private JButton[] numButtons = null;
	private JButton clearButton = new JButton("X");
	private JButton enterButton = new JButton("OK");
	private JButton minusButton = new JButton("-");

	private JPanel buttonPanel = new JPanel();

	private CloseablePopup parentPopup;

	public NumPanel(String name, int value) {
		this(name, value, 0, 100);
	}

	public NumPanel(String name, int value, int minimum, int maximum) {
		this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setOpaque(false);
		defaultValue = value;
		label = new JLabel(name);
		text = new JTextField(String.valueOf(value), maximum > 999 ? 3 : 2);
		valueRect = new VeloRect(minimum, maximum, value);
		initValueRect(minimum, maximum, value);
		initText();
		add(label);
		add(text);
		add(valueRect);
		naturalMax = maximum;
		naturalMin = minimum;
		buttonPanel.setLayout(new GridLayout(5, 3, 0, 0));
		numButtons = new JButton[10];
		for (int i = 0; i < 10; i++) {
			int numI = i + 1;
			if (i < 9) {
				numButtons[i] = new JButton(numI + "");
				numButtons[i].addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						removeSelectedAndWrite("" + numI, false);
					}

				});
			} else {
				numButtons[i] = new JButton("0");
				numButtons[i].addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						removeSelectedAndWrite("0", false);
					}

				});
			}

			numButtons[i].setPreferredSize(new Dimension(15, 15));
			buttonPanel.add(numButtons[i]);
		}
		clearButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				removeSelectedAndWrite("", true);
			}

		});
		enterButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				if (parentPopup != null) {
					parentPopup.close();
				} else {
					closeParentFrame();
				}

			}

		});
		minusButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				if (!text.getText().contains("-")) {
					removeSelectedAndWrite("-", true);
				}

			}

		});
		buttonPanel.add(minusButton);
		buttonPanel.add(clearButton);
		buttonPanel.add(enterButton);
		for (Component c : buttonPanel.getComponents()) {
			c.setFocusable(false);
		}
		add(buttonPanel);

	}

	public void removeSelectedAndWrite(String s, boolean set) {
		if (text.getSelectedText() != null || set) {
			text.setText("");
		}
		text.setText(text.getText() + s);
	}

	public void closeParentFrame() {
		JFrame parentFrame = (JFrame) SwingUtilities.getWindowAncestor(NumPanel.this);
		Toolkit.getDefaultToolkit().getSystemEventQueue()
				.postEvent(new WindowEvent(parentFrame, WindowEvent.WINDOW_CLOSING));

	}

	private void initValueRect(int minimum, int maximum, int value) {
		valueRect.setValue(value);
		//slider.setOrientation(JSlider.VERTICAL);
		valueRect.setPreferredSize(new Dimension(20, 40));
		//slider.setPaintTicks(true);

		valueRect.addMouseListener(new MouseAdapter() {
			boolean dragging = false;
			Thread numCycle = null;
			private Timer timer;

			@Override
			public void mouseClicked(MouseEvent e) {

				if (e.getButton() == MouseEvent.BUTTON1) {
					if (timer == null) {
						timer = new Timer();
						timer.schedule(new TimerTask() {

							@Override
							public void run() { // timer expired before another click received, therefore = single click
								this.cancel();
								timer = null;
								/* single-click actions in here */
							}

						}, (Integer) Toolkit.getDefaultToolkit()
								.getDesktopProperty("awt.multiClickInterval"));
					} else { // received another click before previous click (timer) expired, therefore = double click
						timer.cancel();
						timer = null;
						/* double-click actions in here */
						setInt(defaultValue);
					}
				}
			}

			@Override
			public void mousePressed(MouseEvent me) {
				buttonPresses++;
				if (me.isShiftDown() || buttonPresses == 2) {
					needToReset = true;
					int potentialMax = valueRect.getValue() + valueRect.getMax() / 10;
					int potentialMin = valueRect.getValue() - valueRect.getMax() / 10;
					valueRect.setMax(
							(potentialMax > valueRect.getMax()) ? valueRect.getMax() : potentialMax);
					valueRect.setMin(
							(potentialMin < valueRect.getMin()) ? valueRect.getMin() : potentialMin);
				}
				dragging = true;
				startNumSliderThread(me);


			}

			@Override
			public void mouseReleased(MouseEvent me) {
				if (needToReset || buttonPresses == 2) {
					needToReset = false;
					valueRect.setMax(naturalMax);
					valueRect.setMin(naturalMin);
				} else if (buttonPresses == 1) {
					dragging = false;
					if (needToReset) {
						needToReset = false;
						valueRect.setMax(naturalMax);
						valueRect.setMin(naturalMin);
					}
				}
				buttonPresses--;
			}

			public void startNumSliderThread(MouseEvent me) {
				if (numCycle != null && numCycle.isAlive()) {
					//LG.d("Label slider thread already exists! " + label.getText());
					return;
				}
				//LG.d("Starting new label slider thread..! " + label.getText());
				numCycle = new Thread() {

					public void run() {
						while (dragging) {
							updateToolTip();
							try {
								sleep(25);
							} catch (InterruptedException e) {
								// Auto-generated catch block
								e.printStackTrace();
							}
						}
					}
				};
				numCycle.start();

			}

			public void updateToolTip() {
				text.setText(String.valueOf(valueRect.getValue()));
			}

		});
	}

	private void initText() {
		//text.setFont(new Font(Font.DIALOG, Font.PLAIN, 10));
		text.getDocument().addDocumentListener(new DocumentListener() {

			@Override
			public void insertUpdate(DocumentEvent e) {
				tryUpdate();

			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				tryUpdate();

			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				tryUpdate();

			}

		});
	}

	private void tryUpdate() {
		if (StringUtils.isEmpty(text.getText())) {
			return;
		}
		int tryValue = 0;
		try {
			tryValue = Integer.valueOf(text.getText());
			if (allowValuesOutsideRange) {
				return;
			}
			if (tryValue > valueRect.getMax()) {
				valueRect.setValue(valueRect.getMax());
				updateTextLater(true);
			} else if (tryValue < valueRect.getMin()) {
				valueRect.setValue(valueRect.getMin());
				updateTextLater(false);
			} else {
				valueRect.setValue(tryValue);
			}
			text.setBackground(OMNI.alphen(Color.red, 0));
		} catch (NumberFormatException ex) {
			LG.d("Invalid value: " + text.getText());
			text.setBackground(OMNI.alphen(Color.red, 70));
		}
	}

	private void updateTextLater(boolean isMaximum) {
		SwingUtilities.invokeLater(
				() -> text.setText(((isMaximum) ? valueRect.getMax() : valueRect.getMin()) + ""));
	}

	public String getName() {
		return label.getText();
	}

	public int getInt() {
		return valueRect.getValue();
	}

	public void setInt(int val) {
		text.setText(val + "");
	}

	public VeloRect getValueRect() {
		return valueRect;
	}

	public JTextField getTextfield() {
		return text;
	}

	public boolean isAllowValuesOutsideRange() {
		return allowValuesOutsideRange;
	}

	public void setAllowValuesOutsideRange(boolean allowValuesOutsideRange) {
		this.allowValuesOutsideRange = allowValuesOutsideRange;
	}

	public void setParentPopup(CloseablePopup popup) {
		this.parentPopup = popup;
	}
}
