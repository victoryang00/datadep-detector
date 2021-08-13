package edu.gmu.swe.datadep;

import java.lang.reflect.Field;

public class StaticFieldDependency {
	public Field field; // TODO Is this the static field, or this is the
						// "dependent" field ?
	//
	public String value; // Optional Not necessary
	///
	public int depGen;
	// public String depTestName; // Textual description of the testName ?

	@Override
	public String toString() {
		return field.toString() + ", dependsOn " /* + depTestName */ + "(" + depGen + ")" + ", XML value: \n " + value;
	}
}
