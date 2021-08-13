package edu.gmu.swe.datadep.struct;

import edu.gmu.swe.datadep.DependencyInfo;

public class WrappedPrimitive {
	public WrappedPrimitive(Object value, DependencyInfo dep) {
		this.prim = value;
		this.inf = dep;
	}

	// This flag distinguishes true primitives (int, long), from synthetic ones
	// (Enum, Strings, etc)
	private boolean isTruePrimitive = true;

	public boolean isTruePrimitive() {
		return isTruePrimitive;
	}

	public void setTruePrimitive(boolean isTruePrimitive) {
		this.isTruePrimitive = isTruePrimitive;
	}

	// Make those private?
	public Object prim;
	public DependencyInfo inf;

	@Override
	public String toString() {
		if (prim == null)
			return null;
		return prim.toString();
	}
}
