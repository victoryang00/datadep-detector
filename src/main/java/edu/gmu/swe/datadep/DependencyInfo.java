package edu.gmu.swe.datadep;

import java.io.Serializable;

import org.jdom2.Element;

public final class DependencyInfo implements Serializable {

	private static final long serialVersionUID = 1L;

	public static int CURRENT_TEST_COUNT = 1;

	private boolean ignored;

	private boolean logMe;

	private int crawledGen;
	private int writeGen;
	// private int readGen; // Will this overflow the memory ?!

	private boolean conflict;

	//
	private Element xmlEl;
	StaticField[] fields;

	public void setXmlEl(Element xmlEl) {
		// if (logMe) {
		// System.out.println(this.logMeName + " set XML from " + this.xmlEl + "
		// to " + xmlEl);
		// }
		this.xmlEl = xmlEl;
	}

	public Element getXmlEl() {
		return xmlEl;
	}

	// Note that this is the value at the moment of the conflict detection !
	private String value;

	public DependencyInfo() {

	}

	private StackTraceElement[] ex;

	private String logMeName;

	public int getCrawledGen() {
		return crawledGen;
	}

	public void setCrawledGen() {
		this.crawledGen = CURRENT_TEST_COUNT;
	}

	public boolean isConflict() {
		if (logMe && conflict) {
			System.out.println(this.logMeName + " is in Conflict !");
		}
		return conflict;
	}

	public int getWriteGen() {
		return writeGen;
	}

	public boolean isIgnored() {
		return ignored;
	}

	public void setIgnored(boolean ignored) {
		this.ignored = ignored;
	}

	public void logMe(String name) {
		this.logMe = true;
		this.logMeName = name;
		System.out.println("DependencyInfo.logMe() Enabled for " + name);
	}

	public static boolean IN_CAPTURE = false;

	// Code under test
	private void accumulate() {
		String value = "";
		if (xmlEl.getAttribute("dependsOn") != null) {
			value = xmlEl.getAttribute("dependsOn").getValue();
			value = value + ",";
		}
		value = value + HeapWalker.testNumToTestClass.get(getWriteGen()) + "."
				+ HeapWalker.testNumToMethod.get(getWriteGen());
		xmlEl.setAttribute("dependsOn", value);
		//
		String type = "";
		if (xmlEl.getAttribute("dependsOnType") != null) {
			type = xmlEl.getAttribute("dependsOnType").getValue();
			type = type + ",";
		}
		type = type + "W";
		xmlEl.setAttribute("dependsOnType", type);
	}

	/*
	 * This updates the writeGen no matter what. That's why we use the finally
	 * clause
	 */
	public void write() {

		try {
			///

			if (IN_CAPTURE) {
				return;
			}

			if (ignored) {
				return;
			}

			if (logMe) {
				System.out.println(this.logMeName + " write() during test " + CURRENT_TEST_COUNT);
				/// This is the only way to really debug this code
				// Exception ex = new RuntimeException();
				// ex.printStackTrace();
			}

			if (conflict) {
				if (logMe) {
					System.out.println(this.logMeName + " Already in conflict");
				}
			} else if (writeGen != 0 && writeGen != CURRENT_TEST_COUNT) {
				// This sets the conflict flag
				handleTheConflict("DependencyInfo-Write");
				if (logMe) {
					System.out.println(this.logMeName + " write() Conflict and Last written " + writeGen);
				}
			} else {
				if (logMe) {
					System.out.println(this.logMeName + " write() No conflict Last written " + writeGen);
				}
			}
			writeGen = CURRENT_TEST_COUNT;
		} finally {
			if (logMe) {
				System.out.println(this.logMeName + " After invoking write(): IN_CAPTURE = " + IN_CAPTURE
						+ " ignored = " + ignored + " writeGen " + writeGen + " conflict " + conflict);
			}
		}
	}

	public void read() {

		try {
			if (IN_CAPTURE) {
				return;
			}

			if (ignored) {
				return;
			}

			if (logMe) {
				System.out.println(
						this.logMeName + " read() during test " + CURRENT_TEST_COUNT + " last written " + writeGen);
			}
			if (conflict) {
				if (logMe) {
					System.out.println("DependencyInfo.read() Already in conflict " + this.logMeName);
				}
				return;
			} else if (writeGen != 0 && writeGen != CURRENT_TEST_COUNT) {
				handleTheConflict("DependencyInfo-Read");
			}
		} finally {
			if (logMe) {
				System.out.println(this.logMeName + " After invoking read (): IN_CAPTURE = " + IN_CAPTURE
						+ " ignored = " + ignored + " writeGen " + writeGen + " conflict " + conflict);
			}
		}
	}

	private void handleTheConflict(String msg) {
		conflict = true;
		if (logMe) {
			System.out.println(">>>>> " + this.logMeName + " " + msg + ": Conflict ! ");
		}
		// Why this should be repeated for all the sf ?
		if (xmlEl != null) {
			if (HeapWalker.testNumToTestClass.get(getWriteGen()) == null) {
				System.out.println("FOUND NULL DI " + getWriteGen() + " " + HeapWalker.testNumToTestClass.size());
			} else {
				xmlEl.setAttribute("dependsOn", HeapWalker.testNumToTestClass.get(getWriteGen()) + "."
						+ HeapWalker.testNumToMethod.get(getWriteGen()));
				xmlEl.setAttribute("dependsOnId", "" + getWriteGen());
				xmlEl.setAttribute("setBy", msg + writeGen);
			}
		}
		// Snag the value of the static fields at the time of the conflict,
		if (fields != null) {
			for (StaticField sf : fields) {
				if (sf != null) {
					if (sf.isConflict()) {
						// TODO(gyori): The xmlEl is somehow null. When can
						// this be null?
					} else {
						sf.markConflictAndSerialize(writeGen);
					}
				}
			}

		}
	}

	public static void write(Object obj) {

		if (obj instanceof MockedClass) {
			return;
		} else if (obj instanceof DependencyInstrumented) {
			((DependencyInstrumented) obj).getDEPENDENCY_INFO().write();
		} else if (obj instanceof DependencyInfo) {
			((DependencyInfo) obj).write();
		} else if (obj != null) {
			TagHelper.getOrInitTag(obj).write();
		}
	}

	public static void read(Object obj) {
		if (obj instanceof MockedClass) {
			return;
		} else if (obj instanceof DependencyInstrumented) {
			((DependencyInstrumented) obj).getDEPENDENCY_INFO().read();
		} else if (obj instanceof DependencyInfo) {
			((DependencyInfo) obj).read();
		} else if (obj != null) {
			TagHelper.getOrInitTag(obj).read();
		}
	}

	public void clearConflict() {
		if (logMe) {
			System.out.println(">>>>> " + this.logMeName + " clearConflict()");
		}
		this.conflict = false;
	}

	public void clearSFs() {
		if (fields != null)
			for (int i = 0; i < fields.length; i++)
				fields[i] = null;
	}

	public String printMe() {
		if (logMe)
			return this.logMeName;
		else
			return "";
	}
}
