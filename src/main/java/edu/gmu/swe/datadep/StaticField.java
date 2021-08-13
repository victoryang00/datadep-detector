package edu.gmu.swe.datadep;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.filter.Filters;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;

public class StaticField implements Serializable {

	private static final long serialVersionUID = 1L;
	private static final boolean debug = Boolean.getBoolean("debug");

	private boolean conflict;
	public Element value;
	public int dependsOn;
	public transient Field field;

	private void writeObject(ObjectOutputStream o) throws IOException {

		o.defaultWriteObject();
		o.writeObject(field.getDeclaringClass());
		o.writeObject(field.getName());
	}

	private void readObject(ObjectInputStream o) throws IOException, ClassNotFoundException {

		o.defaultReadObject();
		Class<?> fieldClass = (Class<?>) o.readObject();
		String fieldName = (String) o.readObject();
		try {
			field = fieldClass.getDeclaredField(fieldName);
		} catch (NoSuchFieldException e) {
			// TODO Auto-generated catch block
			// e.printStackTrace();
			System.out.println("WARNING StaticField.readObject() " + e.getMessage());
		} catch (SecurityException e) {
			// TODO Auto-generated catch block
			// e.printStackTrace();
			System.out.println("WARNING StaticField.readObject() " + e.getMessage());
		}
	}

	public StaticField(Field f) {
		this.field = f;
	}

	public boolean isConflict() {
		return conflict;
	}

	public void markConflictAndSerialize(int writeGen) {

		boolean previousValue = DependencyInfo.IN_CAPTURE;
		try {
			if (debug) {
				System.out.println("StaticField.markConflictAndSerialize() SF " + field.getDeclaringClass() + "."
						+ field.getName() + " Write Gen " + writeGen);
				System.out.println("StaticField.markConflictAndSerialize() Disable COLLECTION");
			}

			DependencyInfo.IN_CAPTURE = true;
			conflict = true;
			// Update the write of the first conflicting test
			if (writeGen > dependsOn) {
				dependsOn = writeGen;
			}
			try {

				// TODO Here we can prevent SF to be stored, or at least we can
				// ONLY
				// store the relevant parts, i.e., dependsOn
				// but this might be tricky since the dependsOn might be nested
				// down
				// somewhere !

				field.setAccessible(true);

				//
				value = HeapWalker.serialize(
						// How is this treated ?!
						field.get(null)); // This invoke the shared XStream
											// stuff
											// which is configured to
			} catch (IllegalArgumentException | IllegalAccessException e) {
				e.printStackTrace();
			}

			// if (value == null) {
			// System.out.println(
			// "StaticField.markConflictAndSerialize() Value is null for " +
			// field +
			// " in write gen " + writeGen);
			// }
		} finally {
			DependencyInfo.IN_CAPTURE = previousValue;
			if (debug) {
				System.out.println(
						"StaticField.markConflictAndSerialize() Reset INCAPTURE to " + DependencyInfo.IN_CAPTURE);
			}
		}
	}

	public String getValue() {
		StringWriter sw = new StringWriter();
		XMLOutputter out = new XMLOutputter();

		try {
			if (value != null && value.getContent().size() > 0) {
				Element e = (Element) value.getContent().get(0);
				out.output(new Document(e.detach()), sw);
				out.setFormat(Format.getPrettyFormat());
			} else {
				if (value == null) {
					System.out.println("WARN StaticField.getValue() Null value for " + this);
				} else {
					System.out.println("WARN StaticField.getValue() Empty value for " + this);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return sw.toString();
	}

	// Probably this can be done in a smarte way directly elaborating Element
	public static List<Entry<String, Integer>> getDependencies(String xmlValue) {
		List<Entry<String, Integer>> deps = new ArrayList<Entry<String, Integer>>();
		if (xmlValue == null || xmlValue.trim().equals("")) {
			return deps;
		}
		SAXBuilder builder = new SAXBuilder();
		try {
			Document document = (Document) builder.build(new StringReader(xmlValue));

			// Select only elements with the dependsOnId attribute
			String query = "//*[@dependsOnId]";
			XPathExpression<Element> xpe = XPathFactory.instance().compile(query, Filters.element());
			for (Element urle : xpe.evaluate(document)) {

				Integer depAttrIds = Integer.parseInt(urle.getAttribute("dependsOnId").getValue());

				Entry<String, Integer> dep = new AbstractMap.SimpleEntry<String, Integer>(urle.getName(), depAttrIds);

				deps.add(dep);
			}

		} catch (IOException io) {
			System.out.println(io.getMessage());
		} catch (JDOMException jdomex) {
			System.out.println(jdomex.getMessage());
		}
		return deps;
	}

	@Override
	public String toString() {
		return "StaticField [field=" + field + ", conflict=" + conflict + ", value="
				+ ((value != null && value.getContent() != null && value.getContent().size() > 0) ? "present" : "null")
				+ ", dependsOn=" + dependsOn + "]";
	}

	public void clearConflict() {
		conflict = false;
		value = null;
		dependsOn = 0;
	}

}
