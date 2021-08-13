package edu.gmu.swe.datadep;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Properties;
import java.util.Scanner;
import java.util.Set;

import org.jdom2.Element;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.ConverterLookup;
import com.thoughtworks.xstream.core.ReferenceByXPathMarshallingStrategy;
import com.thoughtworks.xstream.core.TreeMarshaller;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.mapper.Mapper;
import com.thoughtworks.xstream.mapper.MapperWrapper;

import edu.gmu.swe.datadep.xstream.ReferenceByXPathWithDependencysMarshaller;
import edu.gmu.swe.datadep.xstream.WrappedPrimitiveConverter;

public class HeapWalker {

	private static final boolean debug = Boolean.getBoolean("debug");

	protected static String getFieldFQN(Field f) {
		String clz = f.getDeclaringClass().getName();
		String fld = f.getName();
		return clz + "." + fld;
	}

	public static Object loggerSingleton;
	private static final LinkedList<StaticField> sfPool = new LinkedList<StaticField>();
	private static /* final */ Set<String> whiteList = new HashSet<String>();

	public static boolean SKIP_VALUES = true;

	public static void clearWhitelist() {
		whiteList.clear();
	}

	public static void addToWhitelist(String s) {
		whiteList.add(s);
	}

	// private static final Set<String> ignores;

	private static Set<String> fileToSet(Properties p, String key) {
		String f = p.getProperty(key);
		if (f == null) {
			System.err.println("Warn: Whitelist not specified");
			return new HashSet<String>();
		}
		if (new File(f).exists()) {
			HashSet<String> ret = new HashSet<String>();
			Scanner s;
			try {
				s = new Scanner(new File(f));
				while (s.hasNextLine()) {
					String l = s.nextLine();
					if (!l.trim().equals(""))
						ret.add(l);
				}
				s.close();
			} catch (FileNotFoundException e) {
			}
			return ret;
		} else
			throw new IllegalArgumentException(
					"Provided whitelist file, " + new File(f).getAbsolutePath() + ", does not exist");
	}

	public static void loadWhitelist() {
		whiteList.addAll(fileToSet(System.getProperties(), "whitelist"));
		// if (debug) {
		System.out.println("Loaded whitelist from " + System.getProperty("whitelist"));
		// }
		// ignores = fileToSet(System.getProperties(), "ignores");
	}

	// This might be quite inefficient...
	protected static boolean shouldCaptureStaticField(Field f) {

		//
		if (f.getDeclaringClass().getName().startsWith("java") || f.getDeclaringClass().getName().startsWith("sun")
				|| f.getDeclaringClass().getName().startsWith("edu.gmu.swe.datadep."))
			return false;

		// We do not capture fields that belongs to enums and Strings
		if (f.getType().isEnum() && f.getType().equals(f.getDeclaringClass())) {
			// // System.out
			// .println("HeapWalker.shouldCapture() DO not capture Enum items "
			// + f.getType() + "." + f.getName());
			return false;
		}

		if (f.getName().startsWith("serialVersionUID") && (Modifier.isFinal(f.getModifiers()))) {
			// System.out.println("HeapWalker.shouldCapture() Skip " +
			// f.getName() + " - "
			// + (Modifier.isFinal(f.getModifiers()) ? "final" : ""));
			return false;
		}

		String fieldName = getFieldFQN(f);
		String fldLower = fieldName.toLowerCase();
		if ((fldLower.contains("mockito")) || (fldLower.contains("$$"))) {
			// // System.out.println("***Ignored_Root: " + fieldName);
			return false;
		}

		// Not sure about this one...
		if (whiteList.isEmpty()) {
			// if (debug)
			// System.out.println("HeapWalker.shouldCaptureStaticField() White
			// list is empty");
			return true;
		}

		// Why not blacklisted "com.sun.proxy.

		// FIXME TODO Use pattern matching here !

		Package p = f.getDeclaringClass().getPackage();
		if (p != null) {
			String pkg = p.getName();
			for (String s : whiteList) {
				if (s == null)
					continue;
				if (s.trim().length() == 0)
					continue;
				if (pkg.startsWith(s.trim())) {
					// System.out.println(
					// "HeapWalker.shouldCaptureStaticField() " +
					// f.getDeclaringClass() + "." + f.getName());
					return true;
				}
			}
		}

		return false;
	}

	/**
	 * Blacklist static fields, like enums, syntetic DEP_INFO, and Strings.
	 * 
	 * @param f
	 * @return
	 */
	private static boolean isBlackListedSF(Field f) {
		String className = f.getDeclaringClass().getName();
		String fieldName = f.getName();

		// Blacklist by default Enum Items and Inner static Fields of enums in
		// general -- This might be too strict

		// Ignore Synthetic fields like the one we autogenerate and the ones for
		// enums
		if (f.isSynthetic()) {
			// // System.out.println("Ignore static syntetic field " +
			// f.getDeclaringClass() + "." + f.getName());
			return true;
		}

		// Not sure I would go for it anyway...
		// if (fieldName.equals("serialVersionUID")) {
		// System.out.println("HeapWalker.isBlackListedSF() Blacklisting Static
		// Field " + className + "." + fieldName);
		// }

		// Ignore enum items
		if (f.getDeclaringClass().isEnum() && f.getType().isEnum() && f.getType().equals(f.getDeclaringClass())) {
			// // System.out.println("Ignore static enum item " +
			// f.getDeclaringClass() + "." + f.getName());
			return true;
		}

		// Ignore static fields of taint data
		if (f.getDeclaringClass().equals(DependencyInfo.class)) {
			// // System.out.println("HeapWalker.isBlackListedSF() Ignore " +
			// f.getDeclaringClass() + "." + f.getName());
			return true;
		}

		if (fieldName.startsWith("$assertionsDisabled") || fieldName.startsWith("serialVersionUID")) {
			return true;
		}

		return "java.lang.reflect.Field".equals(className) || "java.lang.reflect.Method".equals(className)
				|| "java.lang.Class".equals(className)
				|| ("java.lang.System".equals(className) && "out".equals(fieldName))
				|| ("java.io.BufferedInputStream".equals(className) && "bufUpdater".equals(fieldName))
				|| ("java.io.BufferedReader".equals(className) && "defaultExpectedLineLength".equals(fieldName))
				|| ("java.io.File".equals(className) && "separatorChar".equals(fieldName))
				|| ("java.io.File".equals(className) && "fs".equals(fieldName))
				|| ("java.io.File".equals(className) && "separator".equals(fieldName))
				|| ("java.io.File$PathStatus".equals(className) && "CHECKED".equals(fieldName))
				|| ("java.io.File$PathStatus".equals(className) && "INVALID".equals(fieldName))
				|| ("java.io.FileDescriptor".equals(className) && "err".equals(fieldName))
				|| ("java.io.FileDescriptor".equals(className) && "out".equals(fieldName))
				|| ("java.io.FileDescriptor".equals(className) && "in".equals(fieldName))
				|| ("java.io.ObjectInputStream".equals(className) && "unsharedMarker".equals(fieldName))
				|| ("java.io.ObjectOutputStream".equals(className) && "extendedDebugInfo".equals(fieldName))
				|| ("java.io.ObjectStreamClass".equals(className) && "reflFactory".equals(fieldName))
				|| ("java.io.ObjectStreamClass".equals(className) && "NO_FIELDS".equals(fieldName))
				|| ("java.io.ObjectStreamClass$Caches".equals(className) && "reflectors".equals(fieldName))
				|| ("java.io.ObjectStreamClass$Caches".equals(className) && "reflectorsQueue".equals(fieldName))
				|| ("java.io.ObjectStreamClass$Caches".equals(className) && "localDescs".equals(fieldName))
				|| ("java.io.ObjectStreamClass$Caches".equals(className) && "localDescsQueue".equals(fieldName))
				|| ("java.io.ObjectStreamClass$EntryFuture".equals(className) && "unset".equals(fieldName))
				|| ("java.io.ObjectStreamClass$FieldReflector".equals(className) && "unsafe".equals(fieldName))
				|| ("java.lang.annotation.RetentionPolicy".equals(className) && "RUNTIME".equals(fieldName))
				|| ("java.lang.Class".equals(className) && "reflectionFactory".equals(fieldName))
				|| ("java.lang.Class".equals(className) && "useCaches".equals(fieldName))
				|| ("java.lang.Class".equals(className) && "initted".equals(fieldName))
				|| ("java.lang.Class$Atomic".equals(className) && "annotationTypeOffset".equals(fieldName))
				|| ("java.lang.Class$Atomic".equals(className) && "annotationDataOffset".equals(fieldName))
				|| ("java.lang.Class$Atomic".equals(className) && "reflectionDataOffset".equals(fieldName))
				|| ("java.lang.Class$Atomic".equals(className) && "unsafe".equals(fieldName))
				|| ("java.lang.ClassLoader".equals(className) && "nocerts".equals(fieldName))
				|| ("java.lang.ClassLoader".equals(className) && "scl".equals(fieldName))
				|| ("java.lang.ClassLoader".equals(className) && "sclSet".equals(fieldName))
				|| ("java.lang.ClassLoader$ParallelLoaders".equals(className) && "loaderTypes".equals(fieldName))
				|| ("java.lang.Double".equals(className) && "TYPE".equals(fieldName))
				|| ("java.lang.Long".equals(className) && "TYPE".equals(fieldName))
				|| ("java.lang.Long$LongCache".equals(className) && "cache".equals(fieldName))
				|| ("java.lang.Math".equals(className) && "$assertionsDisabled".equals(fieldName))
				|| ("java.lang.Math$RandomNumberGeneratorHolder".equals(className)
						&& "randomNumberGenerator".equals(fieldName))
				|| ("java.lang.Package".equals(className) && "pkgs".equals(fieldName))
				|| ("java.lang.ref.Finalizer".equals(className) && "queue".equals(fieldName))
				|| ("java.lang.ref.Finalizer".equals(className) && "unfinalized".equals(fieldName))
				|| ("java.lang.ref.Finalizer".equals(className) && "lock".equals(fieldName))
				|| ("java.lang.reflect.AccessibleObject".equals(className) && "reflectionFactory".equals(fieldName))
				|| ("java.lang.reflect.Proxy$ProxyClassFactory".equals(className)
						&& "nextUniqueNumber".equals(fieldName))
				|| ("java.lang.reflect.WeakCache$CacheKey".equals(className) && "NULL_KEY".equals(fieldName))
				|| ("java.lang.reflect.WeakCache$Factory".equals(className) && "$assertionsDisabled".equals(fieldName))
				|| ("java.lang.StringCoding".equals(className) && "decoder".equals(fieldName))
				|| ("java.lang.StringCoding".equals(className) && "encoder".equals(fieldName))
				|| ("java.lang.Thread".equals(className) && "threadSeqNumber".equals(fieldName))
				|| ("java.lang.ThreadLocal".equals(className) && "nextHashCode".equals(fieldName))
				|| ("java.lang.Throwable".equals(className) && "SUPPRESSED_SENTINEL".equals(fieldName))
				|| ("java.lang.Throwable".equals(className) && "UNASSIGNED_STACK".equals(fieldName))
				|| ("java.lang.Void".equals(className) && "TYPE".equals(fieldName))
				|| ("java.math.BigDecimal".equals(className) && "threadLocalStringBuilderHelper".equals(fieldName))
				|| ("java.math.BigDecimal".equals(className) && "ZERO".equals(fieldName))
				|| ("java.math.BigDecimal".equals(className) && "ZERO_SCALED_BY".equals(fieldName))
				|| ("java.math.BigDecimal".equals(className) && "TEN".equals(fieldName))
				|| ("java.math.BigDecimal".equals(className) && "BIG_TEN_POWERS_TABLE".equals(fieldName))
				|| ("java.math.BigDecimal".equals(className) && "BIG_TEN_POWERS_TABLE_MAX".equals(fieldName))
				|| ("java.math.BigDecimal".equals(className) && "THRESHOLDS_TABLE".equals(fieldName))
				|| ("java.math.BigDecimal".equals(className) && "LONG_TEN_POWERS_TABLE".equals(fieldName))
				|| ("java.math.BigDecimal".equals(className) && "zeroThroughTen".equals(fieldName))
				|| ("java.math.BigDecimal".equals(className) && "$assertionsDisabled".equals(fieldName))
				|| ("java.math.BigDecimal".equals(className) && "ONE".equals(fieldName))
				|| ("java.math.BigDecimal".equals(className) && "LONGLONG_TEN_POWERS_TABLE".equals(fieldName))
				|| ("java.math.BigInteger".equals(className) && "digitsPerLong".equals(fieldName))
				|| ("java.math.BigInteger".equals(className) && "longRadix".equals(fieldName))
				|| ("java.math.BigInteger".equals(className) && "negConst".equals(fieldName))
				|| ("java.math.BigInteger".equals(className) && "TEN".equals(fieldName))
				|| ("java.math.BigInteger".equals(className) && "posConst".equals(fieldName))
				|| ("java.math.BigInteger".equals(className) && "ONE".equals(fieldName))
				|| ("java.math.BigInteger".equals(className) && "ZERO".equals(fieldName))
				|| ("java.math.BigInteger".equals(className) && "zeros".equals(fieldName))
				|| ("java.math.MutableBigInteger".equals(className) && "ONE".equals(fieldName))
				|| ("java.math.MutableBigInteger".equals(className) && "$assertionsDisabled".equals(fieldName))
				|| ("java.math.RoundingMode".equals(className) && "HALF_EVEN".equals(fieldName))
				|| ("java.math.RoundingMode".equals(className) && "HALF_UP".equals(fieldName))
				|| ("java.math.RoundingMode".equals(className) && "UNNECESSARY".equals(fieldName))
				|| ("java.math.RoundingMode".equals(className) && "HALF_DOWN".equals(fieldName))
				|| ("java.math.RoundingMode".equals(className) && "FLOOR".equals(fieldName))
				|| ("java.math.RoundingMode".equals(className) && "CEILING".equals(fieldName))
				|| ("java.math.RoundingMode".equals(className) && "DOWN".equals(fieldName))
				|| ("java.math.RoundingMode".equals(className) && "UP".equals(fieldName))
				|| ("java.math.RoundingMode".equals(className) && "$VALUES".equals(fieldName))
				|| ("java.net.URI".equals(className) && "H_SCHEME".equals(fieldName))
				|| ("java.net.URI".equals(className) && "L_SCHEME".equals(fieldName))
				|| ("java.net.URI".equals(className) && "H_ALPHA".equals(fieldName))
				|| ("java.net.URI".equals(className) && "H_PATH".equals(fieldName))
				|| ("java.net.URI".equals(className) && "L_PATH".equals(fieldName))
				|| ("java.net.URL".equals(className) && "handlers".equals(fieldName))
				|| ("java.nio.Bits".equals(className) && "byteOrder".equals(fieldName))
				|| ("java.nio.ByteOrder".equals(className) && "BIG_ENDIAN".equals(fieldName))
				|| ("java.nio.ByteOrder".equals(className) && "LITTLE_ENDIAN".equals(fieldName))
				|| ("java.nio.charset.Charset".equals(className) && "defaultCharset".equals(fieldName))
				|| ("java.nio.charset.Charset".equals(className) && "bugLevel".equals(fieldName))
				|| ("java.nio.charset.Charset".equals(className) && "cache1".equals(fieldName))
				|| ("java.nio.charset.CoderResult".equals(className) && "UNDERFLOW".equals(fieldName))
				|| ("java.nio.charset.CoderResult".equals(className) && "OVERFLOW".equals(fieldName))
				|| ("java.nio.charset.CodingErrorAction".equals(className) && "REPORT".equals(fieldName))
				|| ("java.nio.charset.CodingErrorAction".equals(className) && "REPLACE".equals(fieldName))
				|| ("java.nio.DirectLongBufferU".equals(className) && "unsafe".equals(fieldName))
				|| ("java.security.Provider".equals(className) && "knownEngines".equals(fieldName))
				|| ("java.security.Provider".equals(className) && "previousKey".equals(fieldName))
				|| ("java.security.Security".equals(className) && "spiMap".equals(fieldName))
				|| ("java.text.DecimalFormat".equals(className) && "EmptyFieldPositionArray".equals(fieldName))
				|| ("java.text.DecimalFormat".equals(className) && "$assertionsDisabled".equals(fieldName))
				|| ("java.text.DecimalFormat$DigitArrays".equals(className) && "DigitHundreds1000".equals(fieldName))
				|| ("java.text.DecimalFormat$DigitArrays".equals(className) && "DigitTens1000".equals(fieldName))
				|| ("java.text.DecimalFormat$DigitArrays".equals(className) && "DigitOnes1000".equals(fieldName))
				|| ("java.text.DigitList".equals(className) && "$assertionsDisabled".equals(fieldName))
				|| ("java.text.DigitList$1".equals(className) && "$SwitchMap$java$math$RoundingMode".equals(fieldName))
				|| ("java.text.DontCareFieldPosition".equals(className) && "INSTANCE".equals(fieldName))
				|| ("java.text.NumberFormat$Field".equals(className) && "FRACTION".equals(fieldName))
				|| ("java.text.NumberFormat$Field".equals(className) && "INTEGER".equals(fieldName))
				|| ("java.text.NumberFormat$Field".equals(className) && "SIGN".equals(fieldName))
				|| ("java.text.NumberFormat$Field".equals(className) && "DECIMAL_SEPARATOR".equals(fieldName))
				|| ("java.util.ArrayList".equals(className) && "EMPTY_ELEMENTDATA".equals(fieldName))
				|| ("java.util.Arrays$LegacyMergeSort".equals(className) && "userRequested".equals(fieldName))
				|| ("java.util.BitSet".equals(className) && "$assertionsDisabled".equals(fieldName))
				|| ("java.util.Collections".equals(className) && "EMPTY_SET".equals(fieldName))
				|| ("java.util.Collections".equals(className) && "EMPTY_MAP".equals(fieldName))
				|| ("java.util.Collections".equals(className) && "EMPTY_LIST".equals(fieldName))
				|| ("java.util.Collections$EmptyIterator".equals(className) && "EMPTY_ITERATOR".equals(fieldName))
				|| ("java.util.ComparableTimSort".equals(className) && "$assertionsDisabled".equals(fieldName))
				|| ("java.util.concurrent.atomic.AtomicReference".equals(className) && "unsafe".equals(fieldName))
				|| ("java.util.concurrent.atomic.AtomicReference".equals(className) && "valueOffset".equals(fieldName))
				|| ("java.util.concurrent.atomic.AtomicReferenceFieldUpdater$AtomicReferenceFieldUpdaterImpl"
						.equals(className) && "unsafe".equals(fieldName))
				|| ("java.util.concurrent.ConcurrentHashMap".equals(className) && "BASECOUNT".equals(fieldName))
				|| ("java.util.concurrent.ConcurrentHashMap".equals(className) && "ABASE".equals(fieldName))
				|| ("java.util.concurrent.ConcurrentHashMap".equals(className) && "ASHIFT".equals(fieldName))
				|| ("java.util.concurrent.ConcurrentHashMap".equals(className) && "U".equals(fieldName))
				|| ("java.util.concurrent.ConcurrentHashMap".equals(className) && "SIZECTL".equals(fieldName))
				|| ("java.util.concurrent.ConcurrentHashMap".equals(className) && "TRANSFERINDEX".equals(fieldName))
				|| ("java.util.concurrent.ConcurrentHashMap".equals(className) && "NCPU".equals(fieldName))
				|| ("java.util.concurrent.ConcurrentHashMap".equals(className)
						&& "RESIZE_STAMP_SHIFT".equals(fieldName))
				|| ("java.util.concurrent.ConcurrentHashMap".equals(className) && "RESIZE_STAMP_BITS".equals(fieldName))
				|| ("java.util.concurrent.FutureTask".equals(className) && "UNSAFE".equals(fieldName))
				|| ("java.util.concurrent.locks.AbstractQueuedSynchronizer".equals(className)
						&& "unsafe".equals(fieldName))
				|| ("java.util.concurrent.locks.AbstractQueuedSynchronizer".equals(className)
						&& "stateOffset".equals(fieldName))
				|| ("java.util.concurrent.locks.AbstractQueuedSynchronizer".equals(className)
						&& "tailOffset".equals(fieldName))
				|| ("java.util.concurrent.locks.AbstractQueuedSynchronizer".equals(className)
						&& "waitStatusOffset".equals(fieldName))
				|| ("java.util.concurrent.locks.LockSupport".equals(className) && "UNSAFE".equals(fieldName))
				|| ("java.util.concurrent.locks.LockSupport".equals(className) && "parkBlockerOffset".equals(fieldName))
				|| ("java.util.Currency".equals(className) && "instances".equals(fieldName))
				|| ("java.util.Currency".equals(className) && "mainTable".equals(fieldName))
				|| ("java.util.Currency$CurrencyNameGetter".equals(className)
						&& "$assertionsDisabled".equals(fieldName))
				|| ("java.util.Currency$CurrencyNameGetter".equals(className) && "INSTANCE".equals(fieldName))
				|| ("java.util.Date".equals(className) && "gcal".equals(fieldName))
				|| ("java.util.Formatter".equals(className) && "fsPattern".equals(fieldName))
				|| ("java.util.Formatter$Flags".equals(className) && "UPPERCASE".equals(fieldName))
				|| ("java.util.Formatter$Flags".equals(className) && "ALTERNATE".equals(fieldName))
				|| ("java.util.Formatter$Flags".equals(className) && "PARENTHESES".equals(fieldName))
				|| ("java.util.Formatter$Flags".equals(className) && "GROUP".equals(fieldName))
				|| ("java.util.Formatter$Flags".equals(className) && "ZERO_PAD".equals(fieldName))
				|| ("java.util.Formatter$Flags".equals(className) && "LEADING_SPACE".equals(fieldName))
				|| ("java.util.Formatter$Flags".equals(className) && "PLUS".equals(fieldName))
				|| ("java.util.Formatter$Flags".equals(className) && "LEFT_JUSTIFY".equals(fieldName))
				|| ("java.util.Formatter$Flags".equals(className) && "PREVIOUS".equals(fieldName))
				|| ("java.util.Formatter$Flags".equals(className) && "NONE".equals(fieldName))
				|| ("java.util.HashMap$TreeNode".equals(className) && "$assertionsDisabled".equals(fieldName))
				|| ("java.util.HashSet".equals(className) && "PRESENT".equals(fieldName))
				|| ("java.util.jar.Attributes$Name".equals(className) && "SEALED".equals(fieldName))
				|| ("java.util.Locale".equals(className) && "US".equals(fieldName))
				|| ("java.util.Locale".equals(className) && "ENGLISH".equals(fieldName))
				|| ("java.util.Locale".equals(className) && "ROOT".equals(fieldName))
				|| ("java.util.Locale".equals(className) && "LOCALECACHE".equals(fieldName))
				|| ("java.util.Locale$1".equals(className) && "$SwitchMap$java$util$Locale$Category".equals(fieldName))
				|| ("java.util.Locale$Category".equals(className) && "FORMAT".equals(fieldName))
				|| ("java.util.Locale$Category".equals(className) && "DISPLAY".equals(fieldName))
				|| ("java.util.Random".equals(className) && "seedUniquifier".equals(fieldName))
				|| ("java.util.regex.ASCII".equals(className) && "ctype".equals(fieldName))
				|| ("java.util.regex.Pattern".equals(className) && "accept".equals(fieldName))
				|| ("java.util.regex.Pattern".equals(className) && "lastAccept".equals(fieldName))
				|| ("java.util.ResourceBundle".equals(className) && "$assertionsDisabled".equals(fieldName))
				|| ("java.util.ResourceBundle".equals(className) && "NONEXISTENT_BUNDLE".equals(fieldName))
				|| ("java.util.ResourceBundle".equals(className) && "cacheList".equals(fieldName))
				|| ("java.util.ResourceBundle".equals(className) && "referenceQueue".equals(fieldName))
				|| ("java.util.ResourceBundle$Control".equals(className) && "CANDIDATES_CACHE".equals(fieldName))
				|| ("java.util.ResourceBundle$Control".equals(className) && "FORMAT_DEFAULT".equals(fieldName))
				|| ("java.util.ResourceBundle$Control".equals(className) && "INSTANCE".equals(fieldName))
				|| ("java.util.ResourceBundle$RBClassLoader".equals(className) && "loader".equals(fieldName))
				|| ("java.util.ResourceBundle$RBClassLoader".equals(className) && "INSTANCE".equals(fieldName))
				|| ("java.util.TimeZone".equals(className) && "defaultTimeZone".equals(fieldName))
				|| ("java.util.TimSort".equals(className) && "$assertionsDisabled".equals(fieldName))
				|| ("java.util.zip.Inflater".equals(className) && "defaultBuf".equals(fieldName))
				|| ("java.util.zip.Inflater".equals(className) && "$assertionsDisabled".equals(fieldName))
				|| ("sun.misc.FDBigInteger".equals(className) && "LONG_5_POW".equals(fieldName))
				|| ("sun.misc.FDBigInteger".equals(className) && "ZERO".equals(fieldName))
				|| ("sun.misc.FDBigInteger".equals(className) && "POW_5_CACHE".equals(fieldName))
				|| ("sun.misc.FDBigInteger".equals(className) && "SMALL_5_POW".equals(fieldName))
				|| ("sun.misc.FDBigInteger".equals(className) && "$assertionsDisabled".equals(fieldName))
				|| ("sun.misc.FloatingDecimal".equals(className) && "threadLocalBinaryToASCIIBuffer".equals(fieldName))
				|| ("sun.misc.FloatingDecimal".equals(className) && "B2AC_POSITIVE_ZERO".equals(fieldName))
				|| ("sun.misc.FloatingDecimal".equals(className) && "B2AC_POSITIVE_INFINITY".equals(fieldName))
				|| ("sun.misc.FloatingDecimal".equals(className) && "B2AC_NEGATIVE_INFINITY".equals(fieldName))
				|| ("sun.misc.FloatingDecimal".equals(className) && "B2AC_NOT_A_NUMBER".equals(fieldName))
				|| ("sun.misc.FloatingDecimal$ASCIIToBinaryBuffer".equals(className) && "TINY_10_POW".equals(fieldName))
				|| ("sun.misc.FloatingDecimal$ASCIIToBinaryBuffer".equals(className)
						&& "SMALL_10_POW".equals(fieldName))
				|| ("sun.misc.FloatingDecimal$ASCIIToBinaryBuffer".equals(className)
						&& "MAX_SMALL_TEN".equals(fieldName))
				|| ("sun.misc.FloatingDecimal$BinaryToASCIIBuffer".equals(className) && "N_5_BITS".equals(fieldName))
				|| ("sun.misc.FloatingDecimal$BinaryToASCIIBuffer".equals(className)
						&& "$assertionsDisabled".equals(fieldName))
				|| ("sun.misc.PerfCounter$CoreCounters".equals(className) && "lc".equals(fieldName))
				|| ("sun.misc.PerfCounter$CoreCounters".equals(className) && "lct".equals(fieldName))
				|| ("sun.misc.PerfCounter$CoreCounters".equals(className) && "pdt".equals(fieldName))
				|| ("sun.misc.PerfCounter$CoreCounters".equals(className) && "rcbt".equals(fieldName))
				|| ("sun.misc.ProxyGenerator".equals(className) && "saveGeneratedFiles".equals(fieldName))
				|| ("sun.misc.ProxyGenerator".equals(className) && "$assertionsDisabled".equals(fieldName))
				|| ("sun.misc.ProxyGenerator".equals(className) && "toStringMethod".equals(fieldName))
				|| ("sun.misc.ProxyGenerator".equals(className) && "equalsMethod".equals(fieldName))
				|| ("sun.misc.ProxyGenerator".equals(className) && "hashCodeMethod".equals(fieldName))
				|| ("sun.misc.ProxyGenerator$PrimitiveTypeInfo".equals(className) && "table".equals(fieldName))
				|| ("sun.misc.SharedSecrets".equals(className) && "javaLangAccess".equals(fieldName))
				|| ("sun.misc.Unsafe".equals(className) && "theUnsafe".equals(fieldName))
				|| ("sun.misc.URLClassPath".equals(className) && "DEBUG".equals(fieldName))
				|| ("sun.misc.VM".equals(className) && "allowArraySyntax".equals(fieldName))
				|| ("sun.misc.VM".equals(className) && "peakFinalRefCount".equals(fieldName))
				|| ("sun.misc.VM".equals(className) && "finalRefCount".equals(fieldName))
				|| ("sun.net.www.ParseUtil".equals(className) && "encodedInPath".equals(fieldName))
				|| ("sun.nio.cs.StreamEncoder".equals(className) && "$assertionsDisabled".equals(fieldName))
				|| ("sun.reflect.AccessorGenerator".equals(className) && "primitiveTypes".equals(fieldName))
				|| ("sun.reflect.annotation.AnnotationInvocationHandler".equals(className)
						&& "$assertionsDisabled".equals(fieldName))
				|| ("sun.reflect.annotation.AnnotationParser".equals(className)
						&& "EMPTY_ANNOTATION_ARRAY".equals(fieldName))
				|| ("sun.reflect.ClassDefiner".equals(className) && "unsafe".equals(fieldName))
				|| ("sun.reflect.generics.parser.SignatureParser".equals(className)
						&& "$assertionsDisabled".equals(fieldName))
				|| ("sun.reflect.generics.visitor.Reifier".equals(className) && "$assertionsDisabled".equals(fieldName))
				|| ("sun.reflect.MethodAccessorGenerator".equals(className) && "constructorSymnum".equals(fieldName))
				|| ("sun.reflect.MethodAccessorGenerator".equals(className)
						&& "serializationConstructorSymnum".equals(fieldName))
				|| ("sun.reflect.MethodAccessorGenerator".equals(className) && "methodSymnum".equals(fieldName))
				|| ("sun.reflect.UnsafeFieldAccessorImpl".equals(className) && "unsafe".equals(fieldName))
				|| ("sun.security.jca.Providers".equals(className) && "providerList".equals(fieldName))
				|| ("sun.security.provider.ByteArrayAccess".equals(className) && "unsafe".equals(fieldName))
				|| ("sun.security.provider.ByteArrayAccess".equals(className) && "byteArrayOfs".equals(fieldName))
				|| ("sun.security.provider.ByteArrayAccess".equals(className)
						&& "littleEndianUnaligned".equals(fieldName))
				|| ("sun.security.provider.DigestBase".equals(className) && "padding".equals(fieldName))
				|| ("sun.util.calendar.BaseCalendar".equals(className) && "$assertionsDisabled".equals(fieldName))
				|| ("sun.util.calendar.BaseCalendar".equals(className) && "ACCUMULATED_DAYS_IN_MONTH".equals(fieldName))
				|| ("sun.util.calendar.BaseCalendar".equals(className) && "FIXED_DATES".equals(fieldName))
				|| ("sun.util.locale.BaseLocale".equals(className) && "CACHE".equals(fieldName))
				|| ("sun.util.locale.provider.LocaleDataMetaInfo".equals(className)
						&& "resourceNameToLocales".equals(fieldName))
				|| ("sun.util.locale.provider.LocaleProviderAdapter".equals(className)
						&& "jreLocaleProviderAdapter".equals(fieldName))
				|| ("sun.util.locale.provider.LocaleProviderAdapter".equals(className)
						&& "adapterCache".equals(fieldName))
				|| ("sun.util.locale.provider.LocaleProviderAdapter".equals(className)
						&& "defaultLocaleProviderAdapter".equals(fieldName))
				|| ("sun.util.locale.provider.LocaleProviderAdapter$1".equals(className)
						&& "$SwitchMap$sun$util$locale$provider$LocaleProviderAdapter$Type".equals(fieldName))
				|| ("sun.util.locale.provider.LocaleProviderAdapter$Type".equals(className) && "JRE".equals(fieldName))
				|| ("sun.util.locale.provider.LocaleResources".equals(className) && "NULLOBJECT".equals(fieldName))
				|| ("sun.util.locale.provider.LocaleServiceProviderPool".equals(className)
						&& "poolOfPools".equals(fieldName))
				|| ("sun.util.resources.LocaleData$LocaleDataResourceBundleControl".equals(className)
						&& "$assertionsDisabled".equals(fieldName))
				|| ("sun.util.resources.LocaleData$LocaleDataResourceBundleControl".equals(className)
						&& "INSTANCE".equals(fieldName));
	}

	private static final LinkedHashMap<String, Object> nameToInstance = new LinkedHashMap();
	static int testCount = 1;
	public static HashMap<Integer, String> testNumToMethod = new HashMap<Integer, String>();
	public static HashMap<Integer, String> testNumToTestClass = new HashMap<>();

	public static synchronized void resetAllState() {
		testCount = 1;
		DependencyInfo.IN_CAPTURE = true;
		DependencyInfo.CURRENT_TEST_COUNT = 1;
		for (StaticField sf : sfPool) {
			sf.clearConflict();
		}
		sfPool.clear();
		for (WeakReference<DependencyInfo> i : lastGenReachable) {
			if (i.get() != null) {
				i.get().clearConflict();
			}
		}
		lastGenReachable.clear(); // Added
		DependencyInfo.IN_CAPTURE = false;
	}

	public static boolean SKIP_XML = true;

	public static synchronized LinkedList<StaticFieldDependency> walkAndFindDependencies(String className,
			String methodName) {
		if (debug) {
			System.out.println(
					"\n\n\n HeapWalker.walkAndFindDependencies() for " + className + "." + methodName + "\n\n\n ");
		}
		// It seems this has no effect ...
		DependencyInfo.IN_CAPTURE = true;

		testNumToMethod.put(testCount, methodName);
		testNumToTestClass.put(testCount, className);

		// System.out.println("\n\n\n HeapWalker.walkAndFindDependencies() Last
		// executed test is " + className + "."
		// + methodName + " ID : " + testCount + " \n\n\n ");
		testCount++;

		// First - clean up from last generation: make sure that all static
		// field up-pointers are cleared out
		// System.out.println("HeapWalker.walkAndFindDependencies() Last Gen
		// Reach " + lastGenReachable.size());
		for (WeakReference<DependencyInfo> inf : lastGenReachable) {
			if (inf.get() != null) {
				// inf.get().logMe("Not sure");
				// System.out.println(
				// "HeapWalker.walkAndFindDependencies() Last Gen " +
				// inf.get().printMe() + " clear conflicts");
				inf.get().clearSFs();
				inf.get().clearConflict();
			}
		}
		lastGenReachable.clear();

		// Are duplicate allowed in sfPool ?

		// FIXME Note that the number of Static Fields increases with time since
		// at every walk a new one is created no matter what, this also includes
		// the XML representation, and everything leaks to the memory....
		LinkedList<StaticFieldDependency> deps = new LinkedList<StaticFieldDependency>();

		/*
		 * sfPool contains a copy of SF for all the past tests, this is useful
		 * to accumulate conflicts that are otherwise lost but why they are
		 * lost? Because we reset the conflict flag of DepInfo that are
		 * reachable, so upon serialization the "old" conflict disappear
		 */

		// SF POOL Contains no matter what only the SF that was added in the
		// previous run. Plust we remove olf SF from DepInfo if a new one is
		// provided
		// System.out.println("HeapWalker.walkAndFindDependencies() Processing
		// SF " + sfPool.size());
		for (StaticField sf : sfPool) {
			if (sf.isConflict()) {
				if (debug) {
					System.out.println(
							"HeapWalker.walkAndFindDependencies() Conflict for SF " + sf.field.getDeclaringClass() + "."
									+ sf.field.getName() + " " + System.identityHashCode(sf));
				}
				//
				StaticFieldDependency dep = new StaticFieldDependency();
				//
				dep.depGen = sf.dependsOn;
				// dep.depTestName = testNumToTestClass.get(sf.dependsOn) + "."
				// + testNumToMethod.get(sf.dependsOn);
				//
				dep.field = sf.field;
				// Calling getValue results in draining the content... ?
				dep.value = sf.getValue();
				//
				deps.add(dep);
				// This thing clear previous conflicts informations, however, in
				// case of reads in subsequent tests
				// only the former test report the dep, while all of them should
				// have done. So we clear conflict data ONLY for fields that we
				// wrote.
				if (sf.dependsOn == DependencyInfo.CURRENT_TEST_COUNT) {
					sf.clearConflict();
				}
			} else {
				if (debug) {
					System.out.println(
							"HeapWalker.walkAndFindDependencies() NO Conflict for SF " + sf.field.getDeclaringClass()
									+ "." + sf.field.getName() + " " + System.identityHashCode(sf));
				}
			}
		}
		// TODO - For the fun on it, remove the value from the sf objects before
		// clearing the pool...

		// This empties the SF poll but does NOT remove the Static Fields
		// Objects and their value, which is suspect remains reachable. They are
		// inside the DepObjects !
		sfPool.clear();

		// TODO is the set of classes from the instrumentation are the same, we
		// can keep the cache, aren't we ?!

		// System.out.println("HeapWalker.walkAndFindDependencies() Walking the
		// heap");
		if (debug) {
			System.out.println("\n\n HeapWalker.walkAndFindDependencies() Rebuild sf POOL\n\n");
		}

		HashMap<String, StaticField> cache = new HashMap<String, StaticField>();
		for (Class<?> c : PreMain.getInstrumentation().getAllLoadedClasses()) {

			Set<Field> allFields = new HashSet<Field>();

			try {
				Field[] declaredFields = c.getDeclaredFields();
				Field[] fields = c.getFields();

				allFields.addAll(Arrays.asList(declaredFields));

				allFields.addAll(Arrays.asList(fields));

				// At this point the _parentField is declared
			} catch (NoClassDefFoundError e) {
				continue;
			}

			cache.clear();

			for (Field f : allFields) {
				String fieldName = getFieldFQN(f);

				if (!Modifier.isStatic(f.getModifiers())) {
					continue;

				}
				// At this point only static
				if (Modifier.isFinal(f.getModifiers()) && f.getType().isPrimitive()) {
					// This is imprecise because of the initialization problem,
					// but we live with that, because those are automatically
					// initialized the moment they are defined
					// System.out.println("HeapWalker.walkAndFindDependencies()
					// Skip static final primitive field "
					// + className + "." + fieldName);
					continue;
				}

				// At this point either static primitives, or static
				// final | non-final objects

				try {
					if (isBlackListedSF(f)) {

						if (f.getType().isPrimitive() || f.getType().isAssignableFrom(String.class)
								|| f.getType().isEnum()) {
							try {

								Field depInfo = f.getDeclaringClass()
										.getDeclaredField(f.getName() + "__DEPENDENCY_INFO");
								depInfo.setAccessible(true);
								DependencyInfo i = (DependencyInfo) depInfo.get(null);
								i.setIgnored(true);

								// System.out.println("HeapWalker.walkAndFindDependencies()
								// Blacklist " + fieldName);
							} catch (Throwable t) {
								// Maybe the field doesn't exist on this
								// versin of the JDK, so ignore
							}
						} else {
							try {
								// In some cases, e.g. Debian server no X11
								// sun.mic this fails and breaks everything
								// Shoud this be propagated anyway ?!
								f.setAccessible(true);
								Object obj = f.get(null);
								visitFieldForIgnore(obj);
							} catch (Throwable t) {
								if (debug) {
									System.out.println(
											"HeapWalker.walkAndFindDependencies() Failed visitFiledForIgnore on " + f);
								}
							}
						}
					} else if (shouldCaptureStaticField(f)) {

						if (f.getName().endsWith("__DEPENDENCY_INFO")) {
							fieldName = fieldName.replace("__DEPENDENCY_INFO", "");

							if (!cache.containsKey(fieldName)) {

								StaticField sf = new StaticField(f.getDeclaringClass()
										.getDeclaredField(f.getName().replace("__DEPENDENCY_INFO", "")));

								// System.out.println("HeapWalker.walkAndFindDependencies()
								// CREATE NEW SF "
								// + System.identityHashCode(sf) + " for " +
								// className + "."
								// + f.getName().replace("__DEPENDENCY_INFO",
								// ""));
								cache.put(fieldName, sf);
							}

							// Self linking - fieldName is the one without
							// __DEPENDENCY_INFO
							StaticField sf = cache.get(fieldName);
							f.setAccessible(true);
							DependencyInfo inf = (DependencyInfo) f.get(null);

							if (inf != null) {
								inf.fields = new StaticField[1];
								inf.fields[0] = sf;
							}

							// should this be the fieldName as well ?
							Field origField = f.getDeclaringClass()
									.getDeclaredField(f.getName().replace("__DEPENDENCY_INFO", ""));

							if ((origField.getType().isPrimitive() || origField.getType().isAssignableFrom(String.class)
									|| origField.getType().isEnum())) {
								//
								if (debug) {
									System.out.println("HeapWalker.walkAndFindDependencies() Adding SF to the Pool "
											+ sf.field.getDeclaringClass() + "." + sf.field.getName() + " == "
											+ System.identityHashCode(sf));
								}
								// System.out.println(
								// "HeapWalker.walkAndFindDependencies() Is
								// conflict ? " + sf.isConflict());
								sfPool.add(sf);
							}

							if (inf != null && inf.isConflict()) {
								inf.clearConflict();
							}
						} else if (
						// This is the "Plain field not the _DepInfo"
						!(f.getType().isPrimitive() || //
								f.getType().isAssignableFrom(String.class) || f.getType().isEnum())) {
							// Complext objects are processed here ...
							if (!cache.containsKey(fieldName)) {
								StaticField sf = new StaticField(f);
								// System.out.println("HeapWalker.walkAndFindDependencies()
								// CREATE NEW SF "
								// + System.identityHashCode(sf) + " for " +
								// className + "." + fieldName);
								cache.put(fieldName, sf);
							}

							StaticField sf = cache.get(fieldName);
							f.setAccessible(true);
							Object obj = f.get(null);

							//
							if (debug) {
								System.out.println("HeapWalker.walkAndFindDependencies() Adding SF to the Pool "
										+ sf.field.getDeclaringClass() + "." + sf.field.getName() + " == "
										+ System.identityHashCode(sf));
							}
							//
							// System.out.println("HeapWalker.walkAndFindDependencies()
							// Is conflict ? " + sf.isConflict());
							//
							sfPool.add(sf);

							// System.out.println(
							// "HeapWalker.walkAndFindDependencies() Start
							// visiting the Real objects for SF: "
							// + fieldName);
							visitField(sf, f, obj, false);

							// } else {
							// System.out.println(
							// "HeapWalker.visitField() Found
							// primitive/string/enum... this should be skipped "
							// + className + "." + fieldName);
						}

					} else {
						//
						// System.out.println("HeapWalker.walkAndFindDependencies()
						// Not blacklisted and not captured "
						// + className + "." + fieldName);
					}

				} catch (NoClassDefFoundError e) {
					e.printStackTrace();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		if (debug) {
			System.out.println("\n\n HeapWalker.walkAndFindDependencies() End of Rebuild SF Pool \n");
		}

		// Print relevant statistics. Others are: number of class and fields
		// inspected
		System.out.println("HeapWalker.walkAndFindDependencies() Size of SF Pool: " + sfPool.size());

		DependencyInfo.CURRENT_TEST_COUNT++;
		DependencyInfo.IN_CAPTURE = false;
		return deps;
	}

	private static void visitFieldForIgnore(Object obj) {
		if (obj != null) {

			DependencyInfo inf = TagHelper.getOrFetchTag(obj);
			if (inf.isIgnored()) {
				return;
			}
			inf.setIgnored(true);
			if (// inf.getCrawledGen() != DependencyInfo.CURRENT_TEST_COUNT &&
			!obj.getClass().isArray()) {
				inf.setCrawledGen();
				Set<Field> allFields = new HashSet<Field>();
				try {
					Field[] declaredFields = obj.getClass().getDeclaredFields();
					Field[] fields = obj.getClass().getFields();
					allFields.addAll(Arrays.asList(declaredFields));
					allFields.addAll(Arrays.asList(fields));
				} catch (NoClassDefFoundError e) {
				}
				for (Field f : allFields) {
					if (!f.getType().isPrimitive() && !Modifier.isStatic(f.getModifiers())) {
						try {
							f.setAccessible(true);
							visitFieldForIgnore(f.get(obj));
						} catch (IllegalArgumentException e) {
							e.printStackTrace();
						} catch (IllegalAccessException e) {
							e.printStackTrace();
						}
					}
				}
			} else if (obj.getClass().isArray() && !obj.getClass().getComponentType().isPrimitive()) {
				inf.setCrawledGen();
				Object[] ar = (Object[]) obj;
				for (Object o : ar) {
					visitFieldForIgnore(o);
				}
			}
		}
	}

	static XStream xStreamInst;
	public static boolean CAPTURE_TAINTS = false;

	private static XStream getXStreamInstance() {
		if (xStreamInst != null)
			return xStreamInst;
		// TODO - maintain same mapping for references when serializing multiple
		// roots
		xStreamInst = new XStream(new DepInfoReflectionProvider()) {
			@Override
			protected MapperWrapper wrapMapper(MapperWrapper next) {
				return new FilteringFieldMapper(next);
			}
		};
		// Treat Wrapped type in a different way, basically, extract the DEP
		// info from containing class. Not sure which assumtptions must hold at
		// this point ...
		xStreamInst.registerConverter(new WrappedPrimitiveConverter(), XStream.PRIORITY_VERY_HIGH);
		//
		xStreamInst.setMarshallingStrategy(
				new ReferenceByXPathMarshallingStrategy(ReferenceByXPathMarshallingStrategy.ABSOLUTE) {
					@Override
					protected TreeMarshaller createMarshallingContext(HierarchicalStreamWriter writer,
							ConverterLookup converterLookup, Mapper mapper) {
						return new ReferenceByXPathWithDependencysMarshaller(writer, converterLookup, mapper,
								ReferenceByXPathMarshallingStrategy.ABSOLUTE);
					}
				});

		// for (String ignore : ignores) {
		// int lastDot = ignore.lastIndexOf(".");
		// String clz = ignore.substring(0, lastDot);
		// String fld = ignore.substring(lastDot + 1);
		// try {
		// xStreamInst.omitField(Class.forName(clz), fld);
		// } catch (Exception ex) {
		// }
		// }
		return xStreamInst;
	}

	public static void main(String[] args) {
		HashMap<Object, Object> hm = new HashMap<Object, Object>();
		// hm.put(new LinkedList<String>(), "baz");
		hm.put("a", "baz");
		hm.put("b", "z");

		hm.put("aaa", "baz");
		// System.out.println(HeapWalker.serialize(hm));
	}

	// During Serialization we should NOT interfere by generating reads/writes !
	public static synchronized Element serialize(Object obj) {
		// if (DependencyInfo.IN_CAPTURE)
		// {
		// return null;
		// }
		// if(obj != null &&
		// obj.getClass().getName().contains("edu.columbia.cs.psl.testdepends"))
		// return null;

		boolean previousState = DependencyInfo.IN_CAPTURE;

		if (debug) {
			System.out.println("HeapWalker.serialize() " + obj);
		}
		//
		try {
			// Disable conflict tracking
			DependencyInfo.IN_CAPTURE = true;
			Element root = new Element("root");
			// FIXME Not sure what this is supposed to do ...
			JDomHackWriter wr = new JDomHackWriter(root, SKIP_VALUES);
			// getXStreamInstance().marshal(obj, new CompactWriter(sw));
			getXStreamInstance().marshal(obj, wr);
			return root;
		} catch (Throwable t) {
			System.err.println("ERROR Unable to serialize object!");
			t.printStackTrace();
			return null;
		} finally {
			DependencyInfo.IN_CAPTURE = previousState;
			if (debug) {
				System.out.println("HeapWalker.serialize() Resetting IN_CAPTURE TO " + DependencyInfo.IN_CAPTURE);
			}
		}
	}

	static LinkedList<WeakReference<DependencyInfo>> lastGenReachable = new LinkedList<WeakReference<DependencyInfo>>();

	/**
	 * This method triggers for whatever reason a write on Dependendy objects if
	 * they were not initialized. For string and enums this is wrong, because
	 * with null the deps should not be updated !
	 * 
	 * This reset the conflict on dep info as well...
	 * 
	 * 
	 * FIXME This shall be check again....
	 * 
	 * @param root
	 * @param obj
	 * @param alreadyInConflict
	 */
	static void visitField(StaticField root, Field _field, Object obj, boolean alreadyInConflict) {
		// This is called both for static fields and regular fields

		// How is this possible ?!
		if (_field == null) {
			return;
		}
		// Black list also fields inside the instances non only static fields
		// Why _field or _field.getName() are null ?
		if (_field.getName().contains("serialVersionUID")) {
			return;
		}

		// XXX Field can be null if this objects belong to an array !!
		// System.out.println("HeapWalker.visitField() " + _field);
		// _field.getDeclaringClass() + "." + _field.getName()
		// + " from SF " + root.field + " value " + obj);

		// obj is data
		if (obj != null) {
			//
			DependencyInfo inf = TagHelper.getOrFetchTag(obj);

			if (inf.getCrawledGen() == DependencyInfo.CURRENT_TEST_COUNT) {
				lastGenReachable.add(new WeakReference<DependencyInfo>(inf));
				// return ret; //Not actually OK to bail early if we want to
				// know everything that points to everything!
			}

			// Did we already get here from this sf root this generation though?
			boolean aliasFound = false;
			StaticField duplicate = null;

			if (inf.fields == null) {
				inf.fields = new StaticField[10];
			} else {
				// Those are the sf associated to this object by some previous
				// run...
				for (int i = 0; i < inf.fields.length; i++) {
					// for (StaticField sf : inf.fields) {
					// Null Sf are possible because this is an array of nulls...
					if (inf.fields[i] == null)
						continue;

					if (root == inf.fields[i]) {
						// System.out.println("HeapWalker.visitField() Alias
						// FOUND ");
						aliasFound = true;
					} else if (root.field.equals(inf.fields[i].field)) {
						// remote duplicate
						// System.out.println("HeapWalker.visitField() Remove
						// old duplicate of "
						// + root.field.getDeclaringClass() + "." +
						// root.field.getName());
						inf.fields[i] = null;
					}
				}
				// TODO Compress array if necessary ?!
			}

			if (inf.isConflict()) {
				inf.clearConflict();
			}
			// To not propagate this for objects that are not
			// primitives os String or Enums!
			if (inf.getWriteGen() == 0 && //
					(obj instanceof String || obj instanceof Enum<?>))
			// If the object is null ?
			// obj.getClass().isEnum()||
			// Enum.class.isAssignableFrom(obj.getClass())))
			{
				System.out.println("HeapWalker.visitField() Forcing write for " + inf.printMe() + " " + obj);
				inf.write();
			}

			if (aliasFound) {
				return;
			}

			boolean inserted = false;
			for (int i = 0; i < inf.fields.length && !inserted; i++) {
				if (inf.fields[i] == null) {
					inf.fields[i] = root;
					inserted = true;
					// System.out.println("HeapWalker.visitField() Associating "
					// + root.field + " to dep object for: "
					// + inf.printMe());
				}
			}
			if (!inserted) {
				// out of space
				int k = inf.fields.length;
				StaticField[] tmp = new StaticField[inf.fields.length + 10];
				System.arraycopy(inf.fields, 0, tmp, 0, inf.fields.length);
				inf.fields = tmp;
				inf.fields[k] = root;
			}

			// If we get to a dep info object - like
			if (obj.getClass() == DependencyInfo.class) {
				// System.out.println("HeapWalker.visitField() Returning from "
				// + inf.printMe());
				return;
			}

			// if (!obj.getClass().isArray()) {
			if (!_field.getType().isArray()) {

				inf.setCrawledGen();

				Set<Field> allFields = new HashSet<Field>();
				try {
					// Field[] declaredFields =
					// obj.getClass().getDeclaredFields();
					// Field[] fields = obj.getClass().getFields();
					// This apparently raises some exception
					Field[] declaredFields = _field.getType().getDeclaredFields();
					Field[] fields = _field.getType().getFields();
					//
					allFields.addAll(Arrays.asList(declaredFields));
					allFields.addAll(Arrays.asList(fields));

					// System.out.println("HeapWalker.visitField() All Filed
					// from obj " + obj + " \n " + allFields);

				} catch (NoClassDefFoundError e) {
					e.printStackTrace();
				} catch (Exception e) {
					e.printStackTrace();
				}
				///
				for (Field f : allFields) {
					// Primitive and the other fields are skipped, but NOT their
					// dependencyInfo taint !

					// In any case those fields do not have a taint !
					if ((f.getType().isPrimitive() || f.getType().isEnum()
							|| f.getType().isAssignableFrom(String.class))) {
						// System.out.println("HeapWalker.visitField() Skip the
						// visit to " + f.getName());
						continue;
					}

					// At this point only the tainted fields or the taint
					// themselves are here..

					// At this point here we process only the DepInfo
					if (f.getType().equals(DependencyInfo.class)) {
						try {
							// System.out.println("HeapWalker.visitField() Visit
							// DEP_INFO");

							//
							f.setAccessible(true);
							// Look for the field corresponding to the taint
							for (Field f1 : allFields) {
								if (f1.getType().equals(DependencyInfo.class)) {
									// Skip Taints
									continue;
								}

								//
								if (f1.getName().equals(f.getName().replace("__DEPENDENCY_INFO", ""))) {
									// System.out.println("HeapWalker.visitField()
									// Found taint corresponding to prim "
									// + f1.getName());
									f1.setAccessible(true);
									// This is the tricky part, a String which
									// is written to be null must be propagated
									// anyway
									// if (f1.get(obj) == null) {
									// System.out.println(
									// "HeapWalker.visitField() Do not propogate
									// taint for null objects "
									// + f1.getName());
									//
									// } else {
									// Propagate the field to the TAINT
									// object not the primitive one !!
									visitField(root, f, f.get(obj), alreadyInConflict);
									// }
								}
							}

						} catch (IllegalArgumentException e) {
							e.printStackTrace();
						} catch (IllegalAccessException e) {
							e.printStackTrace();
						}
					}
					// else {
					// if (f.getName().equals("_repoKind")) {
					// // System.out.println("HeapWalker.visitField() Skip " +
					// f.getName());
					// }
					// }
				}
			} else if (obj.getClass().isArray() && !obj.getClass().getComponentType().isPrimitive()) {
				inf.setCrawledGen();
				Object[] ar = (Object[]) obj;
				for (Object o : ar) {
					visitField(root, null, o, alreadyInConflict);
				}
			}
		}
	}
}
