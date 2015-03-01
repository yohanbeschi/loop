package loop;

import java.io.InputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;

import jline.internal.InputStreamReader;

public class TestFilesLoader {
  public static Object run(String file, String[] args) {
    final InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(file);
    try {
      final Reader reader = new InputStreamReader(inputStream, "UTF-8");
      return Loop.run(file, reader, args);
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
      return null;
    }
  }
  
  public static Object run(String file) {
    return TestFilesLoader.run(file, null);
  }
}
