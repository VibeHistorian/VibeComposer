package org.vibehistorian.midimasterpiece.midigenerator.Panels;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "arpSettings")
@XmlType(propOrder = {})
public class ArpGenSettings extends InstGenSettings {

	public ArpGenSettings() {
		super();
	}
}
