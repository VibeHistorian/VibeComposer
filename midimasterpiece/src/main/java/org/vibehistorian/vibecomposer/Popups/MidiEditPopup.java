package org.vibehistorian.vibecomposer.Popups;

import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.apache.commons.lang3.StringUtils;
import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.Section;
import org.vibehistorian.vibecomposer.VibeComposerGUI;
import org.vibehistorian.vibecomposer.Components.CheckButton;
import org.vibehistorian.vibecomposer.Components.MidiEditArea;
import org.vibehistorian.vibecomposer.Components.ScrollComboBox;
import org.vibehistorian.vibecomposer.Helpers.PhraseNotes;
import org.vibehistorian.vibecomposer.Panels.InstPanel;

public class MidiEditPopup extends CloseablePopup {

	MidiEditArea mvea = null;
	InstPanel parent = null;
	JTextField text = null;
	Section sec = null;
	public ScrollComboBox<String> highlightMode = new ScrollComboBox<>(false);
	public CheckButton snapToScaleGrid = new CheckButton("Snap to Scale", true);


	public static final int baseMargin = 5;
	public static int trackScope = 1;


	public int part = 0;
	public int partOrder = 0;
	public static int highlightModeChoice = 3;


	public MidiEditPopup(Section section, int secPartNum, int secPartOrder) {
		super("Edit MIDI Phrase (Graphical)", 14);
		sec = section;
		part = secPartNum;
		partOrder = secPartOrder;
		PhraseNotes values = sec.getPartPhraseNotes().get(part).get(partOrder);
		if (values == null) {
			values = VibeComposerGUI.getAffectedPanels(part).get(partOrder).getCustomMidi();
		}
		values.setCustom(true);

		ScrollComboBox.addAll(
				new String[] { "No Highlight", "Scale/Key", "Chords", "Scale/Key and Chords" },
				highlightMode);
		highlightMode.setSelectedIndex(highlightModeChoice);
		highlightMode.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(ItemEvent e) {
				highlightModeChoice = highlightMode.getSelectedIndex();
				mvea.repaint();
			}
		});

		int vmin = -1 * baseMargin * trackScope;
		int vmax = baseMargin * trackScope;
		if (!values.isEmpty()) {
			vmin += values.stream().map(e -> e.getPitch()).filter(e -> e >= 0).mapToInt(e -> e)
					.min().getAsInt();
			vmax += values.stream().map(e -> e.getPitch()).filter(e -> e >= 0).mapToInt(e -> e)
					.max().getAsInt();
		}
		int min = vmin;
		int max = vmax;
		mvea = new MidiEditArea(min, max, values);
		mvea.setPop(this);
		mvea.setPreferredSize(new Dimension(1500, 600));

		JPanel allPanels = new JPanel();
		allPanels.setLayout(new BoxLayout(allPanels, BoxLayout.Y_AXIS));
		allPanels.setMaximumSize(new Dimension(1500, 750));

		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new GridLayout(0, 4, 0, 0));
		buttonPanel.setPreferredSize(new Dimension(1500, 50));

		buttonPanel.add(VibeComposerGUI.makeButton("???", e -> {
			int size = mvea.getValues().size();
			boolean successRandGenerator = false;
			/*if (butt != null && butt.getRandGenerator() != null) {
				List<Integer> randValues = null;
				try {
					randValues = butt.getRandGenerator().apply(new Object());
				} catch (Exception exc) {
					LG.d("Random generator is not ready!");
				}
				if (randValues != null && !randValues.isEmpty()) {
					mvea.getValues().clear();
					mvea.getValues().addAll(randValues);
					successRandGenerator = true;
				}
			
			}*/
			if (!successRandGenerator) {
				Random rnd = new Random();
				for (int i = 0; i < size; i++) {
					if (mvea.getValues().get(i).getPitch() >= 0) {
						int pitch = rnd
								.nextInt(mvea.max - mvea.min + 1 - trackScope * baseMargin * 2)
								+ mvea.min + baseMargin * trackScope;
						if (snapToScaleGrid.isSelected()) {
							int closestNormalized = MidiUtils
									.getClosestFromList(MidiUtils.MAJ_SCALE, pitch % 12);

							mvea.getValues().get(i).setPitch(12 * (pitch / 12) + closestNormalized);
						} else {
							mvea.getValues().get(i).setPitch(pitch);
						}

					}
				}
			}

			repaintMvea();
		}));

		buttonPanel.add(VibeComposerGUI.makeButton("Apply to Main", e -> {
			mvea.getValues().setCustom(true);
			VibeComposerGUI.getInstList(part).get(partOrder).setCustomMidi(mvea.getValues());
		}));


		JPanel buttonPanel2 = new JPanel();
		buttonPanel2.setLayout(new GridLayout(0, 4, 0, 0));
		buttonPanel2.setPreferredSize(new Dimension(1500, 50));

		buttonPanel2.add(VibeComposerGUI.makeButton("> REMOVE <", e -> {
			mvea.getValues().setCustom(false);
			close();
		}));
		buttonPanel2.add(VibeComposerGUI.makeButton("> CLOSE <", e -> close()));

		JPanel mveaPanel = new JPanel();
		mveaPanel.setPreferredSize(new Dimension(1500, 600));
		mveaPanel.setMinimumSize(new Dimension(1500, 600));
		mveaPanel.add(mvea);

		JPanel textPanel = new JPanel();
		textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.X_AXIS));
		text = new JTextField(values.toStringPitches());
		textPanel.add(text);
		textPanel.add(VibeComposerGUI.makeButton("Apply", e -> {
			if (StringUtils.isNotEmpty(text.getText())) {
				try {
					String[] textSplit = text.getText().split(",");
					List<Integer> nums = new ArrayList<>();
					for (String s : textSplit) {
						nums.add(Integer.valueOf(s));
					}
					for (int i = 0; i < nums.size() && i < mvea.getValues().size(); i++) {
						mvea.getValues().get(i).setPitch(nums.get(i));
					}
					repaintMvea();
				} catch (Exception exc) {
					LG.d("Incorrect text format, cannot convert to list of numbers.");
				}
			}
		}));

		textPanel.add(snapToScaleGrid);
		textPanel.add(new JLabel("  Highlight Mode:"));
		textPanel.add(highlightMode);

		allPanels.add(buttonPanel);
		allPanels.add(buttonPanel2);
		allPanels.add(textPanel);
		allPanels.add(mveaPanel);
		frame.add(allPanels);
		frame.pack();
		frame.setVisible(true);
	}

	public void setup(Section sec) {
		if (!sec.containsPhrase(part, partOrder)) {
			close();
			LG.i("MidiEditPopup cannot be setup - section doesn't contain the part/partOrder!");
			return;
		}

		setSec(sec);
		PhraseNotes values = sec.getPartPhraseNotes().get(part).get(partOrder);

		int vmin = 0;
		int vmax = 0;
		if (!values.isEmpty()) {
			vmin += values.stream().map(e -> e.getPitch()).filter(e -> e >= 0).mapToInt(e -> e)
					.min().getAsInt();
			vmax += values.stream().map(e -> e.getPitch()).filter(e -> e >= 0).mapToInt(e -> e)
					.max().getAsInt();
		}
		mvea.min = Math.min(mvea.min, vmin);
		mvea.max = Math.max(mvea.max, vmax);
		values.setCustom(true);
		mvea.setValues(values);
		repaintMvea();
	}

	void repaintMvea() {
		mvea.repaint();
		text.setText(mvea.getValues().toStringPitches());
	}

	public void setParent(InstPanel parent) {
		this.parent = parent;
	}

	public InstPanel getParent() {
		return parent;
	}

	@Override
	protected void addFrameWindowOperation() {
		frame.addWindowListener(new WindowListener() {

			@Override
			public void windowOpened(WindowEvent e) {
				// Auto-generated method stub

			}

			@Override
			public void windowClosing(WindowEvent e) {

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

	public JTextField getText() {
		return text;
	}

	public void setText(JTextField text) {
		this.text = text;
	}

	public Section getSec() {
		return sec;
	}

	public void setSec(Section sec) {
		this.sec = sec;
	}

}
