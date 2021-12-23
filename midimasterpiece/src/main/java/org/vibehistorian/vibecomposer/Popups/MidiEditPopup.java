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

	public static final int baseMargin = 10;

	public int part = 0;
	public int partOrder = 0;
	public static int highlightModeChoice = 0;


	public MidiEditPopup(Section section, int secPartNum, int secPartOrder) {
		super("Edit MIDI Phrase (Graphical)", 14);
		sec = section;
		part = secPartNum;
		partOrder = secPartOrder;
		PhraseNotes values = sec.getPartPhraseNotes().get(part).get(partOrder);
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

		int vmin = -1 * baseMargin;
		int vmax = baseMargin;
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
		allPanels.setMaximumSize(new Dimension(1500, 700));

		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new GridLayout(0, 4, 0, 0));
		buttonPanel.setPreferredSize(new Dimension(1500, 50));
		/*buttonPanel.add(VibeComposerGUI.makeButton("Add", e -> {
			if (mvea.getValues().size() > 31) {
				return;
			}
			mvea.getValues().add(new PhraseNote(60));
			repaintMvea();
		}));
		buttonPanel.add(VibeComposerGUI.makeButton("Remove", e -> {
			if (mvea.getValues().size() <= 1) {
				return;
			}
			mvea.getValues().remove(mvea.getValues().size() - 1);
			repaintMvea();
		}));*/
		/*buttonPanel.add(VibeComposerGUI.makeButton("Clear", e -> {
			int size = mvea.getValues().size();
			mvea.getValues().clear();
			for (int i = 0; i < size; i++) {
				mvea.getValues().add(new PhraseNote(60));
			}
			repaintMvea();
		}));
		buttonPanel.add(VibeComposerGUI.makeButton("2x", e -> {
			if (mvea.getValues().size() > 16) {
				return;
			}
			mvea.getValues().addAll(mvea.getValues());
			repaintMvea();
		}));
		buttonPanel.add(VibeComposerGUI.makeButton("Dd", e -> {
			if (mvea.getValues().size() > 16) {
				return;
			}
			PhraseNotes ddValues = new PhraseNotes();
			PhraseNotes oldValues = mvea.getValues();
			oldValues.forEach(f -> {
				ddValues.add(f);
				ddValues.add(new PhraseNote(f.toNote()));
			});
			oldValues.clear();
			oldValues.addAll(ddValues);
			repaintMvea();
		}));
		buttonPanel.add(VibeComposerGUI.makeButton("1/2", e -> {
			if (mvea.getValues().size() <= 1) {
				return;
			}
			int toRemain = (mvea.getValues().size() + 1) / 2;
			for (int i = mvea.getValues().size() - 1; i >= toRemain; i--) {
				mvea.getValues().remove(i);
			}
		
			repaintMvea();
		}));*/
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
						int pitch = rnd.nextInt(max - min + 1 - baseMargin * 2) + min + baseMargin;
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

		buttonPanel.add(VibeComposerGUI.makeButton("> OK <", e -> close()));

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
					/*if (nums.size() > mvea.getValues().size()) {
						for (int i = mvea.getValues().size(); i < nums.size(); i++) {
							mvea.getValues().add(new PhraseNote(nums.get(i)));
						}
					} else if (mvea.getValues().size() > nums.size()) {
						for (int i = nums.size(); i < mvea.getValues().size(); i++) {
							mvea.getValues().remove(i);
						}
					}*/
					repaintMvea();
				} catch (Exception exc) {
					LG.d("Incorrect text format, cannot convert to list of numbers.");
				}
			}
		}));

		textPanel.add(snapToScaleGrid);
		textPanel.add(new JLabel("Highlight Mode:"));
		textPanel.add(highlightMode);

		allPanels.add(buttonPanel);
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
				if (parent != null) {
					//parent.setPhraseNotes(mvea.getValues());
				}
				/*if (RandomValueButton.singlePopup != null) {
					if (RandomValueButton.singlePopup.randomNum == randomNum) {
						RandomValueButton.singlePopup = null;
					}
				}*/

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