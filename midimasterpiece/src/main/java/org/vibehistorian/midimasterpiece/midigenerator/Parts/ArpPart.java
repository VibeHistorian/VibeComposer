package org.vibehistorian.midimasterpiece.midigenerator.Parts;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "arpPart")
@XmlType(propOrder = {})
public class ArpPart extends InstPart {
	
	public ArpPart() {
		
	}
	
	@XmlAttribute
	public int getOrder() {
		return order;
	}
	
	public void setOrder(int order) {
		this.order = order;
	}
	
}
