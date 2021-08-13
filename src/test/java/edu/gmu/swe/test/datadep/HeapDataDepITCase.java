package edu.gmu.swe.test.datadep;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.LinkedList;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import edu.gmu.swe.datadep.HeapWalker;
import edu.gmu.swe.datadep.StaticFieldDependency;


public class HeapDataDepITCase {
	static int foo;
	static long bar;
	static Object[] objArray;
	static HashMap<Object, Object> hm;
	static EvilContainer evil;
	@BeforeClass
	public static void setupWhitelist(){
		HeapWalker.clearWhitelist();
		HeapWalker.addToWhitelist("edu");
		System.out.println("Manually forcing whitelist");
	}
	
	@Test
	public void testAFieldSensitivityWrite() throws Exception {
		evil = new EvilContainer();
		evil.val = 4;
		evil.val2 = 5;
		LinkedList<StaticFieldDependency> deps = HeapWalker.walkAndFindDependencies("test1", "test1");
		evil.val2++;
		deps = HeapWalker.walkAndFindDependencies("test2", "test2");
		@SuppressWarnings("unused")
		int baz = evil.val2;
		deps = HeapWalker.walkAndFindDependencies("test3", "test3");
		assertEquals(1, deps.size());
		assertEquals("edu.gmu.swe.test.datadep.HeapDataDepITCase", deps.getFirst().field.getDeclaringClass().getName());
		assertEquals("evil", deps.getFirst().field.getName());
		assertEquals(2, deps.getFirst().depGen);
		assertTrue(deps.getFirst().value.contains("<val2 dependsOn=\"test2.test2\">6</val2>"));
		assertTrue(deps.getFirst().value.contains("<val>4</val>"));
	}
	@Test
	public void testFieldSensitivity() throws Exception{
		evil = new EvilContainer();
		evil.val = 4;
		evil.val2 = 5;
		LinkedList<StaticFieldDependency> deps = HeapWalker.walkAndFindDependencies("test1", "test1");
		@SuppressWarnings("unused")
		int baz = evil.val2;
		deps = HeapWalker.walkAndFindDependencies("test1", "test1");
		assertEquals(1, deps.size());
		assertEquals("edu.gmu.swe.test.datadep.HeapDataDepITCase", deps.getFirst().field.getDeclaringClass().getName());
		assertEquals("evil", deps.getFirst().field.getName());
		assertEquals(1, deps.getFirst().depGen);
		assertTrue(deps.getFirst().value.contains("<val2 dependsOn=\"test1.test1\">5</val2>"));
		assertTrue(deps.getFirst().value.contains("<val>4</val>"));
	}
	@Test
	public void testDirectStaticFieldDepOnPrim() throws Exception {
		foo = 5;
		LinkedList<StaticFieldDependency> deps = HeapWalker.walkAndFindDependencies("test1", "test1");
		assertEquals(0, deps.size());
		@SuppressWarnings("unused")
		int baz = foo;
		bar = 7;
		
		deps = HeapWalker.walkAndFindDependencies("test1", "test1");
//		System.out.println(deps);
		foo = 8; //should have no effect
		assertEquals(1, deps.size());
		assertEquals("edu.gmu.swe.test.datadep.HeapDataDepITCase", deps.getFirst().field.getDeclaringClass().getName());
		assertEquals("foo", deps.getFirst().field.getName());
		assertEquals(1, deps.getFirst().depGen);
		assertTrue(deps.getFirst().value.contains("<int>5</int>"));
		@SuppressWarnings("unused")
		
		
		long barr = bar;
		deps = HeapWalker.walkAndFindDependencies("test2", "test2");
//		System.out.println(deps);
		assertEquals(1, deps.size());
		assertEquals("edu.gmu.swe.test.datadep.HeapDataDepITCase", deps.getFirst().field.getDeclaringClass().getName());
		assertEquals("bar", deps.getFirst().field.getName());
		assertEquals(2, deps.getFirst().depGen);
		assertTrue(deps.getFirst().value.contains("<long>7</long>"));
	}
	

	@Test
	public void testDirectStaticFieldDepOnObj() throws Exception {
		LinkedList<StaticFieldDependency> deps = HeapWalker.walkAndFindDependencies("first_te3st1", "first_test31");
		hm = new HashMap<Object, Object>();
		hm.put(1, 2);
		hm.remove(1);
		deps = HeapWalker.walkAndFindDependencies("te3st1", "test31");
		assertEquals(0, deps.size());

		@SuppressWarnings("unused")
		int sz = hm.size();
		
		hm.put("fobbbbbo", "babbbbr");

		deps = HeapWalker.walkAndFindDependencies("test2333", "tes32");
		assertEquals(1, deps.size());
		assertEquals("edu.gmu.swe.test.datadep.HeapDataDepITCase", deps.getFirst().field.getDeclaringClass().getName());
		assertEquals("hm", deps.getFirst().field.getName());
		assertEquals(2, deps.getFirst().depGen);

		assertTrue(deps.getFirst().value.contains("<map size=\"0\" />")); 
		
		@SuppressWarnings("unused")
		int size = hm.size();

		deps = HeapWalker.walkAndFindDependencies("test3333", "test33");
		assertEquals(1, deps.size());
		assertEquals("edu.gmu.swe.test.datadep.HeapDataDepITCase", deps.getFirst().field.getDeclaringClass().getName());
		assertEquals("hm", deps.getFirst().field.getName());
		assertEquals(3, deps.getFirst().depGen);

		assertContains(deps.getFirst().value, "<map size=\"1\" size__dependsOn=\"test2333.tes32\">");
		assertContains(deps.getFirst().value, "<string>fobbbbbo</string>");
		assertContains(deps.getFirst().value, "<string>babbbbr</string>");
	}
	
	private static final void assertContains(String haystack, String needle)
	{
		assertTrue("String <"+haystack+"> should contain <"+needle+">", haystack.contains(needle));
	}
	
	private String xmlify(String str) {
		return "?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + str;
	}
	
	@Test
	public void testUpdatingXML()
	{
		evil = new EvilContainer();
		evil.val = 5;
		evil.val2 = 6;
		LinkedList<StaticFieldDependency> deps = HeapWalker.walkAndFindDependencies("t1", "t1");
		int bz = evil.val;
//		evil.val = 5;
		deps = HeapWalker.walkAndFindDependencies("t1", "t1");
		//System.out.println(deps);
	}
	
	static EvilContainer e1;
	@Test
	public void testSomeEvil() {
		e1=new EvilContainer();
		e1.val = 5;
		e1.val2 = 12;		
		LinkedList<StaticFieldDependency> deps = HeapWalker.walkAndFindDependencies("t1", "t1");
		assertEquals(0, deps.size());
		e1.val2=e1.val+7;
		deps = HeapWalker.walkAndFindDependencies("t1", "t1");
		assertEquals(1,deps.size());
		System.out.println(deps.getFirst().value);
		// TODO(gyori): Why do I get error here?
		assertTrue(deps.getFirst().value.contains("<edu.gmu.swe.test.datadep.HeapDataDepITCase_-EvilContainer><val dependsOn=\"t1.t1\">5</val><val2>12</val2></edu.gmu.swe.test.datadep.HeapDataDepITCase_-EvilContainer>"));
	}
	
	static EvilContainer[] evilArr;
	@Test
	public void testEvilArray(){
		evilArr = new EvilContainer[3];
		evilArr[0] = new EvilContainer(2,7);
		evilArr[1] = new EvilContainer(21,73);
		evilArr[2] = new EvilContainer(42,17);
		LinkedList<StaticFieldDependency> deps = HeapWalker.walkAndFindDependencies("t1", "t1");
		@SuppressWarnings("unused")
		int tmp = evilArr[1].val;
		deps = HeapWalker.walkAndFindDependencies("t1", "t1");
		assertEquals(1, deps.size());
		System.out.println(deps);
		
	}


	@Test
	public void testMultipleTestsWritingSamePrimitive(){
		foo = 1;
		LinkedList<StaticFieldDependency> deps = HeapWalker.walkAndFindDependencies("TestMultipleTestsWritingSamePrimitive", "test1");
		assertEquals(0, deps.size());

		foo = 2;
		deps = HeapWalker.walkAndFindDependencies("TestMultipleTestsWritingSamePrimitive", "test2");
		assertEquals(1, deps.size());
		StaticFieldDependency dep = deps.getFirst();
		assertEquals(1, dep.depGen);

		int c = foo;
		deps = HeapWalker.walkAndFindDependencies("TestMultipleTestsWritingSamePrimitive", "test3");
		assertEquals(1, deps.size());
		dep = deps.getFirst();
		assertEquals(2, dep.depGen);
	}

	@After
	public void resetHeapWalker()
	{
		HeapWalker.resetAllState();
	}
	static class EvilContainer{
		public EvilContainer(){	}
		public EvilContainer(int v, int v2) {
			this.val=v;
			this.val2=v2;
		}
		int val;
		int val2;
	}
}
