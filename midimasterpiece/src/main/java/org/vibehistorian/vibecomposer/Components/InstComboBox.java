package org.vibehistorian.vibecomposer.Components;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.vibehistorian.vibecomposer.InstUtils;
import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.MidiGenerator;
import org.vibehistorian.vibecomposer.MidiUtils;

public class InstComboBox extends ScrollComboBox<String> {

	private static final long serialVersionUID = -2820153952228324714L;

	public static Set<String> BANNED_INSTS = new HashSet<>();

	private InstUtils.POOL instPool = InstUtils.POOL.ALL;

	public InstUtils.POOL getInstPool() {
		return instPool;
	}

	public void setInstPool(InstUtils.POOL instPool) {
		if (!isEnabled()) {
			return;
		}
		this.instPool = instPool;
	}

	public void initInstPool(InstUtils.POOL instPool) {
		if (!isEnabled()) {
			return;
		}
		this.instPool = instPool;
		this.removeAllItems();
		String[] choices = InstUtils.INST_POOLS.get(instPool);
		for (String c : choices) {
			if (!isBanned(c)) {
				this.addItem(c);
			}
		}
		box().setTooltipFunc(e -> {
			if (box().superToolTipText() == null) {
				return null;
			}
			if (instPool == InstUtils.POOL.DRUM) {
				int pitch = MidiGenerator.mapDrumPitchByCustomMapping(getInstrument(), false);
				box().putClientProperty(TOOL_TIP_TEXT_KEY,
						(pitch + " / " + MidiUtils.pitchToString(pitch)));
			}

			return box().superToolTipText();
		});
	}

	public void changeInstPoolMapping(String[] pool) {
		if (!isEnabled()) {
			return;
		}
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
		if (!isEnabled()) {
			return instPool;
		}
		if (!instSet(instrument)) {
			initInstPool(InstUtils.POOL.ALL);
			LG.d("Switching to POOL.ALL!");
			if (!instSet(instrument)) {
				LG.e("Instrument not found in POOL.ALL: " + instrument);
				setInstrument(getRandomInstrument());
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
				setValRaw(getItemAt(i));
				discardInteraction();
				return true;
			}
		}
		return false;
	}
}
