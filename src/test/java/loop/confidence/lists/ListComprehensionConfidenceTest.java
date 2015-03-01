package loop.confidence.lists;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Iterator;

import loop.LoopTest;
import loop.TestFilesLoader;

import org.junit.Test;

/**
 * Confidence tests run a bunch of semi-realistic programs and assert that their results are
 * as expected. This is meant to be our functional regression test suite.
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public class ListComprehensionConfidenceTest extends LoopTest {
  @Test @SuppressWarnings("unchecked")
  public final void listRanges() {
    assertEquals(Arrays.asList(Arrays.asList(3, 4, 5)), TestFilesLoader.run("loop/confidence/lists/ranges.loop"));
  }

  @Test
  public final void identityComprehension() {
    assertEquals(Arrays.asList(10, 20, 30), TestFilesLoader.run("loop/confidence/lists/projection.loop"));
  }

  @Test
  public final void slicePortions() {
    assertEquals(Arrays.asList(1, 2, 3), TestFilesLoader.run("loop/confidence/lists/slices.loop"));
  }

  @Test
  public final void slicePortions2() {
    assertEquals(Arrays.asList(1, 2, 3), TestFilesLoader.run("loop/confidence/lists/slices_2.loop"));
  }

  @Test
  public final void slicePortionsTo() {
    assertEquals(Arrays.asList(2, 3, 4, 5, 6), TestFilesLoader.run("loop/confidence/lists/slices_to.loop"));
  }

  @Test
  public final void slicePortionsStrings() {
    assertEquals("hel", TestFilesLoader.run("loop/confidence/lists/slices_string.loop"));
  }

  @Test
  public final void slicePortionsStrings2() {
    assertEquals("ell", TestFilesLoader.run("loop/confidence/lists/slices_string_2.loop"));
  }

  @Test
  public final void slicePortionsStrings3() {
    assertEquals("hell", TestFilesLoader.run("loop/confidence/lists/slices_string_3.loop"));
  }

  @Test
  public final void slicePortionsStrings4() {
    assertEquals("lo", TestFilesLoader.run("loop/confidence/lists/slices_string_4.loop"));
  }

  @Test
  public final void slicePortions3() {
    assertEquals(Arrays.asList(2, 3), TestFilesLoader.run("loop/confidence/lists/slices_3.loop"));
  }

  @Test
  public final void identityFilterComprehension() {
    assertEquals(Arrays.asList(10, 20),
        TestFilesLoader.run("loop/confidence/lists/projection_filter.loop"));
  }

  @Test
  public final void expressionProjectComprehension() {
    assertEquals(Arrays.asList(100, 200, 300),
        TestFilesLoader.run("loop/confidence/lists/projection_expr.loop"));
  }

  @Test
  public final void expressionProjectComprehension2() {
    assertEquals(Arrays.asList(100, 200, 300, 400),
        TestFilesLoader.run("loop/confidence/lists/projection_expr2.loop"));
  }

  @Test
  public final void expressionProjectComprehension3() {
    assertEquals(Arrays.asList(100, 200, 300, -1, 400),
        TestFilesLoader.run("loop/confidence/lists/projection_expr3.loop"));
  }

  @Test
  public final void expressionProjectComprehension4() {
    assertEquals(Arrays.asList(100, 200, 300),
        TestFilesLoader.run("loop/confidence/lists/projection_expr4.loop"));
  }

  @Test
  public final void expressionProjectComprehension5() {
    assertEquals(Arrays.asList(100, 200, 300, -1, 400),
        TestFilesLoader.run("loop/confidence/lists/projection_expr5.loop"));
  }

  @Test
  public final void expressionProjectComprehension6() {
    assertEquals(Arrays.asList(100, 200, 300, 400, -1),
        TestFilesLoader.run("loop/confidence/lists/projection_expr6.loop"));
  }

  @Test
  public final void functionProjectComprehension() {
    assertEquals(Arrays.asList(20, 40, 60),
        TestFilesLoader.run("loop/confidence/lists/projection_function.loop"));
  }

  @Test
  public final void functionProjectComprehensionAltFilter() {
    assertEquals(Arrays.asList(20, 40, 80),
        TestFilesLoader.run("loop/confidence/lists/projection_function_2.loop"));
  }

//  @Test
  public final void iteratorComprehension() {
    Object iterator = TestFilesLoader.run("loop/confidence/lists/iter_project_1.loop");
    assertTrue(iterator instanceof Iterator);
//    assertEquals(Arrays.asList(20, 40, 80), iterator);
  }
}
