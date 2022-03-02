package org.vibehistorian.vibecomposer;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.vibehistorian.vibecomposer.Components.ScrollComboBox;

public class InstComboBox extends ScrollComboBox<String> {

	private static final long serialVersionUID = -2820153952228324714L;

	public static Set<String> BANNED_INSTS = new HashSet<>();

	private InstUtils.POOL instPool = InstUtils.POOL.ALL;

	public InstUtils.POOL getInstPool() {
		return instPool;
	}

	@Override
	public String getToolTipText() {
		if (super.getToolTipText() == null) {
			return null;
		}
		if (instPool == InstUtils.POOL.DRUM) {
			int pitch = MidiGenerator.mapDrumPitchByCustomMapping(getInstrument(), false);
			putClientProperty(TOOL_TIP_TEXT_KEY,
					(pitch + " / " + MidiUtils.getNoteForPitch(pitch)));
		}

		return super.getToolTipText();
	}

	public void setInstPool(InstUtils.POOL instPool) {
		this.instPool = instPool;
	}

	public void initInstPool(InstUtils.POOL instPool) {
		this.instPool = instPool;
		this.removeAllItems();
		String[] choices = InstUtils.INST_POOLS.get(instPool);
		for (String c : choices) {
			if (!isBanned(c)) {
				this.addItem(c);
			}
		}
	}

	public void changeInstPoolMapping(String[] pool) {
		int index = getSelectedIndex();
		this.removeAllItems();
		for (String c : pool) {
			if (!isBanned(c)) {
				this.addItem(c);
			}
		}
		setSelectedIndex(index);
	}

	public InstUtils.POOL setInstrument(int instrument) {
		if (!instSet(instrument)) {
			initInstPool(InstUtils.POOL.ALL);
			LG.d("Switching to POOL.ALL!");
			if (!instSet(instrument)) {
				throw new IllegalArgumentException(
						"Instrument not found in POOL.ALL: " + instrument);
			}
		}
		return instPool;
	}

	public int getInstrument() {
		return Integer.valueOf(getVal().split(": ")[0].trim());
	}

	public int getRandomInstrument() {
		Random rand = new Random();
		return Integer.valueOf(getItemAt(rand.nextInt(getItemCount())).split(": ")[0].trim());
	}

	private boolean isBanned(String inst) {
		return BANNED_INSTS.contains(inst.split(": ")[0].trim());
	}

	private boolean instSet(int instrument) {
		for (int i = 0; i < this.getItemCount(); i++) {
			int inst = Integer.valueOf(this.getItemAt(i).split(": ")[0].trim());
			if (inst == instrument) {
				setSelectedItem(getItemAt(i));
				discardInteraction();
				return true;
			}
		}
		return false;
	}
}
