package loop;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import loop.Token.Kind;
import loop.ast.Assignment;
import loop.ast.BigDecimalLiteral;
import loop.ast.BigIntegerLiteral;
import loop.ast.BinaryOp;
import loop.ast.BooleanLiteral;
import loop.ast.Call;
import loop.ast.CallArguments;
import loop.ast.CallChain;
import loop.ast.ClassDecl;
import loop.ast.Comprehension;
import loop.ast.Computation;
import loop.ast.ConstructorCall;
import loop.ast.Dereference;
import loop.ast.DestructuringPair;
import loop.ast.DoubleLiteral;
import loop.ast.FloatLiteral;
import loop.ast.Guard;
import loop.ast.IndexIntoList;
import loop.ast.InlineListDef;
import loop.ast.InlineMapDef;
import loop.ast.IntLiteral;
import loop.ast.JavaLiteral;
import loop.ast.ListDestructuringPattern;
import loop.ast.ListRange;
import loop.ast.ListStructurePattern;
import loop.ast.LongLiteral;
import loop.ast.MapPattern;
import loop.ast.MemberAccess;
import loop.ast.Node;
import loop.ast.OtherwiseGuard;
import loop.ast.PatternRule;
import loop.ast.PrivateField;
import loop.ast.RegexLiteral;
import loop.ast.StringLiteral;
import loop.ast.StringPattern;
import loop.ast.TernaryIfExpression;
import loop.ast.TernaryUnlessExpression;
import loop.ast.TypeLiteral;
import loop.ast.Variable;
import loop.ast.WildcardPattern;
import loop.ast.script.ArgDeclList;
import loop.ast.script.FunctionDecl;
import loop.ast.script.ModuleDecl;
import loop.ast.script.RequireDecl;
import loop.ast.script.Unit;

/**
 * Takes the tokenized form of a raw string and converts it to a CoffeeScript parse tree (an optimized form of its AST).
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public class Parser {
  private final List<AnnotatedError> errors = new ArrayList<AnnotatedError>();
  private final List<Token> tokens;

  private Set<String> aliasedModules;
  private Node last = null;
  private int i = 0;
  private Unit scope;

  private static final Set<Token.Kind> RIGHT_ASSOCIATIVE = new HashSet<Token.Kind>();
  private static final Set<Token.Kind> LEFT_ASSOCIATIVE = new HashSet<Token.Kind>();

  static {
    Parser.RIGHT_ASSOCIATIVE.add(Token.Kind.PLUS);
    Parser.RIGHT_ASSOCIATIVE.add(Token.Kind.MINUS);
    Parser.RIGHT_ASSOCIATIVE.add(Token.Kind.DIVIDE);
    Parser.RIGHT_ASSOCIATIVE.add(Token.Kind.STAR);
    Parser.RIGHT_ASSOCIATIVE.add(Token.Kind.MODULUS);

    Parser.RIGHT_ASSOCIATIVE.add(Token.Kind.AND);
    Parser.RIGHT_ASSOCIATIVE.add(Token.Kind.OR);
    Parser.RIGHT_ASSOCIATIVE.add(Token.Kind.NOT);
    Parser.RIGHT_ASSOCIATIVE.add(Token.Kind.EQUALS);
    Parser.RIGHT_ASSOCIATIVE.add(Token.Kind.LEQ);
    Parser.RIGHT_ASSOCIATIVE.add(Token.Kind.GEQ);
    Parser.RIGHT_ASSOCIATIVE.add(Token.Kind.LESSER);
    Parser.RIGHT_ASSOCIATIVE.add(Token.Kind.GREATER);

    Parser.LEFT_ASSOCIATIVE.add(Token.Kind.DOT);
    Parser.LEFT_ASSOCIATIVE.add(Token.Kind.UNARROW);
  }

  public Parser(final List<Token> tokens) {
    this.tokens = tokens;
  }

  public Parser(final List<Token> tokens, final Unit shellScope) {
    this.tokens = tokens;
    this.scope = shellScope;
  }

  public List<AnnotatedError> getErrors() {
    return this.errors;
  }

  public void addError(final String message, final Token token) {
    this.errors.add(new StaticError(message, token));
  }

  public void addError(final String message, final int line, final int column) {
    this.errors.add(new StaticError(message, line, column));
  }

  /**
   * if := IF computation
   * <p/>
   * assign := computation ASSIGN computation
   * <p/>
   * computation := chain (op chain)+ chain := term call*
   * <p/>
   * call := DOT|UNARROW IDENT (LPAREN RPAREN)?
   * <p/>
   * term := (literal | variable) literal := (regex | string | number) variable := IDENT
   * <p/>
   * <p/>
   * Examples --------
   * <p/>
   * (assign)
   * <p/>
   * x = "hi".tos().tos() x = 1
   * <p/>
   * (computation)
   * <p/>
   * 1 + 2 1 + 2 - 3 * 4 1.int + 2.y() - 3.a.b * 4
   * <p/>
   * --------------------
   * <p/>
   * parse := module | require | line
   */
  public Node parse() {
    Node parsed = this.require();
    if (null == parsed) {
      parsed = this.module();
    }
    if (null == parsed) {
      parsed = this.line();
    }

    return this.last = parsed;
  }

  /**
   * The top level parsing rule. Do not use parse() to parse entire programs, it is more for one-line expressions.
   * <p/>
   * script := module? require* (functionDecl | classDecl)* (computation EOL)*
   */
  public Unit script(final String file) {
    this.chewEols();

    ModuleDecl module = this.module();
    if (null == module) {
      module = ModuleDecl.DEFAULT;
    }

    this.chewEols();

    final Unit unit = new Unit(file, module);
    this.scope = unit;
    RequireDecl require;
    do {
      require = this.require();
      this.chewEols();

      if (null != require) {
        if (unit.imports().contains(require) && require.alias == null) {
          this.addError("Duplicate module import: " + require.toSymbol(), require.sourceLine, require.sourceColumn);
          throw new LoopCompileException();
        }

        unit.declare(require);
      }
    } while (require != null);

    FunctionDecl function;
    ClassDecl classDecl = null;
    do {
      function = this.functionDecl();
      if (function == null) {
        classDecl = this.classDecl();
      }

      this.chewEols();

      if (null != function) {
        if (unit.resolveFunction(function.name(), false) != null) {
          this.addError("Duplicate function definition: " + function.name(), function.sourceLine, function.sourceColumn);
          throw new LoopCompileException();
        }

        unit.declare(function);
      } else if (null != classDecl) {
        if (unit.getType(classDecl.name) != null) {
          this.addError("Duplicate type definition: " + classDecl.name, classDecl.sourceLine, classDecl.sourceColumn);
          throw new LoopCompileException();
        }

        unit.declare(classDecl);
      }

    } while (function != null || classDecl != null);

    // Now slurp up any freeform expressions into the module initializer.
    Node expression;
    while ((expression = this.computation()) != null) {
      unit.addToInitializer(expression);
      if (this.match(Kind.EOL) == null) {
        break;
      }
      this.chewEols();
    }

    this.chewEols();
    if (this.i < this.tokens.size() && this.errors.isEmpty()) {
      this.addError("Expected end of script, but additional statements found", this.tokens.get(this.i));
      throw new LoopCompileException();
    }

    this.scope = null;
    return unit;
  }

  private void chewEols() {
    // Chew up end-of-lines.
    // noinspection StatementWithEmptyBody
    while (this.match(Token.Kind.EOL) != null) {
      ;
    }
  }

  /*** Class parsing rules ***/

  /**
   * Type declaration with inline constructors.
   */
  public ClassDecl classDecl() {
    final boolean isImmutable = this.match(Kind.IMMUTABLE) != null;
    if (this.match(Kind.CLASS) == null) {
      return null;
    }

    final List<Token> className = this.match(Kind.TYPE_IDENT);
    if (null == className) {
      this.addError("Expected type identifier (Hint: Types must be upper CamelCase)", this.tokens.get(this.i));
      throw new LoopCompileException();
    }

    if (null == this.match(Kind.ARROW, Kind.LBRACE)) {
      this.addError("Expected '->' after type identifier", this.tokens.get(this.i));
      throw new LoopCompileException();
    }

    final ClassDecl classDecl = new ClassDecl(className.iterator().next().value, isImmutable).sourceLocation(className);

    Node line;
    do {
      this.chewEols();
      this.withIndent();

      line = this.line();
      if (line != null) {
        classDecl.add(line);
      }

      this.chewEols();
    } while (line != null);

    if (!this.endOfInput() && this.match(Token.Kind.RBRACE) == null) {
      this.addError("Expected end of type, additional statements found", this.tokens.get(this.i));
      throw new LoopCompileException();
    }

    return classDecl;
  }

  /**
   * Named function parsing rule.
   */
  public FunctionDecl functionDecl() {
    return this.internalFunctionDecl(false);
  }

  private FunctionDecl anonymousFunctionDecl() {
    return this.internalFunctionDecl(true);
  }

  /**
   * Dual purpose parsing rule. Functions and anonymous functions.
   * <p/>
   * anonymousFunctionDecl := ANONYMOUS_TOKEN argDeclList? 'except' IDENT ARROW EOL (INDENT+ line EOL)
   * <p/>
   * functionDecl := (PRIVATE_FIELD | IDENT) argDeclList? ('in' SYMBOL)? 'except' IDENT ARROW EOL (INDENT+ line EOL)
   * <p/>
   * patternFunctionDecl := (PRIVATE_FIELD | IDENT) argDeclList? ('in' SYMBOL)? 'except' IDENT HASHROCKET EOL (INDENT+
   * line EOL)*
   */
  private FunctionDecl internalFunctionDecl(final boolean anonymous) {
    List<Token> funcName = null;
    List<Token> startTokens = null;
    if (!anonymous) {
      funcName = this.match(Token.Kind.PRIVATE_FIELD);

      if (null == funcName) {
        funcName = this.match(Token.Kind.IDENT);
      }

      // Not a function
      if (null == funcName) {
        return null;
      }
    } else {
      if ((startTokens = this.match(Token.Kind.ANONYMOUS_TOKEN)) == null) {
        return null;
      }
    }

    // Scan ahead to ensure this is a function decl, coz once we start parsing the arg list
    // we can't go back.
    boolean isFunction = false;
    for (int k = this.i; k - this.i < 200 /* panic */&& k < this.tokens.size(); k++) {
      final Token token = this.tokens.get(k);
      if ((token.kind == Kind.ARROW || token.kind == Kind.HASHROCKET) && k < this.tokens.size() + 1
          && this.tokens.get(k + 1).kind == Kind.LBRACE) {
        isFunction = true;
        break;
      }
      if (token.kind == Kind.LBRACE || token.kind == Kind.EOL) {
        break;
      }
    }

    // Refuse to proceed if there does not appear to be a '->' at the end of the current line.
    if (!isFunction) {
      // Reset the parser in case we've already parsed an identifier.
      this.i--;
      return null;
    }

    final ArgDeclList arguments = this.argDeclList();
    final String name = anonymous ? null : funcName.get(0).value;
    startTokens = funcName != null ? funcName : startTokens;
    final FunctionDecl functionDecl = new FunctionDecl(name, arguments).sourceLocation(startTokens);

    // We need to set the module name here because closures are not declared as
    // top level functions in the module.
    if (anonymous) {
      functionDecl.setModule(this.scope.getModuleName());
    }

    // Before we match the start of the function, allow for cell declaration.
    final List<Token> inCellTokens = this.match(Kind.IN, Kind.PRIVATE_FIELD);
    if (inCellTokens != null) {
      functionDecl.cell = inCellTokens.get(1).value;
    }

    // Before we match the arrow and start the function, slurp up any exception handling logic.
    List<Token> exceptHandlerTokens = this.match(Kind.IDENT, Kind.IDENT);
    if (exceptHandlerTokens == null) {
      exceptHandlerTokens = this.match(Kind.IDENT, Kind.PRIVATE_FIELD);
    }

    if (exceptHandlerTokens != null) {
      final Token exceptToken = exceptHandlerTokens.get(0);
      if (!RestrictedKeywords.EXCEPT.equals(exceptToken.value)) {
        this.addError("Expected 'expect' keyword after function signature", exceptToken);
      }
      functionDecl.exceptionHandler = exceptHandlerTokens.get(1).value;
    }

    // If it doesn't have a thin or fat arrow, then it's not a function either.
    if (this.match(Token.Kind.ARROW, Token.Kind.LBRACE) == null) {
      // Fat arrow, pattern matcher.
      if (this.match(Token.Kind.HASHROCKET, Token.Kind.LBRACE) == null) {
        return null;
      } else {
        return this.patternMatchingFunctionDecl(functionDecl);
      }
    }

    // Optionally match eols here.
    this.chewEols();

    Node line;

    // Absorb indentation level.
    this.withIndent();

    boolean hasBody = false;
    while ((line = this.line()) != null) {
      hasBody = true;
      functionDecl.add(line);

      // Multiple lines are allowed if terminated by a comma.
      if (this.match(Kind.EOL) == null) {
        break;
      }

      // EOLs are optional (probably should discourage this though).
      this.withIndent();
    }

    if (hasBody) {
      this.chewEols();

      // Look for a where block attached to this function.
      this.whereBlock(functionDecl);

      // A function body must be terminated by } (this is ensured by the token-stream rewriter)
      if (!this.endOfInput() && this.match(Token.Kind.RBRACE) == null) {
        this.addError("Expected end of function, additional statements found (did you mean '=>')",
            this.tokens.get(this.i));
        throw new LoopCompileException();
      }
    }

    return functionDecl;
  }

  private FunctionDecl patternMatchingFunctionDecl(final FunctionDecl functionDecl) {
    this.chewEols();
    PatternRule rule = new PatternRule().sourceLocation(functionDecl);
    do {
      this.withIndent();

      // Detect pattern first. Maps supercede lists.
      Node pattern = this.emptyMapPattern();
      if (null == pattern) {
        pattern = this.emptyListPattern();
      }

      if (null == pattern) {
        pattern = this.listOrMapPattern();
      }

      if (null == pattern) {
        pattern = this.stringGroupPattern();
      }

      if (null == pattern) {
        pattern = this.regexLiteral();
      }

      if (pattern == null) {
        pattern = this.term();
      }

      // Try "otherwise" default fall thru.
      if (pattern == null) {
        final List<Token> starToken = this.match(Kind.STAR);
        if (starToken != null) {
          pattern = new WildcardPattern().sourceLocation(starToken);
        }
      }

      // Look for a where block at the end of this pattern matching decl.
      final int currentToken = this.i;
      if (pattern == null) {
        if (this.whereBlock(functionDecl)) {
          if (this.endOfInput() || this.match(Token.Kind.RBRACE) != null) {
            break;
          }
        }
      }

      if (pattern == null) {
        this.addError("Pattern syntax error. Expected a pattern rule", this.tokens.get(currentToken));
        return null;
      }

      rule.patterns.add(pattern);
      rule.sourceLocation(pattern);

      boolean guarded = false;
      while (this.match(Token.Kind.PIPE) != null) {
        guarded = true;

        Node guardExpression = this.computation();
        if (guardExpression == null) {
          if (this.match(Token.Kind.ELSE) != null) {
            guardExpression = new OtherwiseGuard();
          }
        }

        if (this.match(Token.Kind.ASSIGN) == null) {
          this.addError("Expected ':' after guard expression", this.tokens.get(this.i - 1));
        }

        final Node line = this.line();
        this.chewEols();
        this.withIndent();

        final Guard guard = new Guard(guardExpression, line);
        rule.add(guard);
      }

      if (!guarded) {
        if (this.match(Kind.COMMA) == null) {
          if (this.match(Token.Kind.ASSIGN) == null) {
            this.addError("Expected ':' after pattern", this.tokens.get(this.i));
          }
        } else {
          continue;
        }

        rule.rhs = this.line();
        this.chewEols();
      }

      functionDecl.add(rule);
      rule = new PatternRule().sourceLocation(functionDecl);

      if (this.endOfInput() || this.match(Token.Kind.RBRACE) != null) {
        break;
      }
    } while (true);

    functionDecl.patternMatching = true;
    return functionDecl;
  }

  private boolean whereBlock(final FunctionDecl functionDecl) {
    this.withIndent();
    boolean hasWhere = false;
    if (this.match(Token.Kind.WHERE) != null) {
      FunctionDecl helperFunction = null;
      Node assignment;
      do {
        this.chewEols();
        this.withIndent();

        assignment = this.variableAssignment();
        if (null == assignment) {
          helperFunction = this.functionDecl();
        }

        this.chewEols();

        if (null != helperFunction) {
          hasWhere = true;
          functionDecl.declareLocally(helperFunction);
        } else if (null != assignment) {
          hasWhere = true;
          functionDecl.declareLocally(assignment);
        }
      } while (helperFunction != null || assignment != null);
    }

    return hasWhere;
  }

  private Node stringGroupPattern() {
    if (this.match(Token.Kind.LPAREN) == null) {
      return null;
    }
    final StringPattern pattern = new StringPattern();

    Node term;
    while ((term = this.term()) != null) {
      pattern.add(term);
      if (this.match(Token.Kind.ASSIGN) == null) {
        break;
      }
    }

    if (this.match(Token.Kind.RPAREN) == null) {
      this.addError("Expected ')' at end of string group pattern rule.", this.tokens.get(this.i - 1));
      throw new LoopCompileException();
    }

    return pattern;
  }

  private Node emptyMapPattern() {
    final List<Token> startTokens = this.match(Kind.LBRACKET, Kind.ASSIGN, Kind.RBRACKET);
    return startTokens != null ? new MapPattern().sourceLocation(startTokens) : null;
  }

  private Node emptyListPattern() {
    final List<Token> startTokens = this.match(Kind.LBRACKET, Kind.RBRACKET);
    return startTokens != null ? new ListDestructuringPattern().sourceLocation(startTokens) : null;
  }

  /**
   * listOrMapPattern := (LBRACKET term ((ASSIGN term)* | UNARROW term (COMMA term UNARROW term)*) RBRACKET)
   */
  private Node listOrMapPattern() {
    Node pattern;

    // We should allow the possibility of matching a type identifier.
    final List<Token> type = this.match(Token.Kind.TYPE_IDENT);
    TypeLiteral typeLiteral = null;
    if (null != type) {
      typeLiteral = new TypeLiteral(type.get(0).value).sourceLocation(type);
    }

    final Token lbracketTokens = this.anyOf(Kind.LBRACKET, Kind.LBRACE);
    if (lbracketTokens == null) {
      return typeLiteral;
    }

    Node term = this.term();
    if (term == null) {
      this.addError("Expected term after '[' in pattern rule", this.tokens.get(this.i));
      throw new LoopCompileException();
    }

    // This is a list denaturing rule.
    if (this.match(Token.Kind.ASSIGN) != null) {
      pattern = new ListDestructuringPattern().sourceLocation(lbracketTokens);
      pattern.add(term);
      term = this.term();
      if (term == null) {
        this.addError("Expected term after ':' in list pattern rule", this.tokens.get(this.i - 1));
        throw new LoopCompileException();
      }
      pattern.add(term);

      while (this.match(Token.Kind.ASSIGN) != null) {
        pattern.add(this.term());
      }

      if (this.match(Token.Kind.RBRACKET) == null) {
        this.addError("Expected ']' at end of list pattern rule", this.tokens.get(this.i - 1));
        return null;
      }

      return pattern;
    }

    // This is a list literal rule.
    final boolean endList = this.match(Token.Kind.RBRACKET) != null;
    if (endList || this.match(Token.Kind.COMMA) != null) {
      pattern = new ListStructurePattern().sourceLocation(lbracketTokens);
      pattern.add(term);
      if (endList) {
        return pattern;
      }

      term = this.term();
      if (null == term) {
        this.addError("Expected term after ',' in list pattern rule", this.tokens.get(this.i - 1));
        throw new LoopCompileException();
      }
      pattern.add(term);

      while (this.match(Token.Kind.COMMA) != null) {
        term = this.term();
        if (null == term) {
          this.addError("Expected term after ',' in list pattern rule", this.tokens.get(this.i - 1));
          throw new LoopCompileException();
        }

        pattern.add(term);
      }

      if (this.match(Token.Kind.RBRACKET) == null) {
        this.addError("Expected ']' at end of list pattern rule", this.tokens.get(this.i - 1));
        throw new LoopCompileException();
      }

      return pattern;
    }

    // This is a map pattern.
    pattern = new MapPattern().sourceLocation(lbracketTokens);
    if (typeLiteral != null) {
      pattern.add(typeLiteral);
    }

    if (this.match(Token.Kind.UNARROW) == null) {
      throw new RuntimeException("Expected '<-' in object pattern rule");
    }

    if (!(term instanceof Variable)) {
      throw new RuntimeException("Must select into a valid variable name in object pattern rule: " + term.toSymbol());
    }

    Node rhs = this.term();
    if (rhs == null) {
      throw new RuntimeException("Expected term after '<-' in object pattern rule");
    }
    if (rhs instanceof Variable) {
      // See if we can keep slurping a dot-chain.
      final CallChain callChain = new CallChain();
      callChain.nullSafe(false);
      callChain.add(rhs);
      while (this.match(Token.Kind.DOT) != null) {
        final Node variable = this.variable();
        if (null == variable) {
          throw new RuntimeException("Expected term after '.' in object pattern rule");
        }
        callChain.add(variable);
      }

      rhs = callChain;
    }

    pattern.add(new DestructuringPair(term, rhs).sourceLocation(term));

    while (this.match(Token.Kind.COMMA) != null) {
      term = this.variable();
      if (null == term) {
        throw new RuntimeException("Expected variable after ',' in object pattern rule");
      }

      if (this.match(Token.Kind.UNARROW) == null) {
        throw new RuntimeException("Expected '<-' in object pattern rule");
      }

      rhs = this.term();
      if (rhs == null) {
        throw new RuntimeException("Expected term after '<-' in object pattern rule");
      }
      if (rhs instanceof Variable) {
        // See if we can keep slurping a dot-chain.
        final CallChain callChain = new CallChain().sourceLocation(rhs);
        callChain.add(rhs);
        while (this.match(Token.Kind.DOT) != null) {
          final Node variable = this.variable();
          if (null == variable) {
            throw new RuntimeException("Expected term after '.' in object pattern rule");
          }
          callChain.add(variable);
        }

        rhs = callChain;
      }

      pattern.add(new DestructuringPair(term, rhs).sourceLocation(term));
    }

    if (this.match(Token.Kind.RBRACKET) == null && this.match(Kind.RBRACE) == null) {
      throw new RuntimeException("Expected '}' at end of object pattern");
    }

    return this.rewriteObjectPattern(pattern);
  }

  private Node rewriteObjectPattern(final Node pattern) {
    for (final Node node : pattern.children()) {
      if (node instanceof DestructuringPair) {
        final DestructuringPair pair = (DestructuringPair) node;

        // We need to rewrite the chain of variables as a chain of property calls.
        if (pair.rhs instanceof CallChain) {
          final CallChain chain = (CallChain) pair.rhs;
          final CallChain rewritten = new CallChain();
          rewritten.add(chain.children().remove(0));

          for (final Node element : chain.children()) {
            final Variable var = (Variable) element;

            rewritten.add(new Dereference(var.name).sourceLocation(var));
          }

          pair.rhs = rewritten;
        }
      }
    }
    return pattern;
  }

  /**
   * argDeclList := LPAREN IDENT (ASSIGN TYPE_IDENT)? (COMMA IDENT (ASSIGN TYPE_IDENT)? )* RPAREN
   */
  private ArgDeclList argDeclList() {
    final List<Token> lparenTokens = this.match(Kind.LPAREN);
    if (lparenTokens == null) {
      return null;
    }

    final List<Token> first = this.match(Token.Kind.IDENT);
    if (null == first) {
      if (null == this.match(Token.Kind.RPAREN)) {
        this.addError("Expected ')' or identifier in function argument list", this.tokens.get(this.i - 1));
        throw new LoopCompileException();
      }
      return new ArgDeclList().sourceLocation(lparenTokens);
    }

    List<Token> optionalType = this.match(Token.Kind.ASSIGN, Token.Kind.TYPE_IDENT);
    final ArgDeclList arguments = new ArgDeclList().sourceLocation(lparenTokens);

    String firstTypeName = optionalType == null ? null : optionalType.get(1).value;
    arguments.add(new ArgDeclList.Argument(first.get(0).value, firstTypeName));

    while (this.match(Token.Kind.COMMA) != null) {
      final List<Token> nextArg = this.match(Token.Kind.IDENT);
      if (null == nextArg) {
        this.addError("Expected identifier after ',' in function arguments", this.tokens.get(this.i - 1));
        throw new LoopCompileException();
      }
      optionalType = this.match(Token.Kind.ASSIGN, Token.Kind.TYPE_IDENT);
      firstTypeName = optionalType == null ? null : optionalType.get(1).value;

      arguments.add(new ArgDeclList.Argument(nextArg.get(0).value, firstTypeName));
    }

    if (this.match(Token.Kind.RPAREN) == null) {
      this.addError("Expected ')' at end of function arguments", this.tokens.get(this.i - 1));
      throw new LoopCompileException();
    }

    return arguments;
  }

  /**
   * require := REQUIRE IDENT (DOT IDENT)* ('as' IDENT)? EOL
   */
  public RequireDecl require() {
    if (this.match(Token.Kind.REQUIRE) == null) {
      return null;
    }

    List<Token> module = this.match(Kind.JAVA_LITERAL);
    if (null == module) {
      module = this.match(Token.Kind.IDENT);
    } else {
      if (this.match(Kind.EOL) == null) {
        this.addError("Expected newline after require (are you trying to alias Java imports?)",
            this.tokens.get(this.i - 1));
        throw new LoopCompileException();
      }

      return new RequireDecl(module.get(0).value).sourceLocation(module);
    }

    if (null == module) {
      this.addError("Expected module identifier", this.tokens.get(this.i - 1));
      throw new LoopCompileException();
    }

    final List<String> requires = new ArrayList<String>();
    requires.add(module.get(0).value);

    boolean aliased, javaImport = false;
    while (this.match(Token.Kind.DOT) != null) {
      module = this.match(Token.Kind.IDENT);
      if (null == module) {
        module = this.match(Kind.TYPE_IDENT);
        javaImport = true;
      }

      if (null == module) {
        this.addError("Expected module identifier part after '.'", this.tokens.get(this.i - 1));
        throw new LoopCompileException();
      }

      requires.add(module.get(0).value);
    }

    final List<Token> asToken = this.match(Kind.IDENT);
    aliased = asToken != null && RestrictedKeywords.AS.equals(asToken.get(0).value);

    final List<Token> aliasTokens = this.match(Kind.IDENT);
    if (aliased) {
      if (this.aliasedModules == null) {
        this.aliasedModules = new HashSet<String>();
      }

      if (aliasTokens == null) {
        this.addError("Expected module alias after '" + RestrictedKeywords.AS + "'", this.tokens.get(this.i - 1));
        throw new LoopCompileException();
      }

      // Cache the aliases for some smart parsing of namespaced function calls.
      this.aliasedModules.add(aliasTokens.get(0).value);
    }

    if (this.match(Token.Kind.EOL) == null) {
      this.addError("Expected newline after require declaration", this.tokens.get(this.i - 1));
      throw new LoopCompileException();
    }

    // We also allow java imports outside using the backticks syntax.
    final String alias = aliased ? aliasTokens.get(0).value : null;
    if (javaImport) {
      return new RequireDecl(requires.toString().replace(", ", "."), alias).sourceLocation(module);
    }

    return new RequireDecl(requires, alias).sourceLocation(module);
  }

  /**
   * module := MODULE IDENT (DOT IDENT)* EOL
   */
  private ModuleDecl module() {
    if (this.match(Token.Kind.MODULE) == null) {
      return null;
    }

    List<Token> module = this.match(Token.Kind.IDENT);
    if (null == module) {
      this.addError("Expected module identifier after 'module' keyword", this.tokens.get(this.i - 1));
      throw new LoopCompileException();
    }

    final List<String> modules = new ArrayList<String>();
    modules.add(module.get(0).value);

    while (this.match(Token.Kind.DOT) != null) {
      module = this.match(Token.Kind.IDENT);
      if (null == module) {
        this.addError("Expected module identifier part after '.'", this.tokens.get(this.i - 1));
        throw new LoopCompileException();
      }

      modules.add(module.get(0).value);
    }

    if (this.match(Token.Kind.EOL) == null) {
      this.addError("Expected newline after module declaration", this.tokens.get(this.i - 1));
      throw new LoopCompileException();
    }

    return new ModuleDecl(modules).sourceLocation(module);
  }

  /*** In-function instruction parsing rules ***/

  /**
   * line := assign
   */
  public Node line() {
    return this.assign();
  }

  /**
   * Assign a variable an expression.
   * <p/>
   * variableAssignment := variable ASSIGN computation
   */
  private Node variableAssignment() {
    final List<Token> startTokens = this.match(Token.Kind.IDENT, Token.Kind.ASSIGN);
    if (null == startTokens) {
      return null;
    }

    final Node left = new Variable(startTokens.get(0).value);
    final Node right = this.computation();
    if (right == null) {
      this.addError("Expected expression after ':' in assignment", this.tokens.get(this.i - 1));
      throw new LoopCompileException();
    }

    final Assignment assignment = new Assignment();
    assignment.add(left);
    assignment.add(right);
    return assignment.sourceLocation(startTokens);
  }

  /**
   * This is really both "free standing expression" and "assignment".
   * <p/>
   * assign := computation (ASSIGN (computation (IF computation | comprehension)?) | (IF computation THEN computation
   * ELSE computation) )?
   */
  private Node assign() {
    final Node left = this.computation();
    if (null == left) {
      return null;
    }

    final List<Token> assignTokens = this.match(Kind.ASSIGN);
    if (assignTokens == null) {
      return left;
    }

    // Ternary operator if-then-else
    final Node ifThenElse = this.ternaryIf();
    if (null != ifThenElse) {
      return new Assignment().add(left).add(ifThenElse);
    }

    // OTHERWISE-- continue processing a normal assignment.
    final Node right = this.computation();
    if (null == right) {
      this.addError("Expected expression after '=' assign operator", assignTokens.get(0));
      throw new LoopCompileException();
    }

    // Is this a conditional assignment?
    Node condition = null;
    if (this.match(Token.Kind.IF) != null) {
      condition = this.computation();
    } else {
      // Is this a list comprehension?
      final Node comprehension = this.comprehension();
      if (null != comprehension) {
        return new Assignment().add(left).add(right);
      }
    }

    return new Assignment(condition).add(left).add(right);
  }

  /**
   * Ternary operator, like Java's ?:
   * <p/>
   * ternaryIf := IF computation then computation else computation
   */
  private Node ternaryIf() {
    List<Token> ifTokens = this.match(Kind.IF);
    if (ifTokens == null) {
      ifTokens = this.match(Kind.UNLESS);
    }

    if (ifTokens != null) {
      final Token operator = ifTokens.get(0);
      final Node ifPart = this.computation();
      if (this.match(Token.Kind.THEN) == null) {
        this.addError(operator.kind + " expression missing THEN clause", this.tokens.get(this.i - 1));
        throw new LoopCompileException();
      }

      final Node thenPart = this.computation();

      // Allow user not to specify else (equivalent of "... else Nothing").
      Node elsePart;
      if (this.match(Token.Kind.ELSE) == null) {
        // addError(operator.kind + " expression missing ELSE clause", tokens.get(i - 1));
        // throw new LoopCompileException();
        elsePart = new TypeLiteral(TypeLiteral.NOTHING);
      } else {
        elsePart = this.computation();
      }

      final Node expr = operator.kind == Kind.IF ? new TernaryIfExpression() : new TernaryUnlessExpression();
      return expr.add(ifPart).add(thenPart).add(elsePart).sourceLocation(ifTokens);
    }

    return null;
  }

  /**
   * comprehension := FOR variable IN computation (AND computation)?
   */
  private Node comprehension() {
    final List<Token> forTokens = this.match(Kind.FOR);
    if (forTokens == null) {
      return null;
    }

    final Node variable = this.variable();
    if (null == variable) {
      this.addError("Expected variable identifier after 'for' in list comprehension", this.tokens.get(this.i - 1));
      throw new LoopCompileException();
    }

    if (this.match(Token.Kind.IN) == null) {
      this.addError("Expected 'in' after identifier in list comprehension", this.tokens.get(this.i - 1));
      throw new LoopCompileException();
    }

    final Node inList = this.computation();
    if (null == inList) {
      this.addError("Expected list clause after 'in' in list comprehension", this.tokens.get(this.i - 1));
      throw new LoopCompileException();
    }

    if (this.match(Token.Kind.IF) == null) {
      return new Comprehension(variable, inList, null).sourceLocation(forTokens);
    }

    final Node filter = this.computation();
    if (filter == null) {
      this.addError("Expected filter expression after 'if' in list comprehension", this.tokens.get(this.i - 1));
      throw new LoopCompileException();
    }

    return new Comprehension(variable, inList, filter).sourceLocation(forTokens);
  }

  /**
   * group := LPAREN computation RPAREN
   */
  private Node group() {
    if (this.match(Token.Kind.LPAREN) == null) {
      return null;
    }

    final Node computation = this.computation();
    if (null == computation) {
      this.addError("Expected expression after '(' in group", this.tokens.get(this.i - 1));
      throw new LoopCompileException();
    }

    if (this.match(Token.Kind.RPAREN) == null) {
      this.addError("Expected ')' to close group expression", this.tokens.get(this.i - 1));
      throw new LoopCompileException();
    }

    return computation;
  }

  /**
   * computation := (group | chain) (comprehension | (rightOp (group | chain)) )*
   */
  public Node computation() {
    Node node = this.group();

    if (node == null) {
      node = this.chain();
    }

    // Production failed.
    if (null == node) {
      return null;
    }

    final Computation computation = new Computation();
    computation.add(node);

    // See if there is a call here.
    final MemberAccess postfixCall = this.call();
    if (postfixCall != null) {
      computation.add(postfixCall.postfix(true));
    }

    Node rightOp;
    Node comprehension = null;
    Node operand;
    while ((rightOp = this.rightOp()) != null || (comprehension = this.comprehension()) != null) {
      if (comprehension != null) {
        computation.add(comprehension);
        continue;
      }

      operand = this.group();
      if (null == operand) {
        operand = this.chain();
      }
      if (null == operand) {
        break;
      }

      rightOp.add(operand);
      computation.add(rightOp);
    }

    return computation;
  }

  /**
   * chain := listOrMapDef | ternaryIf | anonymousFunctionDecl | (term arglist? (call | indexIntoList)*)
   */
  private Node chain() {
    Node node = this.listOrMapDef();

    // If not a list, maybe a ternary if?
    if (null == node) {
      node = this.ternaryIf();
    }

    if (null == node) {
      node = this.anonymousFunctionDecl();
    }

    // If not an ternary IF, maybe a term?
    if (null == node) {
      node = this.term();
    }

    // Production failed.
    if (null == node) {
      return null;
    }

    // If args exist, then we should turn this simple term into a free method call.
    final CallArguments args = this.arglist();
    if (null != args) {
      final String functionName = node instanceof Variable ? ((Variable) node).name : ((PrivateField) node).name();
      node = new Call(functionName, args).sourceLocation(node);
    }

    final CallChain chain = new CallChain();
    chain.add(node);

    // Is this a static method call being set up? I.e. NOT a reference to a constant.
    final boolean isJavaStaticRef = node instanceof JavaLiteral && ((JavaLiteral) node).staticFieldAccess == null;
    Node call, indexIntoList = null;
    boolean isFirst = true;
    while ((call = this.call()) != null || (indexIntoList = this.indexIntoList()) != null) {
      if (call != null) {
        final MemberAccess postfixCall = (MemberAccess) call;
        postfixCall.javaStatic(isFirst && isJavaStaticRef || postfixCall.isJavaStatic());
        postfixCall.postfix(true);

        // Once we have marked a call as java static, the rest of the chain is not. I.e.:
        // `java.lang.Class`.forName('..').newInstance() <-- the last call is non-static.
        isFirst = false;
      }
      chain.add(call != null ? call : indexIntoList);
    }

    // Smart prediction of whether this is a namespaced call or not.
    final List<Node> children = chain.children();
    if (this.aliasedModules != null && !isJavaStaticRef && children.size() == 2 && node instanceof Variable) {
      final Variable namespace = (Variable) node;
      if (this.aliasedModules.contains(namespace.name)) {
        ((Call) children.get(1)).namespace(namespace.name);

        children.remove(0);
      }
    }

    return chain;
  }

  /**
   * arglist := LPAREN (computation (COMMA computation)*)? RPAREN
   */
  private CallArguments arglist() {
    // Test if there is a leading paren.
    final List<Token> parenthetical = this.match(Token.Kind.LPAREN);

    if (null == parenthetical) {
      return null;
    }

    // Slurp arguments while commas exist.
    CallArguments callArguments;
    // See if this may be a named-arg invocation.
    List<Token> named = this.match(Token.Kind.IDENT, Token.Kind.ASSIGN);
    final boolean isPositional = null == named;

    callArguments = new CallArguments(isPositional);
    Node arg = this.computation();
    if (null != arg) {

      // If this is a named arg, wrap it in a name.
      if (isPositional) {
        callArguments.add(arg);
      } else {
        callArguments.add(new CallArguments.NamedArg(named.get(0).value, arg));
      }
    }

    // Rest of argument list, comma separated.
    while (this.match(Token.Kind.COMMA) != null) {
      named = null;
      if (!isPositional) {
        named = this.match(Token.Kind.IDENT, Token.Kind.ASSIGN);
        if (null == named) {
          this.addError("Cannot mix named and positional arguments in a function call", this.tokens.get(this.i - 1));
          throw new LoopCompileException();
        }
      }

      arg = this.computation();
      if (null == arg) {
        this.addError("Expected expression after ',' in function call argument list", this.tokens.get(this.i - 1));
        throw new LoopCompileException();
      }

      if (isPositional) {
        callArguments.add(arg);
      } else {
        callArguments.add(new CallArguments.NamedArg(named.get(0).value, arg));
      }
    }

    // Ensure the method invocation is properly closed.
    if (this.match(Token.Kind.RPAREN) == null) {
      this.addError("Expected ')' at end of function call argument list", this.tokens.get(this.i - 1));
      throw new LoopCompileException();
    }

    return callArguments;
  }

  /**
   * An array deref.
   * <p/>
   * indexIntoList := LBRACKET (computation | computation? DOT DOT computation?)? RBRACKET
   */
  private Node indexIntoList() {
    final List<Token> lbracketTokens = this.match(Kind.LBRACKET);
    if (lbracketTokens == null) {
      return null;
    }

    final Node index = this.computation();

    // This is a list slice with a range specifier.
    Node to = null;
    boolean slice = false;
    if (this.match(Token.Kind.DOT) != null) {
      if (this.match(Token.Kind.DOT) == null) {
        this.addError("Syntax error, range specifier incomplete. Expected '..'", this.tokens.get(this.i - 1));
        throw new LoopCompileException();
      }

      slice = true;
      to = this.computation();
    } else if (index == null) {
      throw new RuntimeException("Expected symbol or '..' list slice operator.");
    }

    if (this.match(Token.Kind.RBRACKET) == null) {
      this.addError("Expected ']' at the end of list index expression", this.tokens.get(this.i - 1));
      throw new LoopCompileException();
    }

    return new IndexIntoList(index, slice, to).sourceLocation(lbracketTokens);
  }

  /**
   * Inline list/map definition.
   * <p/>
   * listOrMapDef := LBRACKET (computation ((COMMA computation)* | computation? DOT DOT computation?)) | (computation
   * COLON computation (COMMA computation COLON computation)*) | COLON RBRACKET
   */
  private Node listOrMapDef() {
    boolean isBraced = false;
    List<Token> lbracketTokens = this.match(Kind.LBRACKET);
    if (lbracketTokens == null) {
      if ((lbracketTokens = this.match(Token.Kind.LBRACE)) == null) {
        return null;
      } else {
        isBraced = true;
      }
    }

    final Node index = this.computation();

    Node list = new InlineListDef(isBraced).sourceLocation(lbracketTokens);
    if (null != index) {
      final boolean isMap = this.match(Token.Kind.ASSIGN) != null;
      if (isMap) {
        list = new InlineMapDef(!isBraced).sourceLocation(lbracketTokens);

        // This map will be stored as a list of alternating keys/values (in pairs).
        list.add(index);
        final Node value = this.computation();
        if (null == value) {
          this.addError("Expected expression after ':' in map definition", this.tokens.get(this.i - 1));
          throw new LoopCompileException();
        }
        list.add(value);
      } else {
        list.add(index);
      }

      // Slurp up all list or map argument values as a comma-separated sequence.
      while (this.match(Token.Kind.COMMA) != null) {
        final Node listElement = this.computation();
        if (null == listElement) {
          this.addError("Expected expression after ',' in " + (isMap ? "map" : "list") + " definition",
              this.tokens.get(this.i - 1));
          throw new LoopCompileException();
        }

        list.add(listElement);

        // If the first index contained a hashrocket, then this is a map.
        if (isMap) {
          if (null == this.match(Token.Kind.ASSIGN)) {
            this.addError("Expected ':' after map key in map definition", this.tokens.get(this.i - 1));
            throw new LoopCompileException();
          }

          final Node value = this.computation();
          if (null == value) {
            this.addError("Expected value expression after ':' in map definition", this.tokens.get(this.i - 1));
            throw new LoopCompileException();
          }
          list.add(value);
        }
      }

      // OTHERWISE---
      // This is a list slice with a range specifier.
      Node to;
      boolean slice;
      if (this.match(Token.Kind.DOT) != null) {
        if (this.match(Token.Kind.DOT) == null) {
          this.addError("Syntax error, range specifier incomplete. Expected '..'", this.tokens.get(this.i - 1));
          throw new LoopCompileException();
        }

        slice = true;
        to = this.computation();
        list = new ListRange(index, slice, to).sourceLocation(lbracketTokens);
      }
    }

    // Is there a hashrocket?
    if (this.match(Token.Kind.ASSIGN) != null) {
      // This is an empty hashmap.
      list = new InlineMapDef(!isBraced);
    }
    if (this.anyOf(Token.Kind.RBRACKET, Token.Kind.RBRACE) == null) {
      this.addError("Expected '" + (isBraced ? "}" : "]'"), this.tokens.get(this.i - 1));
      return null;
    }

    return list;
  }

  /**
   * dereference := '::' (IDENT | TYPE_IDENT) argList?
   */
  private MemberAccess staticCall() {
    final List<Token> staticOperator = this.match(Kind.ASSIGN, Kind.ASSIGN);
    final boolean isStatic = RestrictedKeywords.isStaticOperator(staticOperator);
    if (!isStatic) {
      return null;
    }

    List<Token> ident = this.match(Kind.IDENT);

    boolean constant = false;
    if (ident == null) {
      ident = this.match(Kind.TYPE_IDENT);
      constant = true;
    }

    if (ident == null) {
      this.addError("Expected static method identifier after '::'", staticOperator.get(0));
      throw new RuntimeException("Expected static method identifier after '::'");
    }

    final CallArguments arglist = this.arglist();

    if (arglist == null) {
      return new Dereference(ident.get(0).value).constant(constant).javaStatic(isStatic).sourceLocation(ident);
    }

    // Use the ident as name, and it is a method if there are () at end.
    return new Call(ident.get(0).value, arglist).callJava(true).javaStatic(isStatic).sourceLocation(ident);
  }

  /**
   * A method call production rule.
   * <p/>
   * call := staticCall | (DOT|UNARROW (IDENT | PRIVATE_FIELD) arglist?)
   */
  private MemberAccess call() {
    final MemberAccess dereference = this.staticCall();
    if (dereference != null) {
      return dereference;
    }

    List<Token> call = this.match(Token.Kind.DOT, Token.Kind.IDENT);

    if (null == call) {
      call = this.match(Token.Kind.DOT, Token.Kind.PRIVATE_FIELD);
    }

    boolean forceJava = false;
    final boolean javaStatic = false;
    if (null == call) {
      call = this.match(Token.Kind.UNARROW, Token.Kind.IDENT);
      forceJava = true;
    }

    // Production failed.
    if (null == call) {
      return null;
    }

    final CallArguments callArguments = this.arglist();
    if (callArguments == null) {
      return new Dereference(call.get(1).value).sourceLocation(call);
    }

    // Use the ident as name, and it is a method if there are () at end.
    return new Call(call.get(1).value, callArguments).callJava(forceJava).javaStatic(javaStatic).sourceLocation(call);
  }

  /**
   * constructorCall := NEW TYPE_IDENT arglist
   */
  private Node constructorCall() {
    if (this.match(Kind.NEW) == null) {
      return null;
    }

    String modulePart = null;
    List<Token> module;
    do {
      module = this.match(Kind.IDENT, Kind.DOT);
      if (module != null) {
        if (modulePart == null) {
          modulePart = "";
        }

        modulePart += module.iterator().next().value + ".";
      }
    } while (module != null);

    final List<Token> typeName = this.match(Kind.TYPE_IDENT);
    if (null == typeName) {
      this.addError("Expected type identifer after 'new'", this.tokens.get(this.i - 1));
      throw new LoopCompileException();
    }

    final CallArguments arglist = this.arglist();
    if (null == arglist) {
      this.addError("Expected '(' after constructor call", this.tokens.get(this.i - 1));
      throw new LoopCompileException();
    }

    return new ConstructorCall(modulePart, typeName.iterator().next().value, arglist).sourceLocation(typeName);
  }

  /**
   * term := (literal | variable | field | constructorCall)
   */
  private Node term() {
    Node term = this.literal();

    if (null == term) {
      term = this.variable();
    }

    if (null == term) {
      term = this.field();
    }

    if (null == term) {
      term = this.constructorCall();
    }

    return term;
  }

  /**
   * regexLiteral := DIVIDE ^(DIVIDE|EOL|EOF) DIVIDE
   */
  private Node regexLiteral() {
    int cursor = this.i;

    Token token = this.tokens.get(cursor);
    if (Token.Kind.DIVIDE == token.kind) {

      // Look ahead until we find the ending terminal, EOL or EOF.
      boolean noTerminal = false;
      do {
        cursor++;
        token = this.tokens.get(cursor);
        if (Token.Kind.EOL == token.kind) {
          noTerminal = true;
          break;
        }

      } while (Parser.notEndOfRegex(token) && cursor < this.tokens.size() /* EOF check */);

      // Skip the last divide.
      cursor++;

      if (noTerminal) {
        return null;
      }

    } else {
      return null;
    }

    final int start = this.i;
    this.i = cursor;

    // Compress tokens into regex literal.
    final StringBuilder builder = new StringBuilder();
    for (final Token part : this.tokens.subList(start, this.i)) {
      builder.append(part.value);
    }

    String expression = builder.toString();
    if (expression.startsWith("/") && expression.endsWith("/")) {
      expression = expression.substring(1, expression.length() - 1);
    }
    return new RegexLiteral(expression).sourceLocation(token);
  }

  private static boolean notEndOfRegex(final Token token) {
    return Token.Kind.DIVIDE != token.kind && Token.Kind.REGEX != token.kind;
  }

  /**
   * (lexer super rule) literal := string | MINUS? (integer | long | ( integer DOT integer )) | TYPE_IDENT |
   * JAVA_LITERAL
   */
  private Node literal() {
    final Token token = this.anyOf(Token.Kind.STRING, Token.Kind.INTEGER, Kind.BIG_INTEGER, Kind.LONG,
        Token.Kind.TYPE_IDENT, Token.Kind.JAVA_LITERAL, Token.Kind.TRUE, Token.Kind.FALSE);
    if (null == token) {
      List<Token> match = this.match(Token.Kind.MINUS, Token.Kind.INTEGER);
      if (null != match) {
        List<Token> additional = this.match(Kind.DOT, Kind.INTEGER);
        if (additional != null) {
          return new DoubleLiteral('-' + match.get(1).value + '.' + additional.get(1).value).sourceLocation(additional
              .get(1));
        }

        additional = this.match(Kind.DOT, Kind.FLOAT);
        if (additional != null) {
          return new FloatLiteral('-' + match.get(1).value + '.' + additional.get(1).value + 'F')
              .sourceLocation(additional.get(1));
        }

        return new IntLiteral('-' + match.get(1).value).sourceLocation(match.get(1));
      } else if ((match = this.match(Kind.MINUS, Kind.LONG)) != null) {
        return new LongLiteral('-' + match.get(1).value).sourceLocation(match.get(1));
      } else if ((match = this.match(Kind.MINUS, Kind.BIG_INTEGER)) != null) {
        final List<Token> additional = this.match(Kind.DOT, Kind.INTEGER);
        if (additional != null) {
          return new BigDecimalLiteral('-' + match.get(1).value + '.' + additional.get(1).value)
              .sourceLocation(additional.get(1));
        }

        return new BigIntegerLiteral('-' + match.get(1).value).sourceLocation(match.get(1));
      }

      return null;
    }

    switch (token.kind) {
    case TRUE:
    case FALSE:
      return new BooleanLiteral(token).sourceLocation(token);
    case INTEGER:
      List<Token> additional = this.match(Kind.DOT, Kind.INTEGER);
      if (additional != null) {
        return new DoubleLiteral(token.value + '.' + additional.get(1).value).sourceLocation(additional.get(1));
      }

      additional = this.match(Kind.DOT, Kind.FLOAT);
      if (additional != null) {
        return new FloatLiteral(token.value + '.' + additional.get(1).value + 'F').sourceLocation(additional.get(1));
      }

      return new IntLiteral(token.value).sourceLocation(token);
    case BIG_INTEGER:
      additional = this.match(Kind.DOT, Kind.INTEGER);
      if (additional != null) {
        return new BigDecimalLiteral(token.value + '.' + additional.get(1).value).sourceLocation(additional.get(1));
      }

      return new BigIntegerLiteral(token.value).sourceLocation(token);
    case LONG:
      return new LongLiteral(token.value).sourceLocation(token);
    case STRING:
      return new StringLiteral(token.value).sourceLocation(token);
    case TYPE_IDENT:
      return new TypeLiteral(token.value).sourceLocation(token);
    case JAVA_LITERAL:
      return new JavaLiteral(token.value).sourceLocation(token);
    default:
      return null;
    }
  }

  private Node variable() {
    final List<Token> var = this.match(Token.Kind.IDENT);
    return null != var ? new Variable(var.get(0).value).sourceLocation(var) : null;
  }

  private Node field() {
    final List<Token> var = this.match(Token.Kind.PRIVATE_FIELD);
    return null != var ? new PrivateField(var.get(0).value).sourceLocation(var) : null;
  }

  /**
   * Right associative operator (see Token.Kind).
   */
  private Node rightOp() {
    if (this.i >= this.tokens.size()) {
      return null;
    }
    final Token token = this.tokens.get(this.i);
    if (Parser.isRightAssociative(token)) {
      this.i++;
      return new BinaryOp(token).sourceLocation(token);
    }

    // No right associative op found.
    return null;
  }

  // Production tools.
  private Token anyOf(final Token.Kind... ident) {
    if (this.i >= this.tokens.size()) {
      return null;
    }
    for (final Token.Kind kind : ident) {
      final Token token = this.tokens.get(this.i);
      if (kind == token.kind) {
        this.i++;
        return token;
      }
    }

    // No match =(
    return null;
  }

  private List<Token> match(final Token.Kind... ident) {
    int cursor = this.i;
    for (final Token.Kind kind : ident) {

      // What we want is more than the size of the token stream.
      if (cursor >= this.tokens.size()) {
        return null;
      }

      final Token token = this.tokens.get(cursor);
      if (token.kind != kind) {
        return null;
      }

      cursor++;
    }

    // Forward cursor in token stream to match point.
    final int start = this.i;
    this.i = cursor;
    return this.tokens.subList(start, this.i);
  }

  /**
   * Returns true if this is the end of the text sequence.
   */
  private boolean endOfInput() {
    return this.i == this.tokens.size();
  }

  /**
   * Slurps any leading whitespace and returns the count.
   */
  private int withIndent() {
    int indent = 0;
    while (this.match(Token.Kind.INDENT) != null) {
      indent++;
    }
    return indent;
  }

  public Node ast() {
    return this.last;
  }

  private static boolean isRightAssociative(final Token token) {
    return null != token && Parser.RIGHT_ASSOCIATIVE.contains(token.kind);
  }

  public static String stringify(final List<Node> list) {
    final StringBuilder builder = new StringBuilder();
    for (int i = 0, listSize = list.size(); i < listSize; i++) {
      builder.append(Parser.stringify(list.get(i)));

      if (i < listSize - 1) {
        builder.append(' ');
      }
    }

    return builder.toString();
  }

  /**
   * recursively walks a parse tree and turns it into a symbolic form that is test-readable.
   */
  public static String stringify(final Node tree) {
    final StringBuilder builder = new StringBuilder();

    final boolean shouldWrapInList = Parser.hasChildren(tree);
    if (shouldWrapInList) {
      builder.append('(');
    }
    builder.append(tree.toSymbol());

    if (shouldWrapInList) {
      builder.append(' ');
    }

    for (final Node child : tree.children()) {
      final String s = Parser.stringify(child);
      if (s.length() == 0) {
        continue;
      }

      builder.append(s);
      builder.append(' ');
    }

    // chew last ' '
    if (shouldWrapInList) {
      builder.deleteCharAt(builder.length() - 1);
      builder.append(')');
    }

    return builder.toString();
  }

  private static boolean hasChildren(final Node tree) {
    return null != tree.children() && !tree.children().isEmpty();
  }
}
