package org.vibehistorian.vibecomposer.Panels;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "arpSettings")
@XmlType(propOrder = {})
public class ArpGenSettings extends InstGenSettings {

	public ArpGenSettings() {
		super();
	}
}
