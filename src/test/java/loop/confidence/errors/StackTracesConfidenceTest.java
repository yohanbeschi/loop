package loop.confidence.errors;

import static org.junit.Assert.fail;
import loop.LoopTest;
import loop.TestFilesLoader;

import org.junit.Test;

/**
 * Confidence tests run a bunch of semi-realistic programs and assert that their results are as
 * expected. This is meant to be our functional regression test suite.
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public class StackTracesConfidenceTest extends LoopTest {
  @Test
  public final void stackTracing1() {
    try {
      TestFilesLoader.run("loop/confidence/errors/stack_traces_1.loop");
      fail();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Test
  public final void bestMatches() {
    try {
      TestFilesLoader.run("loop/confidence/errors/missing_method_error.loop");
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
