package loop.ast;

import java.util.ArrayList;
import java.util.List;

import loop.Token;

/**
 * An abstract node in the parse tree.
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public abstract class Node {
  // the rest of the tree under this node
  protected final List<Node> children = new ArrayList<Node>();

  public int sourceLine, sourceColumn;

  public Node add(final Node child) {
    this.children.add(child);
    return this;
  }

  public <T extends Node> T sourceLocation(final List<Token> tokens) {
    return this.sourceLocation(tokens.iterator().next());
  }

  @SuppressWarnings("unchecked")
  public <T extends Node> T sourceLocation(final Token start) {
    this.sourceLine = start.line;
    this.sourceColumn = start.column;

    return (T) this;
  }

  @SuppressWarnings("unchecked")
  public <T extends Node> T sourceLocation(final Node source) {
    this.sourceLine = source.sourceLine;
    this.sourceColumn = source.sourceColumn;

    return (T) this;
  }

  public List<Node> children() {
    return this.children;
  }

  public Node onlyChild() {
    assert this.children.size() == 1;
    return this.children.get(0);
  }

  public abstract String toSymbol();

  @Override
  public String toString() {
    return this.getClass().getSimpleName() + "{" + this.children + '}';
  }
}
