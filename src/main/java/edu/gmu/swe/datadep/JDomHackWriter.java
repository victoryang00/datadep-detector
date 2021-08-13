package edu.gmu.swe.datadep;

import org.jdom2.Element;

import com.thoughtworks.xstream.io.xml.JDom2Writer;

public class JDomHackWriter extends JDom2Writer {
	public Element recentNode;
	private boolean skipValues;

	public JDomHackWriter(Element root, boolean skipValues) {
		super(root);
		this.skipValues = skipValues;
	}

	// @Override
	// public String encodeAttribute(String name) {
	// System.out.println("JDomHackWriter.encodeAttribute() " + name);
	// return super.encodeAttribute(name);
	// }

	@Override
	public void setValue(String text) {
		if (!skipValues) {
			super.setValue(text);
		}
	}

	@Override
	protected Object createNode(String name) {
		// System.out.println("JDomHackWriter.createNode() " + name);
		Object ret = super.createNode(name);
		recentNode = (Element) ret;
		return ret;
	}
}
