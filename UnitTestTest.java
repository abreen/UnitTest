/*
 * UnitTestTest.java
 *
 * Unit tests of the UnitTest.java framework.
 */

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.concurrent.ExecutionException;
import java.util.List;

public class UnitTestTest {

    public static void missingTestAnnotation() throws AssertionError {
        assert false : "this method should not run because it does not have " +
                       "the @Test annotation";
    }

    @Test("only methods with the @Test annotation should run")
    public static void testOnlyAnnotated() {
        UnitTest ut = new UnitTest();
        ut.addAnnotatedMethodsFromClass(UnitTestTest.class);
        assert ut.buildTestCases("missingTestAnnotation").isEmpty();
    }

    @Test
    public void nonStaticMethod() throws AssertionError {
        assert false : "this method should not run because it is not static";
    }

    @Test("only static methods should run")
    public static void testOnlyStatic() {
        UnitTest ut = new UnitTest();
        ut.addAnnotatedMethodsFromClass(UnitTestTest.class);
        assert ut.buildTestCases("nonStaticMethod").isEmpty();
    }

    @Skip
    public static void skippedMethod() throws AssertionError {
        assert false : "this method should be skipped because it has the " +
                       "@Skip annotation";
    }

    @Test("skip methods with @Skip annotation")
    public static void testSkipped()
        throws NoSuchMethodException, ExecutionException, InterruptedException
    {
        UnitTest ut = new UnitTest();
        ut.addMethod(UnitTestTest.class.getMethod("skippedMethod"));
        TestCase testCase = ut.buildTestCases("skippedMethod").get(0);
        ut.runTestCases(List.of(testCase), blackHole());
        assert !testCase.finished();
    }

    @Skip
    static class Inner {
        public static void skippedInner() throws AssertionError {
            assert false : "this method should be skipped because its class " +
                           "has the @Skip annotation";
        }
    }

    @Test("skip classes with @Skip annotation")
    public static void testSkippedClass()
        throws NoSuchMethodException, ExecutionException, InterruptedException
    {
        UnitTest ut = new UnitTest();
        ut.addMethod(Inner.class.getMethod("skippedInner"));
        TestCase testCase = ut.buildTestCases("skippedInner").get(0);
        ut.runTestCases(List.of(testCase), blackHole());
        assert !testCase.finished();
    }

    public static void sleepFor5() throws InterruptedException {
        Thread.sleep(5000);
    }

    @Test("long running test methods should be stopped")
    public static void testSleeper()
        throws NoSuchMethodException, ExecutionException, InterruptedException
    {
        UnitTest ut = new UnitTest();
        ut.setDefaultTimeout(1);
        ut.addMethod(UnitTestTest.class.getMethod("sleepFor5"));
        TestCase testCase = ut.buildTestCases("sleepFor5").get(0);
        ut.runTestCases(List.of(testCase), blackHole());
        assert testCase.getError() instanceof InterruptedException;
    }

    @Timeout(20)
    public static void sleepFor10() throws InterruptedException {
        Thread.sleep(10);
    }

    @Test("@Timeout annotation should override default timeout")
    public static void testTimeout()
        throws NoSuchMethodException, ExecutionException, InterruptedException
    {
        UnitTest ut = new UnitTest();
        ut.setDefaultTimeout(1);
        ut.addMethod(UnitTestTest.class.getMethod("sleepFor10"));
        TestCase testCase = ut.buildTestCases("sleepFor10").get(0);
        ut.runTestCases(List.of(testCase), blackHole());
        assert testCase.getError() == null;
    }

    public static void infiniteLoop1() {
        while (true) { }
    }

    public static void infiniteLoop2() {
        while (true) { }
    }

    public static void infiniteLoop3() {
        while (true) { }
    }

    public static void infiniteLoop4() {
        while (true) { }
    }

    public static void infiniteLoop5() {
        while (true) { }
    }

    public static void infiniteLoop6() {
        while (true) { }
    }

    public static void infiniteLoop7() {
        while (true) { }
    }

    public static void infiniteLoop8() {
        while (true) { }
    }

    @Test("an infinite loop should be stopped")
    public static void testOneInfiniteLoop()
        throws NoSuchMethodException, ExecutionException, InterruptedException
    {
        UnitTest ut = new UnitTest();
        ut.setDefaultTimeout(10);
        ut.addMethod(UnitTestTest.class.getMethod("infiniteLoop1"));
        TestCase testCase = ut.buildTestCases("infiniteLoop1").get(0);
        ut.runTestCases(List.of(testCase), blackHole());
        assert testCase.getError() instanceof InterruptedException;
    }

    @Test("multiple infinite loops should be stopped")
    public static void testManyInfiniteLoops()
        throws NoSuchMethodException, ExecutionException, InterruptedException
    {
        UnitTest ut = new UnitTest();
        ut.setDefaultTimeout(10);

        for (int i = 1; i <= 8; i++) {
            ut.addMethod(UnitTestTest.class.getMethod("infiniteLoop" + i));
        }

        List<TestCase> cases = ut.buildTestCases("infiniteLoop");
        ut.runTestCases(cases, blackHole());
        for (TestCase testCase : cases) {
            assert !testCase.finished() ||
                   testCase.getError() instanceof InterruptedException;
        }
    }

    private static PrintStream blackHole() {
        return new PrintStream(new OutputStream() {
            @Override public void write(int b) { }
        });
    }
}
