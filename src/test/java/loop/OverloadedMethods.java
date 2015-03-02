package loop;

import java.io.InputStream;
import java.util.Map;

public class OverloadedMethods {
  public static Object eval(final InputStream instream, final Object ctx) {
    throw new RuntimeException("Should not be called!");
  }

  @SuppressWarnings("rawtypes")
  public static Object eval(final String template, final Map vars) {
    String str = template;
    for (final Object obj : vars.entrySet()) {
      final Map.Entry entry = (Map.Entry) obj;
      str = str.replace("@{" + entry.getKey().toString() + "}", entry.getValue().toString());
    }
    return str;
  }

  @SuppressWarnings("rawtypes")
  public static Object eval(final String template, final Object ctx, final Map vars) {
    throw new RuntimeException("Should not be called!");
  }

  public static Object eval(final String template, final Object ctx, final Integer vars) {
    throw new RuntimeException("Should not be called!");
  }
}
