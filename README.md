# UnitTest.java

A simple, one-file implementation of a test framework for Java.

*   As small as possible, but no smaller. The entire API surface consists of
    three annotations: `@Test`, `@Skip` and `@Timeout()`.
*   Use `@Test` with any static method to declare the method as a test case.
    Pass a string to describe the behavior being tested, for example:
    `@Test("should throw exception when passed a negative number")`
*   Use `@Skip` on a class or method to temporarily skip test cases.
    `UnitTest.java` will display the test case but not run it.
*   Use `@Timeout(5000)` to change the amount of time `UnitTest.java` will
    wait for a test case to finish. The default timeout is 2,000 milliseconds
    (two seconds).

## Running tests

Run `UnitTest.java` with no command line arguments. It will search the classpath
for methods annotated with `@Test`, run each of them, and print the results. 
Failures are always printed last. For example:

    > java UnitTest
    [skip] HeapTest.testRemove (? ms)
    [pass] HeapTest.testException (0.05 ms)
    [pass] HeapTest.testIsEmpty (0.03 ms)
    [fail] HeapTest.testInsert (0.19 ms)
       java.util.NoSuchElementException
       at Heap.remove(Heap.java:75)
    4 tests, 1 skipped, 2 passed, 1 failed

A command line argument will limit the search to classes/methods with the given
pattern. For example, to run only test cases in the `Foobar` class, run

    > java UnitTest Foobar
