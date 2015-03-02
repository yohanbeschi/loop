package loop.runtime;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public class Collections {

  public static Object obtain(final Object collection, final Integer from, final Integer to) {
    if (collection instanceof List) {
      final List list = (List) collection;

      return list.subList(from, to + 1);
    } else if (collection instanceof String) {
      final String string = (String) collection;

      return string.substring(from, to + 1);
    } else if (collection instanceof Object[]) {
      final Object[] array = (Object[]) collection;

      return Arrays.copyOfRange(array, from, to + 1);
    }

    throw new RuntimeException("Collection type: " + (collection != null ? collection.getClass() : "null")
        + " not supported");
  }

  public static Object obtain(final Object collection, final Object exactly) {
    if (collection instanceof List) {
      final List list = (List) collection;

      return list.get((Integer) exactly);
    } else if (collection instanceof String) {
      final String string = (String) collection;

      if (exactly instanceof Integer) {
        return Character.toString(string.charAt((Integer) exactly));
      } else if (exactly instanceof String) {
        return string.indexOf(exactly.toString());
      }
    } else if (collection instanceof Map) {
      final Map map = (Map) collection;

      return map.get(exactly);
    } else if (collection instanceof Object[]) {
      final Object[] array = (Object[]) collection;

      return array[(Integer) exactly];
    }

    throw new RuntimeException("Collection type: " + (collection != null ? collection.getClass() : "null")
        + " not supported");
  }

  @SuppressWarnings("unchecked")
  public static Object store(final Object collection, final Object property, final Object value) throws Throwable {
    if (collection instanceof List) {
      final List list = (List) collection;
      list.set((Integer) property, value);

    } else if (collection instanceof Map) {
      final Map map = (Map) collection;
      map.put(property, value);

    } else if (collection instanceof Object[]) {
      final Object[] array = (Object[]) collection;

      // noinspection RedundantCast
      array[(Integer) property] = value;
    } else {
      // Set value.
      final String prop = property.toString();
      Caller.call(collection, "set" + Character.toUpperCase(prop.charAt(0)) + prop.substring(1), value);
    }

    return collection;
  }

  public static Object sliceFrom(final Object collection, final Object fromObj) {
    final int from = (Integer) fromObj;
    if (collection instanceof List) {
      final List list = (List) collection;

      return list.subList(from, list.size());
    } else if (collection instanceof String) {
      final String string = (String) collection;

      return string.substring(from, string.length());
    } else if (collection instanceof Object[]) {
      final Object[] array = (Object[]) collection;

      return Arrays.copyOfRange(array, from, array.length);
    }

    throw new RuntimeException("Collection type: " + (collection != null ? collection.getClass() : "null")
        + " not supported");
  }

  public static Object sliceTo(final Object collection, final Object toObj) {
    final int to = (Integer) toObj;

    if (collection instanceof List) {
      final List list = (List) collection;

      return list.subList(0, to + 1);
    } else if (collection instanceof String) {
      final String string = (String) collection;

      return string.substring(0, to + 1);
    } else if (collection instanceof Object[]) {
      final Object[] array = (Object[]) collection;

      return Arrays.copyOfRange(array, 0, to + 1);
    }

    throw new RuntimeException("Collection type: " + (collection != null ? collection.getClass() : "null")
        + " not supported");
  }
}
