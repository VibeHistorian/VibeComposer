package org.vibehistorian.midimasterpiece.midigenerator;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;

@XmlType(name = "chordSpanFill")
@XmlEnum
public enum ChordSpanFill {
	ALL, ODD, EVEN;
}
