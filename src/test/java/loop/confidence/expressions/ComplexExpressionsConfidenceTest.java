package loop.confidence.expressions;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import loop.LoopTest;
import loop.TestFilesLoader;

import org.junit.Test;

/**
 * Regression test for expression edge cases.
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public class ComplexExpressionsConfidenceTest extends LoopTest {
  @Test
  public final void slicingArrays() {
    assertArrayEquals(new Object[]{ "there", "dude", "1" },
        (Object[]) TestFilesLoader.run("loop/confidence/expressions/array_slice_1.loop"));
  }

  @Test
  public final void slicingArraysFrom() {
    assertArrayEquals(new Object[]{ "dude", "1" },
        (Object[]) TestFilesLoader.run("loop/confidence/expressions/array_slice_2.loop"));
  }

  @Test
  public final void slicingArraysTo() {
    assertArrayEquals(new Object[]{ "hi", "there", "dude" },
        (Object[]) TestFilesLoader.run("loop/confidence/expressions/array_slice_3.loop"));
  }

  @Test
  public final void mutatingArraysInPlace() {
    assertArrayEquals(new Object[]{ "hi", "there", "dude", "!!" },
        (Object[]) TestFilesLoader.run("loop/confidence/expressions/array_mutation.loop"));
  }

  @Test
  public final void assignAndReturn() {
    assertEquals(Arrays.asList(3, 2, 1), TestFilesLoader.run("loop/confidence/expressions/assign_ret_1.loop"));
  }

  @Test
  public final void expressionifWithoutElse() {
    assertEquals(null, TestFilesLoader.run("loop/confidence/expressions/if_then_3.loop"));
  }

  @Test
  public final void ifWithoutElse() {
    assertEquals(null, TestFilesLoader.run("loop/confidence/expressions/if_then_1.loop"));
  }

  @Test
  public final void unlessWithoutElse() {
    assertEquals(null, TestFilesLoader.run("loop/confidence/expressions/if_then_2.loop"));
  }
}
