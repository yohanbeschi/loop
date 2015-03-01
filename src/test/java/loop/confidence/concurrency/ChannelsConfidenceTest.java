package loop.confidence.concurrency;

import loop.LoopTest;
import loop.TestFilesLoader;

import org.junit.After;
import org.junit.Test;

/**
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public class ChannelsConfidenceTest extends LoopTest {
  @After
  public final void post() {

  }

  @Test
  public final void printerBurst() {
    // Counts upto 10 on global worker pool.
    TestFilesLoader.run("loop/confidence/concurrency/channels_printer.loop");
  }

  @Test
  public final void printerSerial() {
    // Counts upto 10 on global worker pool.
    TestFilesLoader.run("loop/confidence/concurrency/channels_printer_2.loop");
  }

  @Test
  public final void counterSerial() {
    TestFilesLoader.run("loop/confidence/concurrency/channels_counter.loop");
  }

  @Test
  public final void pingpongBurst() throws InterruptedException {
    // Counts upto 10 on global worker pool.
    TestFilesLoader.run("loop/confidence/concurrency/channels_pingpong.loop");
    Thread.sleep(15);
  }
}
