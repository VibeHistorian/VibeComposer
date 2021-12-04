package org.vibehistorian.vibecomposer.Popups;

import java.awt.Dimension;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.vibehistorian.vibecomposer.Helpers.MultiValueEditArea;
import org.vibehistorian.vibecomposer.Helpers.RandomIntegerListButton;

public class VisualArrayPopup extends CloseablePopup {

	MultiValueEditArea mvea = null;
	RandomIntegerListButton butt = null;


	public VisualArrayPopup(int min, int max, List<Integer> values) {
		super("Edit Multiple Values (Graphical)", 13);
		mvea = new MultiValueEditArea(min, max, values.size(), values);
		mvea.setPreferredSize(new Dimension(500, 500));
		frame.add(mvea);
		frame.setVisible(true);
		frame.pack();
	}

	public void linkButton(RandomIntegerListButton butt) {
		this.butt = butt;
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
				if (butt != null) {
					butt.setValue(StringUtils.join(mvea.getValues(), ","));
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

}
