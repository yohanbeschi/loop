package loop.confidence.corelib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import loop.LoopTest;
import loop.TestFilesLoader;

import org.junit.Test;

/**
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public class CorelibConfidenceTest extends LoopTest {

  @Test
  public final void simpleFileWrite() throws IOException {
    File expected = new File("target/tmptmp.tmp");
    expected.delete();
    TestFilesLoader.run("loop/confidence/corelib/file_1.loop");

    assertTrue(expected.exists());
    expected.deleteOnExit();
  }

  @Test
  public final void consoleReadLine() throws IOException {
    ByteArrayInputStream bais = new ByteArrayInputStream("hello".getBytes());
    InputStream in = System.in;
    System.setIn(bais);
    try {
      assertEquals("hello", TestFilesLoader.run("loop/confidence/corelib/console_1.loop"));
    } finally {
      System.setIn(in);
    }
  }
}
