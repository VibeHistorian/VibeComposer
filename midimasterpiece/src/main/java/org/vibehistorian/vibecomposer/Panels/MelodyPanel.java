package org.vibehistorian.vibecomposer.Panels;

import java.awt.event.ActionListener;

import javax.swing.JLabel;

import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.Parts.InstPart;
import org.vibehistorian.vibecomposer.Parts.MelodyPart;

public class MelodyPanel extends InstPanel {

	private static final long serialVersionUID = -7861296600641561431L;

	public void initComponents(ActionListener l) {
		MidiUtils.addAllToJComboBox(new String[] { "1" }, midiChannel);
		midiChannel.setSelectedItem("1");
		instrument.initInstPool(instPool);
		setInstrument(8);
		initDefaults(l);
		volSlider.setValue(80);
		setVelocityMin(80);
		setVelocityMax(105);
		this.add(volSlider);
		/*this.add(new JLabel("#"));
		this.add(panelOrder);*/
		this.add(new JLabel("#"));
		this.add(panelOrder);
		soloMuter = new SoloMuter(0, SoloMuter.Type.SINGLE);
		addDefaultInstrumentControls();

		this.add(minMaxVelSlider);

		this.add(transpose);

		this.add(minMaxVelSlider);

		pauseChance.setInt(0);
		this.add(pauseChance);

		this.add(swingPercent);

		this.add(patternSeedLabel);
		this.add(patternSeed);

		this.add(new JLabel("Midi ch.: 1"));
		setPanelOrder(1);
		
	}

	public MelodyPanel(ActionListener l) {
		setPartClass(MelodyPart.class);
		initComponents(l);
	}


	public MelodyPart toMelodyPart(int lastRandomSeed) {
		MelodyPart part = new MelodyPart();
		part.setFromPanel(this, lastRandomSeed);
		part.setOrder(getPanelOrder());
		return part;
	}

	public void setFromInstPart(InstPart p) {
		MelodyPart part = (MelodyPart) p;
		setDefaultsFromInstPart(part);
		setPanelOrder(part.getOrder());
	}

	@Override
	public InstPart toInstPart(int lastRandomSeed) {
		return toMelodyPart(lastRandomSeed);
	}
}
