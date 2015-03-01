package loop.confidence.algorithms;

import loop.Loop;
import loop.LoopTest;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

/**
 * Confidence tests run a bunch of semi-realistic programs and assert that their results are
 * as expected. This is meant to be our functional regression test suite.
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public class AlgorithmsConfidenceTest extends LoopTest {
  @Test
  public final void insertsort() {
    assertEquals(Arrays.asList(1, 2, 3), Loop.run("test/loop/confidence/algorithms/insertsort.loop"));
  }

  @Test
  public final void quicksort() {
    assertEquals(Arrays.asList(0, 1, 2, 5, 6, 19, 92, 144),
        Loop.run("test/loop/confidence/algorithms/quicksort.loop"));
  }

  @Test
  public final void mergesort() {
    assertEquals(Arrays.asList(0, 1, 2, 5, 6, 19, 92, 144),
        Loop.run("test/loop/confidence/algorithms/mergesort.loop"));
  }

  @Test @SuppressWarnings("unchecked")
  public final void shuntingYard() {
    assertEquals(Arrays.asList(Arrays.asList("1", "2", "3"), Arrays.asList("+", "-")),
        Loop.run("test/loop/confidence/algorithms/shunting_yard.loop"));
  }

  @Test
  public final void mergeLists() {
    assertEquals(Arrays.asList(0, 1, 2, 5, 6, 19, 92, 144),
        Loop.run("test/loop/confidence/algorithms/merge_lists.loop"));
  }

  @Test
  public final void ransomNote() {
    assertEquals("{ =14, a=1, b=1, c=1, d=2, e=5, f=2, g=1, h=2, i=5, k=1, l=1, m=3, N=1, n=4, .=1, o=9, r=5, s=6, t=5, u=3, w=2, y=1, Y=1}",
        Loop.run("test/loop/confidence/algorithms/ransom_note.loop").toString());
  }

  @Test
  public final void djikstra() {
    assertEquals(Arrays.asList("root", "a"),
        Loop.run("test/loop/confidence/algorithms/djikstra.loop"));
  }

//  @Test
  public final void aStar() {
    assertEquals(Arrays.asList("root", "n1", "n4"),
        Loop.run("test/loop/confidence/algorithms/a_star.loop"));
  }
}
