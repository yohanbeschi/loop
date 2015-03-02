package loop;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public class LoopClassLoader extends ClassLoader {
  final ConcurrentMap<String, byte[]> rawClasses = new ConcurrentHashMap<String, byte[]>();
  final ConcurrentMap<String, Class<?>> loaded = new ConcurrentHashMap<String, Class<?>>();
  public static volatile LoopClassLoader CLASS_LOADER = new LoopClassLoader();

  public void put(final String javaClass, final byte[] bytes) {
    if (null != this.rawClasses.putIfAbsent(javaClass, bytes)) {
      throw new RuntimeException("Illegal attempt to define duplicate class");
    }
  }

  public boolean isLoaded(final String javaClass) {
    return this.loaded.containsKey(javaClass);
  }

  @Override
  protected Class<?> findClass(final String name) throws ClassNotFoundException {
    Class<?> clazz = this.loaded.get(name);
    if (null != clazz) {
      return clazz;
    }

    final byte[] bytes = this.rawClasses.remove(name);
    if (bytes != null) {

      // We don't define loop classes in the parent class loader.
      clazz = this.defineClass(name, bytes);

      if (this.loaded.putIfAbsent(name, clazz) != null) {
        throw new RuntimeException("Attempted duplicate class definition for " + name);
      }
      return clazz;
    }
    return super.findClass(name);
  }

  public Class<?> defineClass(final String name, final byte[] b) {
    return this.defineClass(name, b, 0, b.length);
  }

  public static void reset() {
    LoopClassLoader.CLASS_LOADER = new LoopClassLoader();
    Thread.currentThread().setContextClassLoader(LoopClassLoader.CLASS_LOADER);
  }
}
