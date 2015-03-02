package loop.ast;

/**
 * Represents a method call or member dereference.
 */
public class Dereference extends MemberAccess {
  private final String name;

  private boolean javaStatic;
  private boolean constant;
  private String namespace;

  public Dereference(final String name) {
    this.name = name;
  }

  @Override
  public String name() {
    return this.name;
  }

  @Override
  public MemberAccess javaStatic(final boolean isStatic) {
    this.javaStatic = isStatic;

    return this;
  }

  public String namespace() {
    return this.namespace;
  }

  public void namespace(final String namespace) {
    this.namespace = namespace;
  }

  @Override
  public boolean isJavaStatic() {
    return this.javaStatic;
  }

  public boolean constant() {
    return this.constant;
  }

  public Dereference constant(final boolean constant) {
    this.constant = constant;

    return this;
  }

  @Override
  public String toString() {
    return "Dereference{" + this.name + "}";
  }

  @Override
  public String toSymbol() {
    return this.name;
  }

  @Override
  public Node postfix(final boolean postfix) {
    return this;
  }
}
