package loop;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * IO Utils. Copied most of this code from commons-io.
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public class Util {
  private static final int DEFAULT_BUFFER_SIZE = 4 * 1024;

  public static String toString(final InputStream input) {
    try {
      final StringWriter out = new StringWriter();
      final Reader in = new InputStreamReader(input);
      final char[] buffer = new char[Util.DEFAULT_BUFFER_SIZE];
      int n;
      while (-1 != (n = in.read(buffer))) {
        out.write(buffer, 0, n);
      }
      return out.toString();
    } catch (final Exception e) {
      throw new RuntimeException("Error while converting an InputStream to a String", e);
    }
  }

  public static List<String> toLines(final Reader input) throws IOException {
    final BufferedReader reader = new BufferedReader(input);
    final List<String> list = new ArrayList<String>();
    String line = reader.readLine();
    while (line != null) {
      list.add(line);
      line = reader.readLine();
    }
    return list;
  }

  public static void writeFile(final File file, final String text) throws IOException {
    final FileWriter fileWriter = new FileWriter(file);
    try {
      fileWriter.write(text);
    } finally {
      fileWriter.close();
    }
  }
}
