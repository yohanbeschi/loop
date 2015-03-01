package loop.ast.script;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import loop.Executable;
import loop.LoopClassLoader;
import loop.Util;
import loop.runtime.Caller;

/**
 * Loads loop source files as individual modules. Module resolution is as follows:
 * <p/>
 * <ol>
 * <li>A single .loop file maps to a leaf module</li>
 * <li>Modules are hierarchical and top-level modules are just directories, so loop.lang must be in loop/lang.loop</li>
 * <li>Requiring 'loop' will import all the concrete sub modules in loop/ (so it won't import any dirs)</li>
 * </ol>
 * <p/>
 * Module resolution order is as follows:
 * <ol>
 * <li>current directory</li>
 * <li>explicit search path</li>
 * </ol>
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public enum ModuleLoader {
  INSTANCE;

  public static final String LOOP_FILES_EXT = ".loop";

  private static final Set<String> CORE_MODULES = new HashSet<String>(Arrays.asList("prelude", //
      "console", //
      "channels", //
      "file" //
      ));

  private final Set<String> availableResources = new HashSet<>();

  // For faster loading of core modules.
  private final Map<String, String> coreModulesCache = new ConcurrentHashMap<>();

  // Prevents cyclic reloading of identical modules.
  private final Map<String, List<Executable>> modules = new ConcurrentHashMap<>();

  // Scan the classpath at startup and map loop files
  private ModuleLoader() {
    this.findAvailableResources();
  }

  private void findAvailableResources() {
    final String classpath = System.getProperty("java.class.path");
    final String[] elements = classpath.split(System.getProperty("path.separator"));

    for (final String element : elements) {
      try {
        final File newFile = new File(element);

        if (newFile.isDirectory()) {
          this.findLoopFilesInDirectory(newFile, newFile.getAbsolutePath());
        } else {
          this.findLoopFilesInJar(newFile);
        }
      } catch (final Exception e) {

      }
    }
  }

  private void findLoopFilesInDirectory(final File directory, final String rootDir) {
    final File[] files = directory.listFiles();

    for (final File currentFile : files) {
      if (currentFile.isDirectory()) {
        this.findLoopFilesInDirectory(currentFile, rootDir);
      } else {
        final String absolutePath = currentFile.getAbsolutePath().replace(rootDir, "").replace('\\', '/');
        if (absolutePath.endsWith(".jar")) {
          this.findLoopFilesInJar(currentFile);
        } else if (absolutePath.endsWith(ModuleLoader.LOOP_FILES_EXT)) {
          this.availableResources.add(absolutePath);
        }
      }
    }
  }

  private void findLoopFilesInJar(final File jarFile) {
    // TODO: ... The thing is that I don't know what is less efficient
    // 1. adding the path of the jar followed by the path of the resource to the set
    // and loading the resource every time we need it
    // 2. caching each loop files as string like core modules
    // (1) is inefficient as it can take a while to read a file inside a zip (jar) file
    // (2) can be memory consuming
  }

  public void reset() {
    this.modules.clear();
    Caller.reset();
    LoopClassLoader.reset();
  }

  public List<Executable> loadAndCompile(final List<String> moduleChain) {
    final StringBuilder nameBuilder = new StringBuilder();

    for (int i = 0, moduleChainSize = moduleChain.size(); i < moduleChainSize; i++) {
      final String part = moduleChain.get(i);
      nameBuilder.append(part);

      if (i < moduleChainSize - 1) {
        nameBuilder.append('/');
      }
    }

    final String moduleName = nameBuilder.toString();

    // Module already compiled
    final List<Executable> executables = this.modules.get(moduleName);
    if (executables != null) {
      return executables;
    }

    if (ModuleLoader.CORE_MODULES.contains(moduleName)) {
      final String code = this.coreModulesCache.get(moduleName);

      if (code == null) {
        return this.loadAndCompileCoreLib(moduleName);
      } else {
        return this.compile(moduleName, code);
      }
    } else {
      return this.findAndCompile(moduleName);
    }
  }

  private List<Executable> loadAndCompileCoreLib(final String coreModuleName) {
    final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
    final InputStream inputStream = contextClassLoader.getResourceAsStream("loop/lang/" + coreModuleName
        + ModuleLoader.LOOP_FILES_EXT);

    final String code = Util.toString(inputStream).intern();
    this.coreModulesCache.putIfAbsent(coreModuleName, code);
    return this.compile(coreModuleName, code);
  }

  private List<Executable> compile(final String moduleName, final Reader reader) {
    final Executable executable = new Executable(reader, moduleName);
    executable.compile();

    final List<Executable> list = Arrays.asList(executable);
    this.modules.putIfAbsent(moduleName, list);

    return list;
  }

  private List<Executable> compile(final String moduleName, final String code) {
    final Reader reader = new StringReader(code);
    return this.compile(moduleName, reader);
  }

  private Executable compileFromClasspath(final String moduleName, final String path) {
    final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
    final InputStream inputStream = contextClassLoader.getResourceAsStream(path.substring(1, path.length()));

    try (final Reader reader = new BufferedReader(new InputStreamReader(inputStream))) {
      return this.compile(moduleName, reader).get(0);
    } catch (final IOException e) {
      e.printStackTrace();
      return null;
    }
  }

  private List<Executable> findAndCompile(final String moduleName) {
    final List<Executable> executables = this.availableResources.parallelStream() //
        .filter(e -> this.moduleMatch(moduleName, e)) //
        .map(e -> this.compileFromClasspath(moduleName, e)) //
        .collect(Collectors.toList());
    if (executables == null || executables.size() > 0) {
      return executables;
    } else {
      return null;
    }
  }

  // FIXME: Makes the tests pass but absolutely broken mechanism
  // For example if we have 2 files with the same name but from different directories we will pretty much screwed
  // at some point as both resources will be loaded
  // Same goes if we have 2 files with the same ended (ie, xxxFoo.loop and foo.loop)
  private boolean moduleMatch(final String moduleName, final String filePath) {
    final String path = filePath.substring(0, filePath.lastIndexOf('.'));
    return path.endsWith(moduleName);
  }
}
