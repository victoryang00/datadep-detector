package edu.gmu.swe.datadep;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.HashSet;

import com.thoughtworks.xstream.mapper.Mapper;
import com.thoughtworks.xstream.mapper.MapperWrapper;

import edu.gmu.swe.datadep.struct.WrappedPrimitive;

public class FilteringFieldMapper extends MapperWrapper {

	public FilteringFieldMapper(Mapper parent) {
		super(parent);
	}

	/*
	 * List the packages that shall not Serialized !
	 */
	static HashSet<String> blackListedPackages = new HashSet<String>();

	static {
		// blackListedPackages.add("org.log4j");
		blackListedPackages.add("edu.columbia.cs.psl.phosphor");
	}

	@Override
	public boolean shouldSerializeMember(Class definedIn, String fieldName) {
		if (definedIn.equals(SoftReference.class) && fieldName.equals("timestamp"))
			return false;
		if (definedIn.equals(SoftReference.class) && fieldName.equals("referent"))
			return false;
		if (definedIn.equals(Reference.class) && fieldName.equals("referent"))
			return false;
		if (fieldName.endsWith("__DEPENDENCY_INFO"))
			return false;
		String pkg = definedIn.getPackage().getName();
		for (String s : blackListedPackages)
			if (pkg.startsWith(s)) {
				System.out.println("Bouncing on " + definedIn);
				return false;
			}
		return super.shouldSerializeMember(definedIn, fieldName);
	}

	@Override
	public Class defaultImplementationOf(Class type) {
		if (type.isPrimitive() || type.isAssignableFrom(String.class) || type.isEnum())
			return WrappedPrimitive.class;
		return String.class;
	}
}
