package example;

import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import edu.gmu.swe.datadep.HeapWalker;
import edu.gmu.swe.datadep.StaticFieldDependency;

public class ExampleDepDetectionITCase {

	static Example bar = null;

	@BeforeClass
	public static void setupWhitelist(){
		HeapWalker.clearWhitelist();
		HeapWalker.addToWhitelist("example");
		System.out.println("Manually forcing whitelist");
	}

	@Test
	public void testAll(){
		test1();
		test2();
		test3();
		test4();
	}

//	@Test
	public void test1(){
		Example baz = new Example(false, Example.Ex.BAR);
		System.out.println("test1 "+HeapWalker.walkAndFindDependencies("test1", "test1"));
	}

//	@Test
	public void test2(){
		bar = new Example(false, Example.Ex.BAR);
		System.out.println("test2 "+HeapWalker.walkAndFindDependencies("test2", "test2"));

	}

//	@Test
	public void test3(){
		bar.isBar();
		bar.bar();
		System.out.println("test3 "+HeapWalker.walkAndFindDependencies("test3", "test3"));
	}

//	@Test
	public void test4(){
		assertTrue(bar.getBaz().equals(Example.Ex.BAR));
		bar.setBaz(Example.Ex.FOO);
		assertTrue(bar.getBaz().equals(Example.Ex.FOO));

		List<StaticFieldDependency> deps = HeapWalker.walkAndFindDependencies("test4", "test4");
		StaticFieldDependency dep = deps.get(0);
		assertTrue("Wrong Generation",dep.depGen == 2);
		System.out.println("test4 "+deps);
	}

//	static int i = 1;
//
//	@After
//	public void teardown(){
//		i++;
//		System.out.println(HeapWalker.walkAndFindDependencies("test"+i, "test"+i));
//	}

	@AfterClass
	public static void resetHeapWalker()
	{
		HeapWalker.resetAllState();
	}

	static class Example{

		public enum Ex{
			FOO, BAR
		}

		private boolean boo;
		private Ex baa;

		public Example(boolean bar, Ex baz){
			this.boo = bar;
			this.baa = baz;
		}

		public Ex getBaz(){
			return this.baa;
		}

		public void setBaz(Ex baz){
			this.baa = baz;
		}

		public boolean isBar(){
			return boo;
		}

		public void bar(){
			boo = true;
		}
	}

}
