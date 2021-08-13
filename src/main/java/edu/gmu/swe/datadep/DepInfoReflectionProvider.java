package edu.gmu.swe.datadep;

import java.lang.reflect.Field;
import java.util.Iterator;

import com.thoughtworks.xstream.converters.reflection.ObjectAccessException;
import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;

import edu.gmu.swe.datadep.struct.WrappedPrimitive;

// Not sure when this is called
public class DepInfoReflectionProvider extends PureJavaReflectionProvider {
	@Override
	public void visitSerializableFields(Object object, Visitor visitor) {
		for (Iterator iterator = fieldDictionary.fieldsFor(object.getClass()); iterator.hasNext();) {
			Field field = (Field) iterator.next();
			if (!fieldModifiersSupported(field)) {
				continue;
			}
			// if(field.getClass().getPackage().getName().contains("java.lang"))
			// continue;

			validateFieldAccess(field);
			try {
				Object value = field.get(object);
				if (field.getType().isPrimitive() || field.getType().isAssignableFrom(String.class)
						|| field.getType().isEnum()) {

					Field depField = field.getDeclaringClass().getDeclaredField(field.getName() + "__DEPENDENCY_INFO");
					depField.setAccessible(true);
					DependencyInfo dep = (DependencyInfo) depField.get(object);
					value = new WrappedPrimitive(value, dep);
				}
				visitor.visit(field.getName(), field.getType(), field.getDeclaringClass(), value);
			} catch (IllegalArgumentException e) {
				throw new ObjectAccessException("Could not get field " + field.getClass() + "." + field.getName(), e);
			} catch (IllegalAccessException e) {
				throw new ObjectAccessException("Could not get field " + field.getClass() + "." + field.getName(), e);
			} catch (NoSuchFieldException e) {
				// TODO Not sure why this happens for joda-time but it is
				// annoying
				// System.out
				// .println("[WARNING] Could not get dep info field " +
				// field.getClass() + "." + field.getName());
				continue;
				// throw new ObjectAccessException("Could not get dep info field
				// " + field.getClass() + "." + field.getName(), e);
			} catch (SecurityException e) {
				throw new ObjectAccessException(
						"Could not get dep info field " + field.getClass() + "." + field.getName(), e);
			}
		}
	}
}
