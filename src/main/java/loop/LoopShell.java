package loop;

import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jline.console.ConsoleReader;
import jline.console.completer.Completer;
import jline.console.completer.FileNameCompleter;
import loop.ast.Assignment;
import loop.ast.Node;
import loop.ast.Variable;
import loop.ast.script.FunctionDecl;
import loop.ast.script.ModuleDecl;
import loop.ast.script.ModuleLoader;
import loop.ast.script.RequireDecl;
import loop.ast.script.Unit;
import loop.lang.LoopObject;
import loop.runtime.Closure;

/**
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public class LoopShell {
  private static Map<String, Object> shellContext;

  public static Object shellObtain(final String var) {
    return LoopShell.shellContext.get(var);
  }

  public static void shell() throws Exception {
    System.out.println("loOp (http://looplang.org)");
    System.out.println("     by Dhanji R. Prasanna\n");

    final ConsoleReader reader = new ConsoleReader();
    try {
      reader.setExpandEvents(false);
      reader.addCompleter(new MetaCommandCompleter());

      Unit shellScope = new Unit(null, ModuleDecl.SHELL);
      FunctionDecl main = new FunctionDecl("main", null);
      shellScope.declare(main);
      LoopShell.shellContext = new HashMap<String, Object>();

      boolean inFunction = false;

      // Used to build up multiline statement blocks (like functions)
      StringBuilder block = null;
      // noinspection InfiniteLoopStatement
      do {
        final String prompt = block != null ? "|    " : ">> ";
        String rawLine = reader.readLine(prompt);

        if (inFunction) {
          if (rawLine == null || rawLine.trim().isEmpty()) {
            inFunction = false;

            // Eval the function to verify it.
            LoopShell.printResult(Loop.evalClassOrFunction(block.toString(), shellScope));
            block = null;
            continue;
          }

          block.append(rawLine).append('\n');
          continue;
        }

        if (rawLine == null) {
          LoopShell.quit(reader);
        }

        // noinspection ConstantConditions
        final String line = rawLine.trim();
        if (line.isEmpty()) {
          continue;
        }

        // Add a require import.
        if (line.startsWith("require ")) {
          shellScope.declare(new Parser(new Tokenizer(line + '\n').tokenize()).require());
          shellScope.loadDeps("<shell>");
          continue;
        }

        if (line.startsWith(":q") || line.startsWith(":quit")) {
          LoopShell.quit(reader);
        }

        if (line.startsWith(":h") || line.startsWith(":help")) {
          LoopShell.printHelp();
        }

        if (line.startsWith(":run")) {
          final String[] split = line.split("[ ]+", 2);
          if (split.length < 2 || !split[1].endsWith(".loop")) {
            System.out.println("You must specify a .loop file to run.");
          }
          Loop.run(split[1]);
          continue;
        }

        if (line.startsWith(":r") || line.startsWith(":reset")) {
          System.out.println("Context reset.");
          shellScope = new Unit(null, ModuleDecl.SHELL);
          main = new FunctionDecl("main", null);
          shellScope.declare(main);
          LoopShell.shellContext = new HashMap<String, Object>();
          continue;
        }
        if (line.startsWith(":i") || line.startsWith(":imports")) {
          for (final RequireDecl requireDecl : shellScope.imports()) {
            System.out.println(requireDecl.toSymbol());
          }
          System.out.println();
          continue;
        }
        if (line.startsWith(":f") || line.startsWith(":functions")) {
          for (final FunctionDecl functionDecl : shellScope.functions()) {
            final StringBuilder args = new StringBuilder();
            final List<Node> children = functionDecl.arguments().children();
            for (int i = 0, childrenSize = children.size(); i < childrenSize; i++) {
              final Node arg = children.get(i);
              args.append(arg.toSymbol());

              if (i < childrenSize - 1) {
                args.append(", ");
              }
            }

            System.out.println(functionDecl.name() + ": (" + args.toString() + ")"
                + (functionDecl.patternMatching ? " #pattern-matching" : ""));
          }
          System.out.println();
          continue;
        }
        if (line.startsWith(":t") || line.startsWith(":type")) {
          final String[] split = line.split("[ ]+", 2);
          if (split.length <= 1) {
            System.out.println("Give me an expression to determine the type for.\n");
            continue;
          }

          final Object result = LoopShell.evalInFunction(split[1], main, shellScope, false);
          LoopShell.printTypeOf(result);
          continue;
        }

        if (line.startsWith(":javatype")) {
          final String[] split = line.split("[ ]+", 2);
          if (split.length <= 1) {
            System.out.println("Give me an expression to determine the type for.\n");
            continue;
          }

          final Object result = LoopShell.evalInFunction(split[1], main, shellScope, false);
          if (result instanceof LoopError) {
            System.out.println(result.toString());
          } else {
            System.out.println(result == null ? "null" : result.getClass().getName());
          }
          continue;
        }

        // Function definitions can be multiline.
        if (line.endsWith("->") || line.endsWith("=>")) {
          inFunction = true;
          block = new StringBuilder(line).append('\n');
          continue;
        } else if (LoopShell.isDangling(line)) {
          if (block == null) {
            block = new StringBuilder();
          }

          block.append(line).append('\n');
          continue;
        }

        if (block != null) {
          rawLine = block.append(line).toString();
          block = null;
        }

        // First determine what kind of expression this is.
        main.children().clear();

        // OK execute expression.
        try {
          LoopShell.printResult(LoopShell.evalInFunction(rawLine, main, shellScope, true));
        } catch (final ClassCastException e) {
          StackTraceSanitizer.cleanForShell(e);
          System.out.println("#error: " + e.getMessage());
          System.out.println();
        } catch (final RuntimeException e) {
          StackTraceSanitizer.cleanForShell(e);
          e.printStackTrace();
          System.out.println();
        }

      } while (true);
    } catch (final IOException e) {
      System.err.println("Something went wrong =(");
      reader.getTerminal().reset();
      System.exit(1);
    }
  }

  private static void printHelp() {
    System.out.println("loOp Shell v1.0");
    System.out.println("  :run <file.loop>  - executes the specified loop file");
    System.out.println("  :reset            - discards current shell context (variables, funcs, etc.)");
    System.out.println("  :imports          - lists all currently imported loop modules and Java types");
    System.out.println("  :functions        - lists all currently defined functions by signature");
    System.out.println("  :type <expr>      - prints the type of the given expression");
    System.out.println("  :javatype <expr>  - prints the underlying java type (for examining loop internals)");
    System.out.println("  :quit (or Ctrl-D) - exits the loop shell");
    System.out.println("  :help             - prints this help card");
    System.out.println();
    System.out.println("  Hint :h is short for :help, etc.");
  }

  private static void printTypeOf(final Object result) {
    if (result instanceof LoopError) {
      System.out.println(result.toString());
    } else if (result instanceof LoopObject) {
      System.out.println(((LoopObject) result).getType());
    } else if (result instanceof Closure) {
      System.out.println("#function: " + ((Closure) result).name);
    } else {
      System.out.println(result == null ? "Nothing" : "#java: " + result.getClass().getName());
    }
  }

  private static Object evalInFunction(String rawLine, final FunctionDecl func, final Unit shellScope,
      final boolean addToWhereBlock) {
    rawLine = rawLine.trim() + '\n';
    final Executable executable = new Executable(new StringReader(rawLine));
    Node parsedLine;
    try {
      final Parser parser = new Parser(new Tokenizer(rawLine).tokenize(), shellScope);
      parsedLine = parser.line();
      if (parsedLine == null || !parser.getErrors().isEmpty()) {
        executable.printErrors(parser.getErrors());
        return "";
      }

      // If this is an assignment, just check the rhs portion of it.
      // This is a bit hacky but prevents verification from balking about new
      // vars declared in the lhs.
      if (parsedLine instanceof Assignment) {
        new Reducer(parsedLine).reduce();
        final Assignment assignment = (Assignment) parsedLine;

        // Strip the lhs of the assignment if this is a simple variable setter
        // as that will happen after the fact in a where-block.
        // However we still do have Assignment nodes that assign "in-place", i.e.
        // mutate the state of existing variables (example: a.b = c), and these need
        // to continue untouched.
        if (assignment.lhs() instanceof Variable) {
          func.children().add(assignment.rhs());
        } else {
          func.children().add(parsedLine);
        }
      } else {
        func.children().add(parsedLine);
      }

      // Compress nodes and eliminate redundancies.
      new Reducer(func).reduce();

      shellScope.loadDeps("<shell>");
      executable.runMain(true);
      executable.compileExpression(shellScope);

      if (executable.hasErrors()) {
        executable.printStaticErrorsIfNecessary();

        return "";
      }
    } catch (final Exception e) {
      e.printStackTrace();
      return new LoopError("malformed expression " + rawLine);
    }

    try {
      final Object result = Loop.safeEval(executable, null);

      if (addToWhereBlock && parsedLine instanceof Assignment) {
        final Assignment assignment = (Assignment) parsedLine;

        boolean shouldReplace = false, shouldAddToWhere = true;
        if (assignment.lhs() instanceof Variable) {
          final String name = ((Variable) assignment.lhs()).name;
          LoopShell.shellContext.put(name, result);

          // Look up the value of the RHS of the variable from the shell context,
          // if this is the second reference to the same variable.
          assignment.setRhs(new Parser(new Tokenizer("`loop.LoopShell`.shellObtain('" + name + "')").tokenize())
              .parse());
          shouldReplace = true;
        } else {
          shouldAddToWhere = false; // Do not add state-mutating assignments to where block.
        }

        // If this assignment is already present in the current scope, we should replace it.
        if (shouldReplace) {
          for (final Iterator<Node> iterator = func.whereBlock().iterator(); iterator.hasNext();) {
            final Node node = iterator.next();
            if (node instanceof Assignment && ((Assignment) node).lhs() instanceof Variable) {
              iterator.remove();
            }
          }
        }

        if (shouldAddToWhere) {
          func.declareLocally(parsedLine);
        }
      }

      return result;
    } finally {
      ModuleLoader.INSTANCE.reset(); // Cleans up the loaded classes.
    }
  }

  private static void printResult(final Object result) {
    if (result instanceof Closure) {
      final Closure fun = (Closure) result;
      System.out.println("#function: " + fun.name);
    } else if (result instanceof Set) {
      final String r = result.toString();
      System.out.println('{' + r.substring(1, r.length() - 1) + '}');
    } else {
      System.out.println(result == null ? "#nothing" : result);
    }
  }

  private static boolean isLoadCommand(final String line) {
    return line.startsWith(":run");
  }

  private static void quit(final ConsoleReader reader) throws Exception {
    reader.getTerminal().restore();

    System.out.println("Bye.");
    System.exit(0);
  }

  // For tracking multiline expressions.
  private static int braces = 0, brackets = 0, parens = 0;

  private static boolean isDangling(final String line) {
    for (final Token token : new Tokenizer(line).tokenize()) {
      switch (token.kind) {
      case LBRACE:
        LoopShell.braces++;
        break;
      case LBRACKET:
        LoopShell.brackets++;
        break;
      case LPAREN:
        LoopShell.parens++;
        break;
      case RBRACE:
        LoopShell.braces--;
        break;
      case RBRACKET:
        LoopShell.brackets--;
        break;
      case RPAREN:
        LoopShell.parens--;
        break;
      default:
        // Do nothing
      }
    }

    return LoopShell.braces > 0 || LoopShell.brackets > 0 || LoopShell.parens > 0;
  }

  private static class MetaCommandCompleter implements Completer {
    private final List<String> commands = Arrays.asList(":help", ":run", ":quit", ":reset", ":type", ":imports",
        ":javatype", ":functions");

    private final FileNameCompleter fileNameCompleter = new FileNameCompleter();

    @Override
    public int complete(String buffer, final int cursor, final List<CharSequence> candidates) {
      if (buffer == null) {
        buffer = "";
      } else {
        buffer = buffer.trim();
      }

      // See if we should chain to the filename completer first.
      if (LoopShell.isLoadCommand(buffer)) {
        final String[] split = buffer.split("[ ]+");

        // Always complete the first argument.
        if (split.length > 1) {
          return this.fileNameCompleter.complete(split[split.length - 1], cursor, candidates);
        }
      }

      for (final String command : this.commands) {
        if (command.startsWith(buffer)) {
          candidates.add(command.substring(buffer.length()) + ' ');
        }
      }

      return cursor;
    }
  }
}
