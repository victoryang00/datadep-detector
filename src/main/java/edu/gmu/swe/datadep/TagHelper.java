package edu.gmu.swe.datadep;

import edu.gmu.swe.datadep.struct.WrappedPrimitive;

public final class TagHelper {
	public static int engaged = 0;

	private static native DependencyInfo _getTag(Object obj);

	private static native void _setTag(Object obj, DependencyInfo tag);

	public static DependencyInfo getOrFetchTag(Object obj) {

		if (obj instanceof MockedClass) {
			System.out.println("WARNING: TagHelper.getOrFetchTag() : " + obj + " instace of Mocked Class !");
		}

		if (obj instanceof DependencyInstrumented)
			return ((DependencyInstrumented) obj).getDEPENDENCY_INFO();
		else if (obj instanceof DependencyInfo)
			return (DependencyInfo) obj;
		/* We need to check for null since String objects might be null ? */
		else if ((obj instanceof WrappedPrimitive) ) // && obj != null) -> This might raise NPE ?
			return ((WrappedPrimitive) obj).inf;
		
		return getOrInitTag(obj);
	}

	public static DependencyInfo getOrInitTag(Object obj) {
		if (engaged == 0)
			throw new IllegalStateException();
		
		// Not sure what this does if obj is null ...
		DependencyInfo ret = _getTag(obj);

		if (ret == null) {
			ret = new DependencyInfo();
			setTag(obj, ret);
		}

		return ret;
	}

	public static DependencyInfo getTag(Object obj) {
		if (engaged == 0)
			throw new IllegalStateException();
		return _getTag(obj);
	}

	public static void setTag(Object obj, DependencyInfo tag) {
		if (engaged == 0)
			throw new IllegalStateException();
		_setTag(obj, tag);
	}
}
