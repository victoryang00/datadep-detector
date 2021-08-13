package edu.gmu.swe.datadep.xstream;

import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.Hashtable;
import java.util.Map;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.ConverterLookup;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.core.ReferenceByXPathMarshaller;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.mapper.Mapper;

import edu.gmu.swe.datadep.DependencyInfo;
import edu.gmu.swe.datadep.HeapWalker;
import edu.gmu.swe.datadep.JDomHackWriter;
import edu.gmu.swe.datadep.TagHelper;
import edu.gmu.swe.datadep.struct.WrappedPrimitive;

/*
 * 
 * Still not sure how/when this is invoked ..
 *
 */
public class ReferenceByXPathWithDependencysMarshaller extends ReferenceByXPathMarshaller {

	private static final boolean debug = Boolean.getBoolean("debug");

	public ReferenceByXPathWithDependencysMarshaller(HierarchicalStreamWriter writer, ConverterLookup converterLookup,
			Mapper mapper, int mode) {
		super(writer, converterLookup, mapper, mode);
	}

	@Override
	public void convert(Object item, final Converter converter) {

		super.convert(item, new Converter() {
			@Override
			public boolean canConvert(Class type) {
				return converter.canConvert(type);
			}

			@Override
			public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {

				if (source != null) {
					if (HeapWalker.CAPTURE_TAINTS) {
					} else {

						// This actually can return a null inf !
						DependencyInfo inf = TagHelper.getOrFetchTag(source);
						//
						if (source instanceof WrappedPrimitive) {
							inf = ((WrappedPrimitive) source).inf;
						}

						if (inf != null && inf.isConflict()) {
							//
							if (HeapWalker.testNumToTestClass.get(inf.getWriteGen()) == null) {
								System.out.println("FOUND NULL RBXPath " + inf.getWriteGen() + " "
										+ HeapWalker.testNumToTestClass.size());
							} else {

								if (debug) {
									System.out.println("ReferenceByXPathWithDependencysMarshaller: found CONFLICT for "
											+ inf.printMe() + " " + inf.getWriteGen());
								}
								///
								writer.addAttribute("dependsOn", HeapWalker.testNumToTestClass.get(inf.getWriteGen())
										+ "." + HeapWalker.testNumToMethod.get(inf.getWriteGen()));
								writer.addAttribute("dependsOnId", "" + inf.getWriteGen());

								writer.addAttribute("setBy", "ReferenceByXPathWithDependencysMarshaller");
							}
						}

						// Special code to treat Map... probably it should be
						// collections in general
						// It does not work for:
						/*
						 * com.google.common.collect.RegularImmutableMap
						 * java.util.concurrent.ConcurrentHashMap
						 * java.util.LinkedHashMap java.util.Properties
						 */
						if (source instanceof Map) {
							try {
								Map m = (Map) source;
								// Field f =
								// source.getClass().getDeclaredField("size");
								Field finf;
								if (source.getClass().equals(Hashtable.class)
										|| source.getClass().isAssignableFrom(Hashtable.class)) {
									finf = source.getClass().getDeclaredField("count__DEPENDENCY_INFO");
								} else {
									try {
										finf = source.getClass().getDeclaredField("size__DEPENDENCY_INFO");
									} catch (NoSuchFieldException e) {
										System.out.println("NoSuchFieldException for map type " + source.getClass());
										finf = null;
									}
								}
								// f.setAccessible(true);

								if (finf != null) {
									finf.setAccessible(true);
									writer.addAttribute("size", "" + m.size());
									//
									inf = (DependencyInfo) finf.get(source);
									if (inf != null && inf.isConflict()) {
										if (HeapWalker.testNumToTestClass.get(inf.getWriteGen()) == null) {
											System.out.println("FOUND NULL SDO " + inf.getWriteGen() + " "
													+ HeapWalker.testNumToTestClass.size());
										} else {
											writer.addAttribute("size_dependsOn",
													HeapWalker.testNumToTestClass.get(inf.getWriteGen()) + "."
															+ HeapWalker.testNumToMethod.get(inf.getWriteGen()));
											writer.addAttribute("size_dependsOnId", "" + inf.getWriteGen());
											writer.addAttribute("setBy", "ReferenceByXPathWithDependencysMarshaller");
										}
									}
								}
							} catch (NoSuchFieldException e) {
								System.err.println("Source " + source.getClass());
								e.printStackTrace();
							} catch (SecurityException e) {
								e.printStackTrace();
							} catch (IllegalArgumentException e) {
								e.printStackTrace();
							} catch (IllegalAccessException e) {
								e.printStackTrace();
							}
						}

					}
				} // Source == null ?

				// FIXME ALessio Commented this out ..
				// TODO Not sure what is doing here ...
				// Why this does not fail for String == null ?
				// if (source instanceof String) {
				// source = ((String) source).trim();
				// }
				// if (source instanceof char[]) {
				// source = new String((char[]) source).trim().toCharArray();
				// }

				// Propagate the marshalling to the original object, will this
				// "create" all the other marshalling objects, such that we can
				// eventually attach the last generated one with
				converter.marshal(source, writer, context);

				// At this point the marshalling of all the objects is done
				// but what's missing ?

				if (source != null) {

					//
					// System.out.println("ReferenceByXPathWithDependencysMarshaller
					// Source class: " + source.getClass());

					JDomHackWriter wr = (JDomHackWriter) writer.underlyingWriter();
					//
					DependencyInfo inf = TagHelper.getOrFetchTag(source);
					//
					if (source instanceof WrappedPrimitive) {
						WrappedPrimitive wP = (WrappedPrimitive) source;

						if (inf != null) {///
							// String prettyPrint = getValue(wr.recentNode);
							// System.out.println("ReferenceByXPathWithDependencysMarshaller:
							// Recent node for " + wP.prim
							// + " " + prettyPrint + " for " + inf + " source is
							// " + source);
							inf.setXmlEl(wr.recentNode);
						}
					}
				}
			}

			private String getValue(Element value) {
				StringWriter sw = new StringWriter();
				XMLOutputter out = new XMLOutputter();

				try {
					if (value != null && value.getContent().size() > 0) {
						Element e = (Element) value.getContent().get(0);
						out.output(new Document(e.detach()), sw);
						out.setFormat(Format.getPrettyFormat());
					}
				} catch (IOException e) {
					e.printStackTrace();
				}

				return sw.toString();
			}

			@Override
			public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
				return converter.unmarshal(reader, context);
			}
		});
	}
}
