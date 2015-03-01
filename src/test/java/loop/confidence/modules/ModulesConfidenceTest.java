package loop.confidence.modules;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import loop.AnnotatedError;
import loop.LoopCompileException;
import loop.LoopTest;
import loop.TestFilesLoader;

import org.junit.Assert;
import org.junit.Test;

/**
 * Confidence tests run a bunch of semi-realistic programs and assert that their results are as expected. This is meant
 * to be our functional regression test suite.
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public class ModulesConfidenceTest extends LoopTest {
  @Test(expected = LoopCompileException.class)
  public final void requireFaultyLoopModule() {
    Assert.assertEquals(new Date(10), TestFilesLoader.run("loop/confidence/modules/require_loop_error_1.loop"));
  }

  @Test(expected = LoopCompileException.class)
  public final void requireLoopModuleTwiceCausesVerifyError() {
    Assert.assertEquals(10, TestFilesLoader.run("loop/confidence/modules/require_loop_error_2.loop"));
  }

  @Test
  public final void requireLoopModule() {
    Assert.assertEquals(30, TestFilesLoader.run("loop/confidence/modules/require_loop_mod_1.loop"));
  }

  @Test
  public final void requireAliasedLoopModule() {
    Assert.assertEquals("hi", TestFilesLoader.run("loop/confidence/modules/require_loop_mod_5.loop"));
  }

  @Test
  public final void requireLoopModuleTransitivelyErrorsIfMissingModuleDecl() {
    List<AnnotatedError> errors = null;
    try {
      TestFilesLoader.run("loop/confidence/modules/require_loop_mod_3.loop");
      Assert.fail();
    } catch (final LoopCompileException e) {
      errors = e.getErrors();
    }

    Assert.assertNotNull(errors);
    Assert.assertEquals(1, errors.size());
  }

  @Test(expected = LoopCompileException.class)
  public final void requireLoopModuleHidesTransitiveDeps() {
    Assert.assertEquals(4, TestFilesLoader.run("loop/confidence/modules/require_loop_mod_4.loop"));
  }

  @Test
  public final void requireLoopModuleRaiseAndCatchException() {
    Assert.assertEquals("now is the winter of our discontent!",
        TestFilesLoader.run("loop/confidence/modules/require_loop_mod_2.loop"));
  }

  @Test
  public final void preludeConfidence1() {
    Assert.assertEquals(true, TestFilesLoader.run("loop/confidence/modules/prelude_conf_1.loop"));
  }

  @Test
  public final void requireJavaClass() {
    Assert.assertEquals(new Date(10), TestFilesLoader.run("loop/confidence/modules/require_java.loop"));
  }

  @Test
  public final void requireLoopClass() {
    final Map<String, Object> map = new HashMap<String, Object>();
    map.put("left", 1);
    map.put("right", 2);

    Assert.assertEquals(map, TestFilesLoader.run("loop/confidence/modules/require_class.loop"));
  }

  @Test
  public final void requireLoopClassWithAlias() {
    final Map<String, Object> map = new HashMap<String, Object>();
    map.put("left", 10);
    map.put("right", 20);

    Assert.assertEquals(map, TestFilesLoader.run("loop/confidence/modules/require_class_3.loop"));
  }

  @Test(expected = LoopCompileException.class)
  public final void requireLoopClassWithAliasFails() {
    final Map<String, Object> map = new HashMap<String, Object>();
    map.put("left", 10);
    map.put("right", 20);

    Assert.assertEquals(map, TestFilesLoader.run("loop/confidence/modules/require_class_4.loop"));
  }

  @Test(expected = LoopCompileException.class)
  public final void requireLoopClassWithAliasFails2() {
    final Map<String, Object> map = new HashMap<String, Object>();
    map.put("left", 10);
    map.put("right", 20);

    Assert.assertEquals(map, TestFilesLoader.run("loop/confidence/modules/require_class_5.loop"));
  }

  @Test(expected = LoopCompileException.class)
  public final void requireLoopClassHidesTransitiveDeps() {
    final Map<String, Object> map = new HashMap<String, Object>();
    map.put("left", 1);
    map.put("right", 2);

    Assert.assertEquals(map, TestFilesLoader.run("loop/confidence/modules/require_class_2.loop"));
  }

  @Test(expected = LoopCompileException.class)
  public final void requireJavaClassFails() {
    Assert.assertEquals(new Date(10), TestFilesLoader.run("loop/confidence/modules/require_java_error.loop"));
  }

  @Test
  public final void requiredModuleHidesPrivateFunctions() {
    Assert.assertEquals("1", TestFilesLoader.run("loop/confidence/modules/require_hides_private.loop"));
  }

  @Test(expected = LoopCompileException.class)
  public final void requiredModuleHidesPrivateFunctionsError() {
    Assert.assertEquals("1", TestFilesLoader.run("loop/confidence/modules/require_hides_private_err.loop"));
  }

  @Test
  public final void requireFileModule() {
    Assert.assertTrue(TestFilesLoader.run("loop/confidence/modules/require_file.loop").toString()
        .contains("http://looplang.org"));
  }
}
