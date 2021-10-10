package org.vibehistorian.vibecomposer.Panels;

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
import org.vibehistorian.vibecomposer.Helpers.VeloRect;

public class NumPanel extends JPanel {

	private static final long serialVersionUID = -2145278227995141172L;

	private JTextField text = null;
	private JLabel label = null;
	private VeloRect slider = null;
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

	public NumPanel(String name, int value) {
		this(name, value, 0, 100);
	}

	public NumPanel(String name, int value, int minimum, int maximum) {
		this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setOpaque(false);
		defaultValue = value;
		label = new JLabel(name);
		text = new JTextField(String.valueOf(value), maximum > 999 ? 3 : 2);
		slider = new VeloRect(minimum, maximum, value);
		initSlider(minimum, maximum, value);
		initText();
		add(label);
		add(text);
		add(slider);
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
				closeParentFrame();
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
		JFrame parent = (JFrame) SwingUtilities.getWindowAncestor(NumPanel.this);
		Toolkit.getDefaultToolkit().getSystemEventQueue()
				.postEvent(new WindowEvent(parent, WindowEvent.WINDOW_CLOSING));

	}

	private void initSlider(int minimum, int maximum, int value) {
		slider.setValue(value);
		//slider.setOrientation(JSlider.VERTICAL);
		slider.setPreferredSize(new Dimension(20, 40));
		//slider.setPaintTicks(true);

		slider.addMouseListener(new MouseAdapter() {
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
					int potentialMax = slider.getValue() + slider.getMax() / 10;
					int potentialMin = slider.getValue() - slider.getMax() / 10;
					slider.setMax(
							(potentialMax > slider.getMax()) ? slider.getMax() : potentialMax);
					slider.setMin(
							(potentialMin < slider.getMin()) ? slider.getMin() : potentialMin);
				}
				dragging = true;
				startNumSliderThread(me);


			}

			@Override
			public void mouseReleased(MouseEvent me) {
				if (needToReset || buttonPresses == 2) {
					needToReset = false;
					slider.setMax(naturalMax);
					slider.setMin(naturalMin);
				} else if (buttonPresses == 1) {
					dragging = false;
					if (needToReset) {
						needToReset = false;
						slider.setMax(naturalMax);
						slider.setMin(naturalMin);
					}
				}
				buttonPresses--;
			}

			public void startNumSliderThread(MouseEvent me) {
				if (numCycle != null && numCycle.isAlive()) {
					//System.out.println("Label slider thread already exists! " + label.getText());
					return;
				}
				//System.out.println("Starting new label slider thread..! " + label.getText());
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
				text.setText(String.valueOf(slider.getValue()));
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
			if (tryValue > slider.getMax()) {
				slider.setValue(slider.getMax());
				updateTextLater(true);
			} else if (tryValue < slider.getMin()) {
				slider.setValue(slider.getMin());
				updateTextLater(false);
			} else {
				slider.setValue(tryValue);
			}

		} catch (NumberFormatException ex) {
			System.out.println("Invalid value: " + text.getText());
		}
	}

	private void updateTextLater(boolean isMaximum) {
		Runnable doAssist = new Runnable() {
			@Override
			public void run() {
				text.setText(((isMaximum) ? slider.getMax() : slider.getMin()) + "");
			}
		};
		SwingUtilities.invokeLater(doAssist);
	}

	public String getName() {
		return label.getText();
	}

	public int getInt() {
		return slider.getValue();
	}

	public void setInt(int val) {
		text.setText(val + "");
	}

	public VeloRect getSlider() {
		return slider;
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
}
