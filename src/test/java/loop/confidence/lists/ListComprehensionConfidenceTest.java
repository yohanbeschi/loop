package loop.confidence.lists;

import java.util.Arrays;
import java.util.Iterator;

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
public class ListComprehensionConfidenceTest extends LoopTest {
  @Test
  public final void listRanges() {
    Assert
        .assertEquals(Arrays.asList(Arrays.asList(3, 4, 5)), TestFilesLoader.run("loop/confidence/lists/ranges.loop"));
  }

  @Test
  public final void identityComprehension() {
    Assert.assertEquals(Arrays.asList(10, 20, 30), TestFilesLoader.run("loop/confidence/lists/projection.loop"));
  }

  @Test
  public final void slicePortions() {
    Assert.assertEquals(Arrays.asList(1, 2, 3), TestFilesLoader.run("loop/confidence/lists/slices.loop"));
  }

  @Test
  public final void slicePortions2() {
    Assert.assertEquals(Arrays.asList(1, 2, 3), TestFilesLoader.run("loop/confidence/lists/slices_2.loop"));
  }

  @Test
  public final void slicePortionsTo() {
    Assert.assertEquals(Arrays.asList(2, 3, 4, 5, 6), TestFilesLoader.run("loop/confidence/lists/slices_to.loop"));
  }

  @Test
  public final void slicePortionsStrings() {
    Assert.assertEquals("hel", TestFilesLoader.run("loop/confidence/lists/slices_string.loop"));
  }

  @Test
  public final void slicePortionsStrings2() {
    Assert.assertEquals("ell", TestFilesLoader.run("loop/confidence/lists/slices_string_2.loop"));
  }

  @Test
  public final void slicePortionsStrings3() {
    Assert.assertEquals("hell", TestFilesLoader.run("loop/confidence/lists/slices_string_3.loop"));
  }

  @Test
  public final void slicePortionsStrings4() {
    Assert.assertEquals("lo", TestFilesLoader.run("loop/confidence/lists/slices_string_4.loop"));
  }

  @Test
  public final void slicePortions3() {
    Assert.assertEquals(Arrays.asList(2, 3), TestFilesLoader.run("loop/confidence/lists/slices_3.loop"));
  }

  @Test
  public final void identityFilterComprehension() {
    Assert.assertEquals(Arrays.asList(10, 20), TestFilesLoader.run("loop/confidence/lists/projection_filter.loop"));
  }

  @Test
  public final void expressionProjectComprehension() {
    Assert
        .assertEquals(Arrays.asList(100, 200, 300), TestFilesLoader.run("loop/confidence/lists/projection_expr.loop"));
  }

  @Test
  public final void expressionProjectComprehension2() {
    Assert.assertEquals(Arrays.asList(100, 200, 300, 400),
        TestFilesLoader.run("loop/confidence/lists/projection_expr2.loop"));
  }

  @Test
  public final void expressionProjectComprehension3() {
    Assert.assertEquals(Arrays.asList(100, 200, 300, -1, 400),
        TestFilesLoader.run("loop/confidence/lists/projection_expr3.loop"));
  }

  @Test
  public final void expressionProjectComprehension4() {
    Assert.assertEquals(Arrays.asList(100, 200, 300),
        TestFilesLoader.run("loop/confidence/lists/projection_expr4.loop"));
  }

  @Test
  public final void expressionProjectComprehension5() {
    Assert.assertEquals(Arrays.asList(100, 200, 300, -1, 400),
        TestFilesLoader.run("loop/confidence/lists/projection_expr5.loop"));
  }

  @Test
  public final void expressionProjectComprehension6() {
    Assert.assertEquals(Arrays.asList(100, 200, 300, 400, -1),
        TestFilesLoader.run("loop/confidence/lists/projection_expr6.loop"));
  }

  @Test
  public final void functionProjectComprehension() {
    Assert.assertEquals(Arrays.asList(20, 40, 60),
        TestFilesLoader.run("loop/confidence/lists/projection_function.loop"));
  }

  @Test
  public final void functionProjectComprehensionAltFilter() {
    Assert.assertEquals(Arrays.asList(20, 40, 80),
        TestFilesLoader.run("loop/confidence/lists/projection_function_2.loop"));
  }

  // @Test
  public final void iteratorComprehension() {
    final Object iterator = TestFilesLoader.run("loop/confidence/lists/iter_project_1.loop");
    Assert.assertTrue(iterator instanceof Iterator);
    // assertEquals(Arrays.asList(20, 40, 80), iterator);
  }
}
