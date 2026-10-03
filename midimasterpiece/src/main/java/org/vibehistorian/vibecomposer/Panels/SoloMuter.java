package org.vibehistorian.vibecomposer.Panels;

import org.vibehistorian.vibecomposer.UITheme;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class SoloMuter extends JPanel {
	public interface Context {
		void onSoloToggled(SoloMuter soloMuter);
		void onMuteToggled(SoloMuter soloMuter);
		SoloMuter getGlobalSoloMuter();
		SoloMuter getGroupSoloMuter(int instrumentIndex);
	}

	public enum Type {
		SINGLE, GROUP, GLOBAL;
	}

	public enum State {
		OFF, HALF, FULL;
	}

	public static final Color OFF_DARK = Color.white.darker();
	public static final Color OFF_LIGHT = Color.black.brighter();

	public static final Color FULL_SOLO = new Color(60, 180, 60);
	public static final Color HALF_SOLO = new Color(120, 180, 120);
	public static final Color EMPTY = null;

	public static final Color FULL_MUTE = new Color(180, 180, 60);
	public static final Color HALF_MUTE = new Color(180, 180, 120);

	private static final long serialVersionUID = -6597866822061307462L;

	public JButton soloer = new JButton();
	public JButton muter = new JButton();
	public Integer inst = null;
	public Type type = Type.SINGLE;
	public State soloState = State.OFF;
	public State muteState = State.OFF;
	public SoloMuter smParent = null;
	private final Context context;

	public SoloMuter(Integer inst, Type type, Context context) {
		super();
		this.context = context;
		setOpaque(false);
		this.type = type;
		if (type == Type.GROUP) {
			setPreferredSize(new Dimension(70, 35));
			smParent = context.getGlobalSoloMuter();
			setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		} else if (type == Type.SINGLE) {
			smParent = context.getGroupSoloMuter(inst);
		}

		this.inst = inst;
		soloer.setText("S");
		soloer.setFont(new Font(Font.DIALOG, Font.PLAIN, 10));
		muter.setText("M");
		muter.setFont(new Font(Font.DIALOG, Font.PLAIN, 10));
		soloer.addMouseListener(new MouseAdapter() {

			@Override
			public void mousePressed(MouseEvent e) {
				if (SwingUtilities.isLeftMouseButton(e)) {
					toggleSolo();
				} else if (SwingUtilities.isMiddleMouseButton(e) && type != Type.GLOBAL) {
					if (context.getGlobalSoloMuter().soloState != State.OFF) {
						context.getGlobalSoloMuter().toggleSolo();
					}
					toggleSolo();
				}

			}

		});
		muter.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				toggleMute();
			}

		});
		add(soloer);
		add(muter);

	}

	public void halfSolo() {
		soloState = State.HALF;
		soloer.setBackground(HALF_SOLO);
		soloer.setForeground(Color.black);
	}

	public void halfMute() {
		muteState = State.HALF;
		muter.setBackground(HALF_MUTE);
		muter.setForeground(Color.black);
	}

	public void toggleSolo() {
		context.onSoloToggled(this);
	}

	public void solo() {
		if (!checkEnabled(soloer))
			return;
		soloState = State.FULL;
		soloer.setBackground(FULL_SOLO);
		soloer.setForeground(Color.black);
		unmute();
	}

	public void unsolo() {
		if (!checkEnabled(soloer))
			return;
		soloState = State.OFF;
		soloer.setBackground(EMPTY);
		soloer.setForeground(UITheme.isDarkMode ? OFF_DARK : OFF_LIGHT);
	}

	public void toggleMute() {
		context.onMuteToggled(this);
	}

	public void mute() {
		if (!checkEnabled(muter))
			return;
		muteState = State.FULL;
		muter.setBackground(FULL_MUTE);
		muter.setForeground(Color.black);
		unsolo();
	}

	public void unmute() {
		if (!checkEnabled(muter))
			return;
		muteState = State.OFF;
		muter.setBackground(EMPTY);
		muter.setForeground(UITheme.isDarkMode ? OFF_DARK : OFF_LIGHT);
	}

	public void reapplyTextColor() {
		if (muteState == State.OFF) {
			muter.setForeground(UITheme.isDarkMode ? OFF_DARK : OFF_LIGHT);
		} else {
			muter.setForeground(Color.black);
		}

		if (soloState == State.OFF) {
			soloer.setForeground(UITheme.isDarkMode ? OFF_DARK : OFF_LIGHT);
		} else {
			soloer.setForeground(Color.black);
		}
	}

	@Override
	public void setEnabled(boolean state) {
		soloer.setEnabled(state);
		muter.setEnabled(state);
	}

	private boolean checkEnabled(JButton butt) {
		return butt.isEnabled();
	}
}
