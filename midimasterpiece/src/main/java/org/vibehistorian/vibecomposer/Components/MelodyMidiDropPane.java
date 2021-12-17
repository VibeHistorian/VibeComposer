package org.vibehistorian.vibecomposer.Components;

/*
 * Code adapted from URL: https://stackoverflow.com/a/13597635
 * Original author: MadProgrammer
 */

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.TooManyListenersException;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.apache.commons.lang3.tuple.Pair;
import org.vibehistorian.vibecomposer.JMusicUtilsCustom;
import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.MidiUtils.ScaleMode;
import org.vibehistorian.vibecomposer.OMNI;
import org.vibehistorian.vibecomposer.VibeComposerGUI;

import jm.music.data.Part;
import jm.music.data.Phrase;
import jm.music.data.Score;
import jm.util.Read;

public class MelodyMidiDropPane extends JPanel {

	private static final long serialVersionUID = 6132531225113455208L;

	private DropTarget dropTarget;
	private DropTargetHandler dropTargetHandler;
	private Point dragPoint;

	private boolean dragOver = false;
	private BufferedImage target = null;

	public static Phrase userMelody = null;
	public static Phrase userMelodyCandidate = null;

	public static JLabel message;

	public MelodyMidiDropPane() {
		/*try {
			target = ImageIO.read(new File("target.png"));
		} catch (IOException ex) {
			ex.printStackTrace();
		}*/

		setLayout(new GridBagLayout());
		message = new JLabel("* * Drag'n'Drop MIDI Here * *");
		message.setFont(message.getFont().deriveFont(Font.BOLD, 12));
		add(message);

	}

	@Override
	public Dimension getPreferredSize() {
		return new Dimension(200, 35);
	}

	protected DropTarget getMyDropTarget() {
		if (dropTarget == null) {
			dropTarget = new DropTarget(this, DnDConstants.ACTION_COPY_OR_MOVE, null);
		}
		return dropTarget;
	}

	protected DropTargetHandler getDropTargetHandler() {
		if (dropTargetHandler == null) {
			dropTargetHandler = new DropTargetHandler();
		}
		return dropTargetHandler;
	}

	@Override
	public void addNotify() {
		super.addNotify();
		try {
			getMyDropTarget().addDropTargetListener(getDropTargetHandler());
		} catch (TooManyListenersException ex) {
			ex.printStackTrace();
		}
	}

	@Override
	public void removeNotify() {
		super.removeNotify();
		getMyDropTarget().removeDropTargetListener(getDropTargetHandler());
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		if (dragOver) {
			Graphics2D g2d = (Graphics2D) g.create();
			g2d.setColor(new Color(0, 255, 0, 64));
			g2d.fill(new Rectangle(getWidth(), getHeight()));
			if (dragPoint != null && target != null) {
				int x = dragPoint.x - 12;
				int y = dragPoint.y - 12;
				g2d.drawImage(target, x, y, this);
			}
			g2d.dispose();
		}
	}

	protected void importFiles(final List<File> files) {
		Runnable run = new Runnable() {
			@Override
			public void run() {
				userMelody = null;
				userMelodyCandidate = null;
				if (files == null || files.isEmpty()) {
					message.setText("No file!");
				} else {
					File file = (File) files.get(0);
					if (file.getName().endsWith("mid") || file.getName().endsWith("midi")) {
						message.setText(file.getName());
						Score scr = Read.midiOrJmWithNoMessaging(file);
						LG.d("Score parts: " + scr.getPartList().size());
						Part part = new Part();
						for (int i = 0; i < scr.getPart(0).getPhraseList().size(); i++) {
							Phrase phr = (Phrase) scr.getPart(0).getPhraseList().get(i);
							LG.d(phr.toString());
							phr.setAppend(false);
							//phr.setStartTime(MidiGenerator.START_TIME_DELAY);
							JMusicUtilsCustom.addRestsToRhythmValues(phr);
							part.add(phr);
						}

						JMusicUtilsCustom.consolidate(part);
						//Mod.consolidate(part);
						Phrase userMelodyCandidate = part.getPhrase(0);
						List<Pair<ScaleMode, Integer>> detectionResults = MidiUtils
								.detectKeyAndMode(userMelodyCandidate, null, false);

						if (detectionResults == null) {
							message.setText("Unknown key, skipped!");
							LG.d("Melody uses unknown key, skipped!");
							return;
						}

						//LG.d(userMelody.toString());
						LG.d("Tempo: " + scr.getTempo());

						MelodyMidiDropPane.userMelodyCandidate = userMelodyCandidate;
						VibeComposerGUI.userMelodyScaleModeSelect.removeAllItems();
						VibeComposerGUI.userMelodyScaleModeSelect.addItem(OMNI.EMPTYCOMBO);
						for (Pair<ScaleMode, Integer> p : detectionResults) {
							VibeComposerGUI.userMelodyScaleModeSelect
									.addItem(p.getLeft().toString() + "," + p.getRight());
						}

					} else {
						message.setText("Not MIDI! - " + file.getName());
					}

				}

			}
		};
		SwingUtilities.invokeLater(run);
	}

	protected class DropTargetHandler implements DropTargetListener {

		protected void processDrag(DropTargetDragEvent dtde) {
			if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
				dtde.acceptDrag(DnDConstants.ACTION_COPY);
			} else {
				dtde.rejectDrag();
			}
		}

		@Override
		public void dragEnter(DropTargetDragEvent dtde) {
			processDrag(dtde);
			SwingUtilities.invokeLater(new DragUpdate(true, dtde.getLocation()));
			repaint();
		}

		@Override
		public void dragOver(DropTargetDragEvent dtde) {
			processDrag(dtde);
			SwingUtilities.invokeLater(new DragUpdate(true, dtde.getLocation()));
			repaint();
		}

		@Override
		public void dropActionChanged(DropTargetDragEvent dtde) {
		}

		@Override
		public void dragExit(DropTargetEvent dte) {
			SwingUtilities.invokeLater(new DragUpdate(false, null));
			repaint();
		}

		@Override
		public void drop(DropTargetDropEvent dtde) {

			SwingUtilities.invokeLater(new DragUpdate(false, null));

			Transferable transferable = dtde.getTransferable();
			if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
				dtde.acceptDrop(dtde.getDropAction());
				try {

					List<File> transferData = (List<File>) transferable
							.getTransferData(DataFlavor.javaFileListFlavor);
					if (transferData != null && transferData.size() > 0) {
						importFiles(transferData);
						dtde.dropComplete(true);
					}

				} catch (Exception ex) {
					ex.printStackTrace();
				}
			} else {
				dtde.rejectDrop();
			}
		}
	}

	public class DragUpdate implements Runnable {

		private boolean dragOver;
		private Point dragPoint;

		public DragUpdate(boolean dragOver, Point dragPoint) {
			this.dragOver = dragOver;
			this.dragPoint = dragPoint;
		}

		@Override
		public void run() {
			MelodyMidiDropPane.this.dragOver = dragOver;
			MelodyMidiDropPane.this.dragPoint = dragPoint;
			MelodyMidiDropPane.this.repaint();
		}
	}

}
