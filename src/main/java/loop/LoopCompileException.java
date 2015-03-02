package loop;

import java.util.List;

/**
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public class LoopCompileException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  private final Executable executable;

  public LoopCompileException(final String message, final Executable executable) {
    super(message);

    this.executable = executable;
  }

  public LoopCompileException() {
    super("Syntax errors exist");
    this.executable = null;
  }

  public List<AnnotatedError> getErrors() {
    return this.executable.getStaticErrors();
  }
}
