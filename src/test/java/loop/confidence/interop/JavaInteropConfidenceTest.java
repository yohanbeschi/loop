package loop.confidence.interop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Date;

import loop.LoopTest;
import loop.TestFilesLoader;

import org.junit.Test;

/**
 * Confidence tests run a bunch of semi-realistic programs and assert that their results are
 * as expected. This is meant to be our functional regression test suite.
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public class JavaInteropConfidenceTest extends LoopTest {
  public static final Integer CONSTANT = Long.valueOf(new Date().getTime()).intValue();

  @Test
  public final void newInstanceByReflection() {
    assertEquals("",
        TestFilesLoader.run("loop/confidence/interop/new_instance.loop"));
  }

  @Test
  public final void newInstanceByReflectionWithForName() {
    assertEquals("",
        TestFilesLoader.run("loop/confidence/interop/new_instance_2.loop"));
  }

  @Test
  public final void newInstanceByReflectionWithForNameCalendar() {
    Date now = new Date();
    Object result = TestFilesLoader.run("loop/confidence/interop/new_instance_3.loop");
    assertTrue(result instanceof Date);
    assertTrue(now.before((Date) result) || now.equals(result));
  }

  @Test
  public final void newInstanceByReflectionCalendar() {
    Date now = new Date();
    Object result = TestFilesLoader.run("loop/confidence/interop/new_instance_4.loop");
    assertTrue(result instanceof Date);
    assertTrue(now.before((Date) result) || now.equals(result));
  }

  @Test
  public final void mainMethodArgs() {
    assertEquals(Arrays.asList("ARG1", "ARG2"),
        TestFilesLoader.run("loop/confidence/interop/main_args.loop", new String[] { "arg1", "arg2" }));
  }

  @Test
  public final void normalFunctionCall() {
    assertEquals("hello", TestFilesLoader.run("loop/confidence/interop/postfix_call_1.loop"));
  }

  @Test
  public final void forceCallAsJava() {
    assertEquals("hello", TestFilesLoader.run("loop/confidence/interop/postfix_call_2.loop"));
  }

  @Test
  public final void callOverloadedJavaMethod() {
    assertEquals("hello", TestFilesLoader.run("loop/confidence/interop/overloaded_call_1.loop"));
  }

  @Test
  public final void callAsLoopOverridingJava() {
    assertEquals("HELLO", TestFilesLoader.run("loop/confidence/interop/postfix_call_3.loop"));
  }

  @Test
  public final void callJavaStaticFunction() {
    assertTrue(1327205727145L /* epoch time when this test was written */ < Long.parseLong(
        TestFilesLoader.run("loop/confidence/interop/call_java_static.loop").toString()));
  }

  @Test
  public final void callJavaStaticConstant() {
    assertEquals(CONSTANT + 1, TestFilesLoader.run("loop/confidence/interop/call_java_static_2.loop"));
  }

  @Test
  public final void callJavaStaticConstantWithoutBackticks() {
    assertEquals(CONSTANT + 1, TestFilesLoader.run("loop/confidence/interop/call_java_static_3.loop"));
  }
}
