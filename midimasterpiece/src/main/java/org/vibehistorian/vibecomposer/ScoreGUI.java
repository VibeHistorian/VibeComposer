/* --------------------
* @author Vibe Historian
* ---------------------

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2 of the License, or any
later version.

This program is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not,
see <https://www.gnu.org/licenses/>.
*/

package org.vibehistorian.vibecomposer;

import org.vibehistorian.vibecomposer.Components.CustomCheckBox;
import org.vibehistorian.vibecomposer.Components.PlayheadRangeSlider;
import org.vibehistorian.vibecomposer.Components.ShowPanelBig;
import org.vibehistorian.vibecomposer.Panels.KnobPanel;
import org.vibehistorian.vibecomposer.Popups.ShowScorePopup;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** Owns score display state, settings, and popup behavior. */
public class ScoreGUI {
	public interface Context extends ShowPanelBig.PlaybackActions {
		JTabbedPane getInstrumentTabPane();
		Component getMainWindowComponent();
	}

	private final Context context;

	public ScoreGUI(Context context) {
		this.context = context;
	}

	public static JScrollPane scoreScrollPane;
	public static ShowPanelBig scorePanel;
	public static KnobPanel transposeScore;
	public static JButton showScore;
	public static ShowScorePopup scorePopup;
	public static JCheckBox highlightScoreNotes;
	public static JCheckBox miniScorePopup;

	public static void saveToConfig(GUIConfig gc) { gc.setTranspose(transposeScore.getInt()); }
	public static void loadFromConfig(GUIConfig gc) { transposeScore.setInt(gc.getTranspose()); }

	public void initScoreSettings(int startY, int anchorSide) {
		JPanel scrollableScorePanel = new JPanel();
		scrollableScorePanel.setLayout(new BoxLayout(scrollableScorePanel, BoxLayout.Y_AXIS));
		scrollableScorePanel.setAutoscrolls(true);
		scoreScrollPane = new JScrollPane() {
			@Override
			public Dimension getPreferredSize() {
				return UITheme.scrollPaneDimension;
			}
		};
		scoreScrollPane.setViewportView(scrollableScorePanel);
		scoreScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
		scoreScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scoreScrollPane.getVerticalScrollBar().setUnitIncrement(16);
		context.getInstrumentTabPane().addTab("Score", scoreScrollPane);
	}

	public KnobPanel createTransposeControl() {
		transposeScore = new KnobPanel("Global Transpose<br>(Key)", 0, -24, 24);
		return transposeScore;
	}

	public JButton createShowScoreButton() {
		showScore = new JButton("Show Score Tab");
		showScore.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				toggleShowScorePopup();
			}
		});
		return showScore;
	}

	public static void initDisplaySettings(JPanel displayStylePanel) {
		highlightScoreNotes = new CustomCheckBox("Highlight Score Notes (-Perf)", true);
		miniScorePopup = new CustomCheckBox("Mini Score Popup", true);
		displayStylePanel.add(highlightScoreNotes);
		displayStylePanel.add(miniScorePopup);
	}

	public void pianoRoll() {
		if (MidiGenerator.LAST_SCORES.isEmpty()) {
			return;
		}
		if (scorePanel == null) {
			scorePanel = new ShowPanelBig(context);
			((JPanel) scoreScrollPane.getViewport().getView()).add(scorePanel);
		}
		ShowPanelBig.scoreBox.setSelectedIndex(0);
		scorePanel.setScore();
		scoreScrollPane.repaint();
	}

	public PlayheadRangeSlider createPlayheadRangeSlider(JTabbedPane instrumentTabPane) {
		return new PlayheadRangeSlider(instrumentTabPane, this::repaintScoreMinimum,
				() -> highlightScoreNotes.isSelected());
	}

	private void repaintScoreMinimum() {
		if (scorePanel != null) {
			scorePanel.repaintMinimum();
		}
	}

	public void repaintScoreDisplay() {
		if (scoreScrollPane != null) {
			scoreScrollPane.repaint();
		}
	}

	public void toggleShowScorePopup() {
		JTabbedPane instrumentTabPane = context.getInstrumentTabPane();
		if (scorePanel != null) {
			if (instrumentTabPane.getComponentCount() == 8) {
				instrumentTabPane.remove(scoreScrollPane);
				if (miniScorePopup.isSelected()) {
					ShowPanelBig.beatWidthBases = ShowPanelBig.beatWidthBasesSmall;
					ShowPanelBig.beatWidthBase = ShowPanelBig.beatWidthBases
							.get(ShowPanelBig.beatWidthBaseIndex);
					scorePanel.updatePanelHeight(300);
					scorePanel.getShowArea().setNoteHeight(4);
					scorePanel.setScore();
					scorePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
					scoreScrollPane.repaint();
					SwingUtilities.invokeLater(() ->
							ShowPanelBig.zoomIn(ShowPanelBig.areaScrollPane, new Point(0, 0), 0.0, 0.0));
				}
				scorePopup = new ShowScorePopup(scoreScrollPane, instrumentTabPane,
						context.getMainWindowComponent(), miniScorePopup.isSelected(),
						this::restoreScoreAfterPopup);
			} else {
				cleanup();
				if (instrumentTabPane.getComponentCount() < 8) {
					instrumentTabPane.add(scoreScrollPane, 7);
					instrumentTabPane.setTitleAt(7, " Score ");
				}
			}
		}
	}

	private void restoreScoreAfterPopup() {
		ShowPanelBig.beatWidthBases = ShowPanelBig.beatWidthBasesBig;
		ShowPanelBig.beatWidthBase = ShowPanelBig.beatWidthBases
				.get(ShowPanelBig.beatWidthBaseIndex);
		scorePanel.updatePanelHeight(UITheme.scrollPaneDimension.height);
		scorePanel.getShowArea().setNoteHeight(7);
		scorePanel.setScore();
		scoreScrollPane.repaint();
		scorePopup = null;
		SwingUtilities.invokeLater(() ->
				ShowPanelBig.zoomIn(ShowPanelBig.areaScrollPane, new Point(0, 300), 0.0,
						(7 / 5.0) - 1.0));
	}

	public void cleanup() {
		if (scorePopup != null) {
			scorePopup.close();
			scorePopup = null;
		}
	}
}
