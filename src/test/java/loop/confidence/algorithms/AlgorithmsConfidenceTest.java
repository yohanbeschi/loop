package loop.confidence.algorithms;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;

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
public class AlgorithmsConfidenceTest extends LoopTest {
  @Test
  public final void insertsort() {
    Assert.assertEquals(Arrays.asList(1, 2, 3), TestFilesLoader.run("loop/confidence/algorithms/insertsort.loop"));
  }

  @Test
  public final void quicksort() {
    Assert.assertEquals(Arrays.asList(0, 1, 2, 5, 6, 19, 92, 144),
        TestFilesLoader.run("loop/confidence/algorithms/quicksort.loop"));
  }

  @Test
  public final void mergesort() {
    Assert.assertEquals(Arrays.asList(0, 1, 2, 5, 6, 19, 92, 144),
        TestFilesLoader.run("loop/confidence/algorithms/mergesort.loop"));
  }

  @Test
  public final void shuntingYard() {
    Assert.assertEquals(Arrays.asList(Arrays.asList("1", "2", "3"), Arrays.asList("+", "-")),
        TestFilesLoader.run("loop/confidence/algorithms/shunting_yard.loop"));
  }

  @Test
  public final void mergeLists() {
    Assert.assertEquals(Arrays.asList(0, 1, 2, 5, 6, 19, 92, 144),
        TestFilesLoader.run("loop/confidence/algorithms/merge_lists.loop"));
  }

  @Test
  public final void ransomNote() {
    Assert
    .assertEquals(
        "{ =14, a=1, b=1, c=1, d=2, e=5, f=2, g=1, h=2, i=5, k=1, l=1, m=3, N=1, n=4, .=1, o=9, r=5, s=6, t=5, u=3, w=2, y=1, Y=1}",
        TestFilesLoader.run("loop/confidence/algorithms/ransom_note.loop").toString());
  }

  @Test
  public final void djikstra() {
    Assert.assertEquals(Arrays.asList("root", "a"), TestFilesLoader.run("loop/confidence/algorithms/djikstra.loop"));
  }

  // @Test
  public final void aStar() throws UnsupportedEncodingException {
    Assert.assertEquals(Arrays.asList("root", "n1", "n4"),
        TestFilesLoader.run("loop/confidence/algorithms/a_star.loop"));
  }
}
