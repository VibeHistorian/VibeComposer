package org.vibehistorian.vibecomposer;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import javax.swing.JComboBox;

import org.vibehistorian.vibecomposer.MidiUtils.POOL;

public class InstComboBox extends JComboBox<String> {

	private static final long serialVersionUID = -2820153952228324714L;

	public static Set<String> BANNED_INSTS = new HashSet<>();

	private MidiUtils.POOL instPool = POOL.ALL;

	public MidiUtils.POOL getInstPool() {
		return instPool;
	}

	public void setInstPool(MidiUtils.POOL instPool) {
		this.instPool = instPool;
	}

	public void initInstPool(MidiUtils.POOL instPool) {
		this.instPool = instPool;
		this.removeAllItems();
		String[] choices = MidiUtils.INST_POOLS.get(instPool);
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

	public POOL setInstrument(int instrument) {
		if (!instSet(instrument)) {
			initInstPool(POOL.ALL);
			System.out.println("Switching to POOL.ALL!");
			if (!instSet(instrument)) {
				throw new IllegalArgumentException(
						"Instrument not found in POOL.ALL: " + instrument);
			}
		}
		return instPool;
	}

	public int getInstrument() {
		return Integer.valueOf(getItemAt(getSelectedIndex()).split(" = ")[1].trim());
	}

	public int getRandomInstrument() {
		Random rand = new Random();
		return Integer.valueOf(getItemAt(rand.nextInt(getItemCount())).split(" = ")[1].trim());
	}

	private boolean isBanned(String inst) {
		return BANNED_INSTS.contains(inst.split(" = ")[1].trim());
	}

	private boolean instSet(int instrument) {
		for (int i = 0; i < this.getItemCount(); i++) {
			int inst = Integer.valueOf(this.getItemAt(i).split(" = ")[1].trim());
			if (inst == instrument) {
				setSelectedIndex(i);
				return true;
			}
		}
		return false;
	}
}
