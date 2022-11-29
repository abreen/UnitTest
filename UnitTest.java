/*
 * UnitTest.java
 *
 * A simple unit testing framework.
 */

import java.io.*;
import java.lang.annotation.*;
import java.lang.management.ManagementFactory;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.List;
import java.util.LinkedList;

@Retention(RetentionPolicy.RUNTIME)
@interface Test {
    String value() default "";
}

@Retention(RetentionPolicy.RUNTIME)
@interface Timeout {
    long value() default UnitTest.TIMEOUT_MILLISECONDS;
}

@Retention(RetentionPolicy.RUNTIME)
@interface Skip { }

public class UnitTest {
    public static final long TIMEOUT_MILLISECONDS = 2000;

    private List<Method> methods = new LinkedList<>();
    private long defaultTimeout = TIMEOUT_MILLISECONDS;

    public static void main(String[] args) throws InterruptedException {
        String pattern = args.length > 0 ? args[0].trim() : "";
        if (!pattern.isEmpty()) {
            System.out.println("search pattern: " + pattern);
        }

        try {
            UnitTest ut = new UnitTest();
            ut.loadClassesFromClasspath();
            ut.runTestCases(ut.buildTestCases(pattern), System.out);
        } catch (IOException | ExecutionException | URISyntaxException e) {
            System.err.println("cannot run unit tests: " + e);
            System.exit(1);
        }
    }

    public void loadClassesFromClasspath()
        throws IOException, URISyntaxException
    {
        ClassLoader loader = getClass().getClassLoader();
        loader.setDefaultAssertionStatus(true);

        Enumeration<URL> classResources = loader.getResources("");
        while (classResources.hasMoreElements()) {
            URI fileUri = classResources.nextElement().toURI();
            findClasses(loader, new File(fileUri), 12);
        }
    }

    private void findClasses(ClassLoader loader, File dir, int depth) {
        if (dir == null || !dir.isDirectory() || depth <= 0) {
            return;
        }
        File[] files = dir.listFiles();
        for (File f : Objects.requireNonNull(files)) {
            String fileName = f.getName();
            if (f.isFile() && fileName.toLowerCase().endsWith(".class")) {
                loadClassUsingFileName(loader, f);
            } else if (f.isDirectory()) {
                findClasses(loader, f, depth - 1);
            }
        }
    }

    private void loadClassUsingFileName(ClassLoader loader, File f) {
        String fileName = f.getName();
        String className = fileName.substring(0,  fileName.length() - 6);
        try {
            addAnnotatedMethodsFromClass(loader.loadClass(className));
        } catch (ClassNotFoundException e) {
            System.err.println("could not load class: " + className);
        }
    }

    public void addAnnotatedMethodsFromClass(Class<?> c) {
        for (Method method : c.getMethods()) {
            if (method.getAnnotation(Test.class) != null &&
                Modifier.isStatic(method.getModifiers())
            ) {
                this.addMethod(method);
            }
        }
    }

    public void addMethod(Method method) {
        methods.add(method);
    }

    public void setDefaultTimeout(long defaultTimeout) {
        this.defaultTimeout = defaultTimeout;
    }

    public List<TestCase> buildTestCases(String pattern) {
        List<TestCase> cases = new LinkedList<>();
        for (Method method : methods) {
            if (TestCase.getFullMethodName(method).contains(pattern)) {
                Timeout t = method.getAnnotation(Timeout.class);
                long timeoutValue = t != null ? t.value() : defaultTimeout;
                cases.add(new TestCase(method, timeoutValue));
            }
        }
        return cases;
    }

    public void runTestCases(List<TestCase> cases, PrintStream out)
        throws ExecutionException, InterruptedException
    {
        CompletionService<TestCase> service =
            new ExecutorCompletionService<>(Executors.newWorkStealingPool());

        int numSkipped = 0;
        for (TestCase testCase : cases) {
            if (hasSkipAnnotation(testCase.getMethod())) {
                testCase.printResult(out);
                numSkipped++;
            } else {
                service.submit(testCase);
            }
        }

        List<TestCase> failed = new LinkedList<>();
        for (int i = 0; i < cases.size() - numSkipped; i++) {
            TestCase testCase = service.take().get();
            if (testCase.getError() != null) {
                failed.add(testCase);
            } else {
                testCase.printResult(out);
            }
        }
        for (TestCase testCase : failed) {
            testCase.printResult(out);
        }

        int numCases = cases.size(), numFailed = failed.size();
        int numPassed = numCases - numFailed - numSkipped;
        out.printf(
            "%d tests, %d skipped, %d passed, %d failed\n",
            numCases, numSkipped, numPassed, numFailed
        );
    }

    private static boolean hasSkipAnnotation(Method method) {
        return method.getAnnotation(Skip.class) != null ||
               method.getDeclaringClass().getAnnotation(Skip.class) != null;
    }
}

class TestCase implements Callable<TestCase> {
    private Method method;
    private long timeout;
    private volatile long start, end;
    private volatile Throwable error;

    public TestCase(Method method, long timeout) {
        this.method = method;
        this.timeout = timeout;
    }

    public boolean finished() {
        return start > 0 && end > 0;
    }

    public Method getMethod() {
        return method;
    }

    public Throwable getError() {
        return error;
    }

    @SuppressWarnings("deprecation")
    public TestCase call() {
        Thread methodInvoker = new Thread(() -> {
            start = System.nanoTime();
            try {
                method.invoke(null);
            } catch (IllegalAccessException e) {
                error = e;
            } catch (InvocationTargetException e) {
                if (e.getTargetException() instanceof ThreadDeath) {
                    throw (ThreadDeath)e.getTargetException();
                }
                error = e.getTargetException();
            } finally {
                end = System.nanoTime();
            }
        });

        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(timeout);
                if (methodInvoker.isAlive()) {
                    error = new InterruptedException("timed out");
                    methodInvoker.interrupt();
                    methodInvoker.stop();
                }
            } catch (InterruptedException ignored) { }
        });

        methodInvoker.start();
        if (!virtualMachineIsDebugging()) {
            watchdog.start();
        }
        try {
            methodInvoker.join();
        } catch (InterruptedException ignored) { }
        return this;
    }

    public void printResult(PrintStream out) {
        out.print("[" + getStatus() + "] ");
        out.print(getFullMethodName(method));
        String description = getDescription();
        if (description != null) {
            out.print(": " + description);
        }
        out.print(" (" + getDurationString() + ")");
        if (error != null) {
            printError(out);
        }
        out.println();
    }

    private void printError(PrintStream out) {
        if (error instanceof InterruptedException) {
            out.printf("\n       test case timed out after %,d ms",  timeout);
            return;
        }
        if (error instanceof AssertionError) {
            out.print("\n       assertion failed");
            if (error.getMessage() != null) {
                out.print(": " + error.getMessage());
            }
        } else {
            out.print("\n       " + error);
        }
        out.print("\n       at " + error.getStackTrace()[0]);
    }

    private String getStatus() {
        String green = "\u001b[32m", red = "\u001b[31m", reset = "\u001b[0m";
        if (!finished()) {
            return "skip";
        } else if (error == null) {
            return green + "pass" + reset;
        }
        return red + "fail" + reset;
    }

    private String getDurationString() {
        return (end > start ? formatNanosToMillis(end - start) : "?") + " ms";
    }

    public String getDescription() {
        Test test = method.getAnnotation(Test.class);
        return test != null && !test.value().isEmpty() ? test.value() : null;
    }

    public String toString() {
        return String.format(
            "TestCase(method=%s timeout=%d start=%d end=%d error=%s)",
            method, timeout, start, end, error
        );
    }

    public static String getFullMethodName(Method method) {
        return method.getDeclaringClass().getName() + "." + method.getName();
    }

    public static String formatNanosToMillis(long nanos) {
        return String.format("%,.2f", nanos / 1e6);
    }

    private static boolean virtualMachineIsDebugging() {
        String env = System.getenv("JAVA_TOOL_OPTIONS") + "";
        List<String> vm = ManagementFactory.getRuntimeMXBean()
                                           .getInputArguments();
        return env.contains("-Xdebug") || env.contains("-agentlib:jdwp") ||
               vm.stream().anyMatch(s -> s.contains("-Xdebug") ||
                                         s.startsWith("-agentlib:jdwp"));
    }
}
