package org.vibehistorian.vibecomposer.Components;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Insets;

public class DynamicGridLayout extends GridLayout {
	private static final long serialVersionUID = -6658481515046780575L;
	int hgap;
	int vgap;
	int rows;
	int cols;

	public DynamicGridLayout() {
		this(1, 0, 0, 0);
	}

	public DynamicGridLayout(int rows, int cols) {
		this(rows, cols, 0, 0);
	}

	public DynamicGridLayout(int rows, int cols, int hgap, int vgap) {
		if ((rows == 0) && (cols == 0)) {
			throw new IllegalArgumentException("rows and cols cannot both be zero");
		}
		this.rows = rows;
		this.cols = cols;
		this.hgap = hgap;
		this.vgap = vgap;
	}

	@Override
	public Dimension preferredLayoutSize(Container parent) {
		synchronized (parent.getTreeLock()) {
			Insets insets = parent.getInsets();

			int ncomponents = getVisibleComponents(parent);
			int nrows = rows;
			int ncols = cols;

			if (nrows > 0) {
				ncols = (ncomponents + nrows - 1) / nrows;
			} else {
				nrows = (ncomponents + ncols - 1) / ncols;
			}
			int w = 0;
			int h = 0;
			//  for (int i = 0 ; i < ncomponents ; i++) {
			for (int i = 0; i < parent.getComponentCount(); i++) {
				Component comp = parent.getComponent(i);

				if (!comp.isVisible())
					continue; // added

				Dimension d = comp.getPreferredSize();
				if (w < d.width) {
					w = d.width;
				}
				if (h < d.height) {
					h = d.height;
				}
			}

			Dimension d = new Dimension(insets.left + insets.right + ncols * w + (ncols - 1) * hgap,
					insets.top + insets.bottom + nrows * h + (nrows - 1) * vgap);

			return d;
		}
	}

	@Override
	public Dimension minimumLayoutSize(Container parent) {
		synchronized (parent.getTreeLock()) {
			Insets insets = parent.getInsets();

			int ncomponents = getVisibleComponents(parent);
			int nrows = rows;
			int ncols = cols;

			if (nrows > 0) {
				ncols = (ncomponents + nrows - 1) / nrows;
			} else {
				nrows = (ncomponents + ncols - 1) / ncols;
			}
			int w = 0;
			int h = 0;

			for (int i = 0; i < parent.getComponentCount(); i++) {
				Component comp = parent.getComponent(i);

				if (!comp.isVisible())
					continue; // added

				Dimension d = comp.getMinimumSize();
				if (w < d.width) {
					w = d.width;
				}
				if (h < d.height) {
					h = d.height;
				}
			}

			Dimension d = new Dimension(insets.left + insets.right + ncols * w + (ncols - 1) * hgap,
					insets.top + insets.bottom + nrows * h + (nrows - 1) * vgap);

			return d;
		}
	}

	@Override
	public void layoutContainer(Container parent) {
		synchronized (parent.getTreeLock()) {
			Insets insets = parent.getInsets();

			int ncomponents = getVisibleComponents(parent);
			int nrows = rows;
			int ncols = cols;
			boolean ltr = parent.getComponentOrientation().isLeftToRight();

			if (ncomponents == 0) {
				return;
			}
			if (nrows > 0) {
				ncols = (ncomponents + nrows - 1) / nrows;
			} else {
				nrows = (ncomponents + ncols - 1) / ncols;
			}

			int w = parent.getSize().width - (insets.left + insets.right);
			int h = parent.getSize().height - (insets.top + insets.bottom);
			w = (w - (ncols - 1) * hgap) / ncols;
			h = (h - (nrows - 1) * vgap) / nrows;

			int i = 0;

			if (ltr) {
				for (int r = 0, y = insets.top; r < nrows; r++, y += h + vgap) {
					int c = 0;
					int x = insets.left;

					while (c < ncols) {
						if (i >= parent.getComponentCount())
							break;

						Component component = parent.getComponent(i);

						if (component.isVisible()) {
							parent.getComponent(i).setBounds(x, y, w, h);
							c++;
							x += w + hgap;
						}

						i++;
					}
				}
			}

		}
	}

	private int getVisibleComponents(Container parent) {
		int visible = 0;

		for (Component c : parent.getComponents()) {
			if (c.isVisible())
				visible++;
		}

		return visible;
	}
}