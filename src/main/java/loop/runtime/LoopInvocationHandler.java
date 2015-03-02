package loop.runtime;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import loop.Executable;
import loop.ast.script.ModuleLoader;

/**
 * InvocationHandler for Java -> Loop communication
 *
 * @author galdolber
 */
public class LoopInvocationHandler implements InvocationHandler {
  public static final Class<?>[] NULLARY = new Class<?>[0];
  private final Class<?> clazz;
  private final String loopFile;

  public LoopInvocationHandler(final Class<?> i, final String file) {
    final String name = file != null ? file : i.getSimpleName();

    final List<Executable> executables = ModuleLoader.INSTANCE.loadAndCompile(Arrays.asList(name));

    if (executables == null || executables.isEmpty()) {
      throw new RuntimeException("Unable to find/compile: " + name + ".loop");
    }

    final Executable executable = executables.get(0);
    this.clazz = executable.getCompiled();
    this.loopFile = executable.file();
  }

  @Override
  public Object invoke(final Object arg0, final Method m, final Object[] args) throws Throwable {
    Class<?>[] argsTypes;
    if (args != null) {
      argsTypes = new Class<?>[args.length];
      Arrays.fill(argsTypes, Object.class);
    } else {
      argsTypes = LoopInvocationHandler.NULLARY;
    }

    try {
      return this.clazz.getMethod(m.getName(), argsTypes).invoke(null, args);
    } catch (final NoSuchMethodException e) {
      throw new RuntimeException(m.toGenericString() + " is not implemented on " + this.loopFile);
    }
  }
}
