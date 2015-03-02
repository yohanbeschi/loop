package loop;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Stack;

/**
 * @author Dhanji R. Prasanna
 */
public class Tokenizer {
  private final String input;

  public Tokenizer(final String input) {
    try {
      // Clean input of leading whitespace on empty lines.
      final StringBuilder cleaned = new StringBuilder();

      final List<String> lines = Util.toLines(new StringReader(input));
      for (int i = 0, linesSize = lines.size(); i < linesSize; i++) {
        final String line = lines.get(i);
        if (!line.trim().isEmpty()) {
          cleaned.append(line);
        }

        // Append newlines for all but the last line, because we don't want to introduce an
        // unnecessary newline at the eof.
        if (i < linesSize - 1) {
          cleaned.append('\n');
        }
      }

      // Unless it explicitly has one.
      if (input.endsWith("\n") || input.endsWith("\r")) {
        cleaned.append('\n');
      }

      this.input = cleaned.toString();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static final int NON = 0; // MUST be zero
  private static final int SINGLE_TOKEN = 1;
  private static final int SEQUENCE_TOKEN = 2;

  private static final int[] DELIMITERS = new int[256];
  private static final boolean[] STRING_TERMINATORS = new boolean[256];

  static {
    Tokenizer.DELIMITERS['-'] = Tokenizer.SEQUENCE_TOKEN;
    Tokenizer.DELIMITERS['='] = Tokenizer.SEQUENCE_TOKEN;
    Tokenizer.DELIMITERS['+'] = Tokenizer.SEQUENCE_TOKEN;
    Tokenizer.DELIMITERS['/'] = Tokenizer.SEQUENCE_TOKEN;
    Tokenizer.DELIMITERS['*'] = Tokenizer.SEQUENCE_TOKEN;
    Tokenizer.DELIMITERS['>'] = Tokenizer.SEQUENCE_TOKEN;
    Tokenizer.DELIMITERS['<'] = Tokenizer.SEQUENCE_TOKEN;

    // SINGLE token delimiters are one char in length in any context
    Tokenizer.DELIMITERS['\n'] = Tokenizer.SINGLE_TOKEN;
    Tokenizer.DELIMITERS['.'] = Tokenizer.SINGLE_TOKEN;
    Tokenizer.DELIMITERS[','] = Tokenizer.SINGLE_TOKEN;
    Tokenizer.DELIMITERS[':'] = Tokenizer.SINGLE_TOKEN;
    Tokenizer.DELIMITERS['('] = Tokenizer.SINGLE_TOKEN;
    Tokenizer.DELIMITERS[')'] = Tokenizer.SINGLE_TOKEN;
    Tokenizer.DELIMITERS['['] = Tokenizer.SINGLE_TOKEN;
    Tokenizer.DELIMITERS[']'] = Tokenizer.SINGLE_TOKEN;
    Tokenizer.DELIMITERS['{'] = Tokenizer.SINGLE_TOKEN;
    Tokenizer.DELIMITERS['}'] = Tokenizer.SINGLE_TOKEN;

    Tokenizer.STRING_TERMINATORS['"'] = true;
    Tokenizer.STRING_TERMINATORS['\''] = true;
    Tokenizer.STRING_TERMINATORS['`'] = true;
  }

  public List<Token> tokenize() {
    final List<Token> tokens = new ArrayList<Token>();
    final char[] input = this.input.toCharArray();

    int line = 0, column = 0;

    int i = 0, start = 0;
    boolean inWhitespace = false, inDelimiter = false, inComment = false, leading = true;
    char inStringSequence = 0;
    for (; i < input.length; i++) {
      final char c = input[i];
      column++;

      if (c == '\n') {
        line++;
        column = 0;
      }

      // strings and sequences
      if (Tokenizer.STRING_TERMINATORS[c] && !inComment) {

        if (inStringSequence > 0) {

          // end of the current string sequence. bake.
          if (inStringSequence == c) {
            // +1 to include the terminating token.
            Tokenizer.bakeToken(tokens, input, i + 1, start, line, column);
            start = i + 1;

            inStringSequence = 0; // reset to normal language
            leading = false;
            continue;
          }
          // it's a string terminator but it's ok, it's part of the string, ignore...

        } else {
          // Also bake if there is any leading tokenage.
          if (i > start) {
            Tokenizer.bakeToken(tokens, input, i, start, line, column);
            start = i;
          }

          inStringSequence = c; // start string
        }
      }

      // skip everything if we're in a string...
      if (inStringSequence > 0) {
        continue;
      }

      if (c == '\n') {
        leading = true;
      }

      // Comments beginning with #
      if (c == '#') {
        inComment = true;
      }

      // We run the comment until the end of the line
      if (inComment) {
        if (c == '\n') {
          inComment = false;
        }

        start = i;
        continue;
      }

      // whitespace is ignored unless it is leading...
      if (Tokenizer.isWhitespace(c)) {
        inDelimiter = false;

        if (!inWhitespace) {
          // bake token
          Tokenizer.bakeToken(tokens, input, i, start, line, column);
          inWhitespace = true;
        }

        // leading whitespace is a special token...
        if (leading) {
          tokens.add(new Token(" ", Token.Kind.INDENT, line, column));
        }

        // skip whitespace
        start = i + 1;
        continue;
      }

      // any non-whitespace character encountered
      inWhitespace = false;
      if (c != '\n') {
        leading = false;
      }

      // For delimiters that are 1-char long in all contexts,
      // break early.
      if (Tokenizer.isSingleTokenDelimiter(c)) {

        Tokenizer.bakeToken(tokens, input, i, start, line, column);
        start = i;

        // Also add the delimiter.
        Tokenizer.bakeToken(tokens, input, i + 1, start, line, column);
        start = i + 1;
        continue;
      }

      // is delimiter
      if (Tokenizer.isDelimiter(c)) {

        if (!inDelimiter) {
          Tokenizer.bakeToken(tokens, input, i, start, line, column);
          inDelimiter = true;
          start = i;
        }

        continue;
      }

      // if coming out of a delimiter, we still need to bake
      if (inDelimiter) {
        Tokenizer.bakeToken(tokens, input, i, start, line, column);
        start = i;
        inDelimiter = false;
      }
    }

    // collect residual token
    if (i > start && !inComment) {
      // we don't want trailing whitespace
      Tokenizer.bakeToken(tokens, input, i, start, line, column);
    }

    return this.cleanTokens(tokens);
  }

  private List<Token> cleanTokens(final List<Token> tokens) {
    // Analyze token stream and remove line breaks inside groups and such.
    int groups = 0;
    Stack<Token.Kind> groupStack = new Stack<Token.Kind>();

    for (final ListIterator<Token> iterator = tokens.listIterator(); iterator.hasNext();) {
      final Token token = iterator.next();

      if (Token.Kind.LPAREN == token.kind || Token.Kind.LBRACE == token.kind || Token.Kind.LBRACKET == token.kind) {
        groupStack.push(token.kind);
        groups++;
      } else if (Token.Kind.RPAREN == token.kind || Token.Kind.RBRACE == token.kind
          || Token.Kind.RBRACKET == token.kind) {
        if (!groupStack.empty() && groupStack.peek() == token.kind) {
          groupStack.pop();
        }
        groups--;
      }

      // Remove token.
      if (groups > 0 && (token.kind == Token.Kind.EOL || token.kind == Token.Kind.INDENT)) {
        iterator.remove();
      }
    }

    // Iterate again and dress function bodies with { }
    groupStack = new Stack<Token.Kind>();
    for (final ListIterator<Token> iterator = tokens.listIterator(); iterator.hasNext();) {
      final Token token = iterator.next();

      // Insert new function start token if necessary.
      if (Tokenizer.isThinOrFatArrow(token)) {
        // Don't bother doing this if there is already an lbrace next.
        if (iterator.hasNext() && iterator.next().kind != Token.Kind.LBRACE) {
          iterator.previous();
          iterator.add(new Token("{", Token.Kind.LBRACE, token.line, token.column));
          groupStack.push(Token.Kind.LBRACE);
        }
      }

      Token previous = null;
      if (iterator.previousIndex() - 1 >= 0) {
        previous = tokens.get(iterator.previousIndex() - 1);
      }

      if (token.kind == Token.Kind.EOL && previous != null
          && (Tokenizer.isThinOrFatArrow(previous) || previous.kind == Token.Kind.EOL)
          || token.kind == Token.Kind.RPAREN && groups > 0) {

        while (!groupStack.isEmpty() && groupStack.peek() == Token.Kind.LBRACE) {
          // Add before cursor.
          final Token prev = iterator.previous();
          iterator.add(new Token("}", Token.Kind.RBRACE, prev.line, prev.column));
          iterator.next();

          groupStack.pop();
        }
      }

      if (Token.Kind.LPAREN == token.kind) {
        groupStack.push(Token.Kind.LPAREN);
      } else if (Token.Kind.RPAREN == token.kind) {
        while (groupStack.peek() != Token.Kind.LPAREN) {
          // Add before cursor.
          final Token prev = iterator.previous();
          iterator.add(new Token("}", Token.Kind.RBRACE, prev.line, prev.column));
          iterator.next();

          groupStack.pop();
        }

        // Pop the matching lparen.
        groupStack.pop();
      }
    }

    // Close dangling functions
    while (!groupStack.isEmpty()) {
      if (groupStack.pop() == Token.Kind.LBRACE) {
        tokens.add(new Token("}", Token.Kind.RBRACE, 0, 0));
      }
    }

    return tokens;
  }

  private static boolean isThinOrFatArrow(final Token token) {
    return token.kind == Token.Kind.ARROW || token.kind == Token.Kind.HASHROCKET;
  }

  private static boolean isWhitespace(final char c) {
    return '\n' != c && Character.isWhitespace(c);
  }

  static boolean isSingleTokenDelimiter(final char c) {
    return Tokenizer.DELIMITERS[c] == Tokenizer.SINGLE_TOKEN;
  }

  public static String detokenize(final List<Token> tokens) {
    final StringBuilder builder = new StringBuilder();

    for (final Token token : tokens) {
      if (Token.Kind.INDENT == token.kind) {
        builder.append("~");
      } else {
        builder.append(token.value);
      }
      builder.append(' ');
    }

    return builder.toString().trim();
  }

  private static boolean isDelimiter(final char c) {
    return Tokenizer.DELIMITERS[c] != Tokenizer.NON;
  }

  private static void bakeToken(final List<Token> tokens, final char[] input, final int i, final int start,
      final int line, final int column) {
    if (i > start) {
      final String value = new String(input, start, i - start);

      // remove this disgusting hack when you can fix the lexer.
      tokens.add(new Token(value, Token.Kind.determine(value), line, column));
    }
  }
}
