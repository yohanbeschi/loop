package loop;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicInteger;

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
import loop.ast.script.Unit;
import loop.runtime.Closure;
import loop.runtime.Scope;
import loop.runtime.regex.NamedPattern;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.util.TraceClassVisitor;

/**
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public class AsmCodeEmitter implements Opcodes {
  private static final boolean printBytecode = System.getProperty("print_bytecode") != null;
  private static final AtomicInteger functionNameSequence = new AtomicInteger();

  private static final String IS_LIST_VAR_PREFIX = "__$isList_";
  private static final String RUNTIME_LIST_SIZE_VAR_PREFIX = "__$runtimeListSize_";
  private static final String RUNTIME_STR_LEN_PREFIX = "__$str_len_";
  private static final String IS_STRING_PREFIX = "__$isStr_";
  private static final String IS_READER_PREFIX = "__$isRdr_";
  private static final String WHERE_SCOPE_FN_PREFIX = "$wh$";

  private final Stack<Context> functionStack = new Stack<Context>();

  private final Scope scope;

  public static class SourceLocation implements Comparable<SourceLocation> {
    public final int line;
    public final int column;

    public SourceLocation(final int line, final int column) {
      this.line = line;
      this.column = column;
    }

    @Override
    public int compareTo(final SourceLocation that) {
      return this.line * 1000000 + this.column - (that.line * 1000000 + that.column);
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || this.getClass() != o.getClass()) {
        return false;
      }

      final SourceLocation that = (SourceLocation) o;

      return this.column == that.column && this.line == that.line;

    }

    @Override
    public int hashCode() {
      return 31 * this.line + this.column;
    }
  }

  private final Map<Class<?>, Emitter> EMITTERS = new HashMap<Class<?>, Emitter>();

  AsmCodeEmitter(final Scope scope) {
    this.scope = scope;

    this.EMITTERS.put(Call.class, this.callEmitter);
    this.EMITTERS.put(Dereference.class, this.dereferenceEmitter);
    this.EMITTERS.put(Computation.class, this.computationEmitter);
    this.EMITTERS.put(IntLiteral.class, this.intEmitter);
    this.EMITTERS.put(FloatLiteral.class, this.floatEmitter);
    this.EMITTERS.put(LongLiteral.class, this.longEmitter);
    this.EMITTERS.put(DoubleLiteral.class, this.doubleEmitter);
    this.EMITTERS.put(BigIntegerLiteral.class, this.bigIntegerEmitter);
    this.EMITTERS.put(BigDecimalLiteral.class, this.bigDecimalEmitter);
    this.EMITTERS.put(BooleanLiteral.class, this.booleanEmitter);
    this.EMITTERS.put(TypeLiteral.class, this.typeLiteralEmitter);
    this.EMITTERS.put(Variable.class, this.variableEmitter);
    this.EMITTERS.put(JavaLiteral.class, this.javaLiteralEmitter);
    this.EMITTERS.put(BinaryOp.class, this.binaryOpEmitter);
    this.EMITTERS.put(StringLiteral.class, this.stringLiteralEmitter);
    this.EMITTERS.put(RegexLiteral.class, this.regexLiteralEmitter);
    this.EMITTERS.put(Assignment.class, this.assignmentEmitter);
    this.EMITTERS.put(InlineMapDef.class, this.inlineMapEmitter);
    this.EMITTERS.put(InlineListDef.class, this.inlineListEmitter);
    this.EMITTERS.put(IndexIntoList.class, this.indexIntoListEmitter);
    this.EMITTERS.put(CallChain.class, this.callChainEmitter);
    this.EMITTERS.put(FunctionDecl.class, this.functionDeclEmitter);
    this.EMITTERS.put(ArgDeclList.class, this.argDeclEmitter);
    this.EMITTERS.put(PrivateField.class, this.privateFieldEmitter);
    this.EMITTERS.put(PatternRule.class, this.patternRuleEmitter);
    this.EMITTERS.put(TernaryIfExpression.class, this.ternaryExpressionEmitter);
    this.EMITTERS.put(TernaryUnlessExpression.class, this.ternaryExpressionEmitter);
    this.EMITTERS.put(Comprehension.class, this.comprehensionEmitter);
    this.EMITTERS.put(ConstructorCall.class, this.constructorCallEmitter);
    this.EMITTERS.put(ListRange.class, this.inlineListRangeEmitter);
  }

  private final ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
  private final Stack<MethodVisitor> methodStack = new Stack<MethodVisitor>();

  public Class<?> write(final Unit unit) {
    Thread.currentThread().setContextClassLoader(LoopClassLoader.CLASS_LOADER);

    // We always emit functions as static into a containing Java class.
    final String javaClass = unit.name();

    String fileName = unit.getFileName();
    if (fileName != null) {
      if (!fileName.endsWith(".loop")) {
        fileName += ".loop";
      }
      this.classWriter.visitSource(fileName, null);
    }
    this.classWriter.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC, javaClass, null, "java/lang/Object", new String[0]);

    for (final FunctionDecl functionDecl : unit.functions()) {
      this.emit(functionDecl);
    }

    // Emit any static initializer here.
    if (unit.initializer() != null) {
      this.emitInitializerBlock(unit.initializer());
    }

    this.classWriter.visitEnd();

    if (AsmCodeEmitter.printBytecode) {
      try {
        new ClassReader(new ByteArrayInputStream(this.classWriter.toByteArray())).accept(new TraceClassVisitor(
            new PrintWriter(System.out)), ClassReader.SKIP_DEBUG);
      } catch (final IOException e) {
        e.printStackTrace();
      }
    }

    LoopClassLoader.CLASS_LOADER.put(javaClass, this.classWriter.toByteArray());
    try {
      return LoopClassLoader.CLASS_LOADER.findClass(javaClass);
    } catch (final ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  private void emitInitializerBlock(final List<Node> exprs) {
    final MethodVisitor initializer = this.classWriter.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);

    this.methodStack.push(initializer);
    final Context context = new Context(FunctionDecl.STATIC_INITIALIZER);
    this.functionStack.push(context);
    this.scope.pushScope(context);
    for (final Node expr : exprs) {
      this.emit(expr);
      initializer.visitInsn(Opcodes.POP);
    }

    initializer.visitInsn(Opcodes.RETURN);
    initializer.visitMaxs(1, 0);
    initializer.visitEnd();
    this.scope.popScope();
    this.functionStack.pop();
    this.methodStack.pop();
  }

  private void trackLineAndColumn(final Node node) {
    final Label line = new Label();
    this.methodStack.peek().visitLabel(line);
    this.methodStack.peek().visitLineNumber(node.sourceLine, line);
  }

  private void emitChildren(final Node node) {
    for (final Node child : node.children()) {
      this.emit(child);
    }
  }

  private void emitOnlyChild(final Node node) {
    this.emit(node.children().get(0));
  }

  public void emit(final Node node) {
    if (!this.EMITTERS.containsKey(node.getClass())) {
      throw new RuntimeException("Missing emitter for " + node.getClass().getSimpleName());
    }
    this.EMITTERS.get(node.getClass()).emitCode(node);
  }

  // -------------------------------------------------------------------
  // EMITTERS ----------------------------------------------------------
  // -------------------------------------------------------------------

  private final Emitter ternaryExpressionEmitter = node -> {
    final boolean unless = node instanceof TernaryUnlessExpression;
    final MethodVisitor methodVisitor = AsmCodeEmitter.this.methodStack.peek();

    final Label elseBranch = new Label();
    final Label end = new Label();

    // If condition
    AsmCodeEmitter.this.emit(node.children().get(0));
    methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Boolean");
    methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z");

    // Flip the clauses in an "unless" expression.
    if (unless) {
      methodVisitor.visitJumpInsn(Opcodes.IFNE, elseBranch);
    } else {
      methodVisitor.visitJumpInsn(Opcodes.IFEQ, elseBranch);
    }

    AsmCodeEmitter.this.emit(node.children().get(1));
    methodVisitor.visitJumpInsn(Opcodes.GOTO, end);

    methodVisitor.visitLabel(elseBranch);
    AsmCodeEmitter.this.emit(node.children().get(2));
    methodVisitor.visitLabel(end);
  };

  private final Emitter computationEmitter = node -> {
    AsmCodeEmitter.this.trackLineAndColumn(node);
    AsmCodeEmitter.this.emitChildren(node);
  };

  private final Emitter dereferenceEmitter = node -> {
    final Dereference dereference = (Dereference) node;
    AsmCodeEmitter.this.trackLineAndColumn(dereference);

    final MethodVisitor methodVisitor = AsmCodeEmitter.this.methodStack.peek();
    methodVisitor.visitLdcInsn(dereference.name());

    // Special form to call on a java type rather than lookup by class name.
    if (dereference.isJavaStatic()) {
      methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "loop/runtime/Caller", "getStatic",
          "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Object;");
    } else {
      // If JDK7, use invokedynamic instead for better performance.
      methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "loop/runtime/Caller", "dereference",
          "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;");
    }
  };

  private final Emitter callEmitter = node -> {
    final Call call = (Call) node;
    AsmCodeEmitter.this.trackLineAndColumn(call);

    final MethodVisitor methodVisitor = AsmCodeEmitter.this.methodStack.peek();
    final Context context = AsmCodeEmitter.this.functionStack.peek();

    FunctionDecl resolvedFunction;
    if (call.namespace() != null) {
      resolvedFunction = AsmCodeEmitter.this.scope.resolveNamespacedFunction(call.name(), call.namespace());
    } else {
      resolvedFunction = call.callJava() ? null : AsmCodeEmitter.this.scope.resolveFunctionOnStack(call.name());
    }

    // All Loop functions are Java static.
    boolean isStatic = resolvedFunction != null, isClosure = false;

    // Is this a tail-recursive function?
    final boolean isTailRecursive = call.isTailCall() && call.namespace() == null && !call.isJavaStatic()
        && context.thisFunction.equals(resolvedFunction);

    // The parse-tree knows if we are calling a java method statically.
    if (!isStatic) {
      isStatic = call.isJavaStatic();
    }

    // This is a special invocation so we emit it without the dot.
    String name;
    if (Closure.CALL_FORM.equals(call.name())) {
      name = "";

      isStatic = true;
      isClosure = true;
    } else if (isStatic && resolvedFunction != null) {
      name = AsmCodeEmitter.normalizeMethodName(resolvedFunction.scopedName());
    } else {
      name = AsmCodeEmitter.normalizeMethodName(call.name());
    }

    // Compute if we should "call as postfix method" (can be overridden with <- operator)
    final List<Node> arguments = call.args().children();
    int callAsPostfixVar = -1;

    boolean callAsPostfix = false;
    int argSize = arguments.size();
    if (resolvedFunction != null && resolvedFunction.arguments() != null) {
      // The actual call is 1 less argument than the function takes, so this is a
      // "call-as-method" syntax.
      if (resolvedFunction.arguments().children().size() - argSize == 1) {
        callAsPostfix = true;
        callAsPostfixVar = context.localVarIndex(context.newLocalVariable());
        argSize++;

        // Save the top of the stack for use as the first argument.
        methodVisitor.visitVarInsn(Opcodes.ASTORE, callAsPostfixVar);
      }
    }

    // TAIL CALL ELIMINATION:
    // Store the call-args into the args of this function and short-circuit the call.
    if (isTailRecursive) {
      final List<Node> children = call.args().children();
      for (int i1 = 0, childrenSize = children.size(); i1 < childrenSize; i1++) {
        final Node arg1 = children.get(i1);
        AsmCodeEmitter.this.emit(arg1); // value

        // Store into the local vars representing the arguments to the recursive function.
        methodVisitor.visitVarInsn(Opcodes.ASTORE, i1);
      }

      // If there's anything left, pop it off. If some expression results in void on stack
      // rather than null or 0, then we might be screwed.
      // Pattern matching functions are well behaved (i.e. not multiline, so leave them alone)
      if (!context.thisFunction.patternMatching && context.thisFunction.children().size() > 1) {
        for (int i2 = 1; i2 < context.thisFunction.children().size(); i2++) {
          methodVisitor.visitInsn(Opcodes.POP);
        }
      }

      // Loop.
      methodVisitor.visitJumpInsn(Opcodes.GOTO, context.startOfFunction);

      return;
    }

    // push name of containing type if this is a static call.
    final boolean isExternalFunction = resolvedFunction != null && resolvedFunction.moduleName != null
        && !AsmCodeEmitter.this.scope.getModuleName().equals(resolvedFunction.moduleName);

    if (isStatic && !call.isJavaStatic()) {
      if (isClosure) {
        methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, "loop/runtime/Closure");
      }

      if (!isExternalFunction) {
        methodVisitor.visitLdcInsn(AsmCodeEmitter.this.scope.getModuleName());
      }
    }

    // push method name onto stack
    if (!isClosure) {
      // Emit the module name of the containing class for the resolved function, BUT only
      // if it is not in the same module as us.
      if (isExternalFunction) {
        methodVisitor.visitLdcInsn(resolvedFunction.moduleName);
      }

      methodVisitor.visitLdcInsn(name);
    }

    if (argSize > 0) {
      final int arrayVar = context.localVarIndex(context.newLocalVariable());

      // push args as array.
      methodVisitor.visitIntInsn(Opcodes.BIPUSH, argSize); // size of array
      methodVisitor.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
      methodVisitor.visitVarInsn(Opcodes.ASTORE, arrayVar);
      int i3 = 0;

      if (callAsPostfix) {
        methodVisitor.visitVarInsn(Opcodes.ALOAD, arrayVar); // array
        methodVisitor.visitIntInsn(Opcodes.BIPUSH, i3); // index
        methodVisitor.visitVarInsn(Opcodes.ALOAD, callAsPostfixVar); // value
        methodVisitor.visitInsn(Opcodes.AASTORE);

        i3++;
      }

      for (final Node arg2 : arguments) {
        methodVisitor.visitVarInsn(Opcodes.ALOAD, arrayVar); // array
        methodVisitor.visitIntInsn(Opcodes.BIPUSH, i3); // index
        AsmCodeEmitter.this.emit(arg2); // value

        methodVisitor.visitInsn(Opcodes.AASTORE);
        i3++;
      }

      // Load the array back in.
      methodVisitor.visitVarInsn(Opcodes.ALOAD, arrayVar);

      if (isStatic) {
        // If JDK7, use invokedynamic instead for better performance.
        if (isClosure) {
          methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "loop/runtime/Caller", "callClosure",
              "(Lloop/runtime/Closure;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;");
        } else {
          // Special form to call on a java type rather than lookup by class name.
          if (call.callJava()) {
            methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "loop/runtime/Caller", "callStatic",
                "(Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;");
          } else {
            methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "loop/runtime/Caller", "callStatic",
                "(Ljava/lang/String;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;");
          }
        }

      } else {
        // If JDK7, use invokedynamic instead for better performance.
        methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "loop/runtime/Caller", "call",
            "(Ljava/lang/Object;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;");
      }
    } else {
      if (isStatic) {
        // If JDK7, use invokedynamic instead for better performance.
        if (isClosure) {
          methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "loop/runtime/Caller", "callClosure",
              "(Lloop/runtime/Closure;Ljava/lang/String;)Ljava/lang/Object;");
        } else {
          // Special form to call on a java type rather than lookup by class name.
          if (call.callJava()) {
            methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "loop/runtime/Caller", "callStatic",
                "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Object;");
          } else {
            methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "loop/runtime/Caller", "callStatic",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Object;");
          }
        }

      } else {
        // If JDK7, use invokedynamic instead for better performance.
        methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "loop/runtime/Caller", "call",
            "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;");
      }
    }
  };

  private final Emitter constructorCallEmitter = node -> {
    final ConstructorCall call = (ConstructorCall) node;
    final MethodVisitor methodVisitor = AsmCodeEmitter.this.methodStack.peek();
    final Context context = AsmCodeEmitter.this.functionStack.peek();

    // Try to resolve as an aliased dep first.
    ClassDecl classDecl = null;
    if (call.modulePart != null && call.modulePart.split("[.]").length == 1) {
      classDecl = AsmCodeEmitter.this.scope.resolveAliasedType(
          call.modulePart.substring(0, call.modulePart.length() - 1), call.name);
    }

    // Resolve a loop type internally. Note that this makes dynamic linking
    // of Loop types impossible, but we CAN link Java binaries dynamically.
    if (classDecl == null) {
      classDecl = AsmCodeEmitter.this.scope.resolve(call.name, true);
    }
    if (classDecl != null) {

      // Instatiate the loop object first. With the correct type
      final int objectVar = context.localVarIndex(context.newLocalVariable());

      methodVisitor.visitTypeInsn(Opcodes.NEW, "loop/lang/LoopObject");
      methodVisitor.visitInsn(Opcodes.DUP);

      methodVisitor.visitTypeInsn(Opcodes.NEW, "loop/lang/LoopClass");
      methodVisitor.visitInsn(Opcodes.DUP);
      methodVisitor.visitLdcInsn(classDecl.name);
      methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, "loop/lang/LoopClass", "<init>", "(Ljava/lang/String;)V");

      methodVisitor
          .visitMethodInsn(Opcodes.INVOKESPECIAL, "loop/lang/LoopObject", "<init>", "(Lloop/lang/LoopClass;)V");
      methodVisitor.visitVarInsn(Opcodes.ASTORE, objectVar);

      final Map<String, Node> fields = new HashMap<String, Node>();
      for (final Node nodeAssign : classDecl.children()) {
        if (nodeAssign instanceof Assignment) {
          final Assignment assignment = (Assignment) nodeAssign;
          fields.put(((Variable) assignment.lhs()).name, assignment.rhs());
        }
      }

      final List<Node> children = call.args().children();
      if (!children.isEmpty() || !fields.isEmpty()) {

        // First emit named-args as overrides of defaults.
        for (final Node child : children) {
          final CallArguments.NamedArg arg1 = (CallArguments.NamedArg) child;

          methodVisitor.visitVarInsn(Opcodes.ALOAD, objectVar);
          methodVisitor.visitLdcInsn(arg1.name);
          AsmCodeEmitter.this.emit(arg1.arg);
          methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Map", "put",
              "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");

          // Puts return crap which we need to discard.
          methodVisitor.visitInsn(Opcodes.POP);
        }

        // Now emit any remaining defaults.
        for (final Map.Entry<String, Node> field : fields.entrySet()) {
          methodVisitor.visitVarInsn(Opcodes.ALOAD, objectVar);
          methodVisitor.visitLdcInsn(field.getKey());
          AsmCodeEmitter.this.emit(field.getValue());
          methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Map", "put",
              "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");

          // Puts return crap which we need to discard.
          methodVisitor.visitInsn(Opcodes.POP);
        }
      }

      // Leave the object on the stack.
      methodVisitor.visitVarInsn(Opcodes.ALOAD, objectVar);

      // Should we freeze this object?
      if (classDecl.immutable) {
        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "loop/lang/LoopObject", "immutize",
            "()Lloop/lang/ImmutableLoopObject;");

        // Overwrite the old var in case there is a reference later on.
        methodVisitor.visitVarInsn(Opcodes.ASTORE, objectVar);
        methodVisitor.visitVarInsn(Opcodes.ALOAD, objectVar);
      }

    } else {
      // Emit Java constructor call.
      String javaType;
      if (call.modulePart != null) {
        javaType = call.modulePart + call.name;
      } else {
        javaType = AsmCodeEmitter.this.scope.resolveJavaType(call.name);
      }

      final boolean isNullary = call.args().children().isEmpty();
      if (!isNullary) {
        final int arrayVar = context.localVarIndex(context.newLocalVariable());
        methodVisitor.visitIntInsn(Opcodes.BIPUSH, call.args().children().size()); // size of array
        methodVisitor.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
        methodVisitor.visitVarInsn(Opcodes.ASTORE, arrayVar);

        int i = 0;
        for (final Node arg2 : call.args().children()) {
          methodVisitor.visitVarInsn(Opcodes.ALOAD, arrayVar); // array
          methodVisitor.visitIntInsn(Opcodes.BIPUSH, i); // index
          AsmCodeEmitter.this.emit(arg2); // value

          methodVisitor.visitInsn(Opcodes.AASTORE);
          i++;
        }

        // push type and constructor arg array.
        methodVisitor.visitLdcInsn(javaType);
        methodVisitor.visitVarInsn(Opcodes.ALOAD, arrayVar);

        methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "loop/runtime/Caller", "instantiate",
            "(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;");
      } else {
        // Otherwise invoke the Java constructor directly. We don't need to resolve it.
        // This is an optimization.
        javaType = javaType.replace('.', '/');
        methodVisitor.visitTypeInsn(Opcodes.NEW, javaType);
        methodVisitor.visitInsn(Opcodes.DUP);
        methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, javaType, "<init>", "()V");
      }
    }
  };

  private final Emitter binaryOpEmitter = node -> {
    final BinaryOp binaryOp = (BinaryOp) node;
    AsmCodeEmitter.this.emitOnlyChild(binaryOp);

    final MethodVisitor methodVisitor = AsmCodeEmitter.this.methodStack.peek();
    switch (binaryOp.operator.kind) {
    case PLUS:
      methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "loop/runtime/Operations", "plus",
          "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
      break;
    case MINUS:
      methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "loop/runtime/Operations", "minus",
          "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
      break;
    case STAR:
      methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "loop/runtime/Operations", "multiply",
          "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
      break;
    case DIVIDE:
      methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "loop/runtime/Operations", "divide",
          "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
      break;
    case MODULUS:
      methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "loop/runtime/Operations", "remainder",
          "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
      break;
    case LESSER:
      methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "loop/runtime/Operations", "lesserThan",
          "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Boolean;");
      break;
    case LEQ:
      methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "loop/runtime/Operations", "lesserThanOrEqual",
          "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Boolean;");
      break;
    case GREATER:
      methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "loop/runtime/Operations", "greaterThan",
          "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Boolean;");
      break;
    case GEQ:
      methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "loop/runtime/Operations", "greaterThanOrEqual",
          "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Boolean;");
      break;
    case EQUALS:
      methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "loop/runtime/Operations", "equal",
          "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Boolean;");
      break;
    case AND:
      methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "loop/runtime/Operations", "and",
          "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Boolean;");
      // methodVisitor.visitInsn(IAND);
      break;
    case OR:
      methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "loop/runtime/Operations", "or",
          "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Boolean;");
      // methodVisitor.visitInsn(IOR);
      break;
    case NOT:
      methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "loop/runtime/Operations", "notEqual",
          "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Boolean;");
      break;
    default:
      throw new UnsupportedOperationException("Unsupported binary operator " + binaryOp.toSymbol());
    }
  };

  private final Emitter assignmentEmitter = node -> {
    final Assignment assignment = (Assignment) node;
    AsmCodeEmitter.this.trackLineAndColumn(assignment);
    final Context context = AsmCodeEmitter.this.functionStack.peek();
    final MethodVisitor methodVisitor = AsmCodeEmitter.this.methodStack.peek();

    if (assignment.lhs() instanceof Variable) {
      final int lhsVar = context.localVarIndex(context.newLocalVariable((Variable) assignment.lhs()));
      AsmCodeEmitter.this.emit(assignment.rhs());

      // Leave the result of the assignment on the stack.
      methodVisitor.visitInsn(Opcodes.DUP);
      methodVisitor.visitVarInsn(Opcodes.ASTORE, lhsVar);
    } else if (assignment.lhs() instanceof CallChain) {
      // this is a setter/put style assignment.
      final CallChain lhs = (CallChain) assignment.lhs();
      final List<Node> children = lhs.children();
      final Node last = children.remove(children.size() - 1);

      if (last instanceof IndexIntoList) {
        // The object to assign into.
        AsmCodeEmitter.this.emit(lhs);

        // The slot where this assignment will go.
        AsmCodeEmitter.this.emit(((IndexIntoList) last).from());

        // The value to assign.
        AsmCodeEmitter.this.emit(assignment.rhs());
        methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "loop/runtime/Collections", "store",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");

      } else if (last instanceof MemberAccess) {
        final MemberAccess call = (MemberAccess) last;
        if (call instanceof Call) {
          throw new RuntimeException("Cannot assign value to a function call");
        }

        // The object to assign into.
        AsmCodeEmitter.this.emit(lhs);

        // The slot where this assignment will go.
        methodVisitor.visitLdcInsn(call.name());

        // The value to assign.
        AsmCodeEmitter.this.emit(assignment.rhs());
        methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "loop/runtime/Collections", "store",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");

      } else {
        throw new RuntimeException("Can only assign a value to variable or object property");
      }
    }
  };

  private final Emitter variableEmitter = node -> {
    final Variable var = (Variable) node;

    final Context context = AsmCodeEmitter.this.functionStack.peek();
    Integer index = context.argumentIndex.get(var.name);
    if (index == null) {
      index = context.localVarIndex(var.name);
    }

    if (index == null) {
      // It could be a function-reference. In which case emit it as a closure.
      final FunctionDecl functionDecl = AsmCodeEmitter.this.scope.resolveFunctionOnStack(var.name);
      if (functionDecl != null) {
        final MethodVisitor methodVisitor = AsmCodeEmitter.this.methodStack.peek();
        methodVisitor.visitTypeInsn(Opcodes.NEW, "loop/runtime/Closure");
        methodVisitor.visitInsn(Opcodes.DUP);
        methodVisitor.visitLdcInsn(functionDecl.moduleName);
        methodVisitor.visitLdcInsn(functionDecl.scopedName());
        methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, "loop/runtime/Closure", "<init>",
            "(Ljava/lang/String;Ljava/lang/String;)V");

      }
    } else {
      AsmCodeEmitter.this.methodStack.peek().visitVarInsn(Opcodes.ALOAD, index);
    }
  };

  private static String normalizeMethodName(final String name) {
    return name.replaceFirst("@", "__");
  }

  private final Emitter intEmitter = node -> {
    final IntLiteral intLiteral = (IntLiteral) node;

    // Emit int wrappers.
    final MethodVisitor methodVisitor = AsmCodeEmitter.this.methodStack.peek();
    methodVisitor.visitLdcInsn(intLiteral.value);
    methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;");

  };

  private final Emitter floatEmitter = node -> {
    final FloatLiteral floatLiteral = (FloatLiteral) node;

    // Emit float wrappers.
    final MethodVisitor methodVisitor = AsmCodeEmitter.this.methodStack.peek();
    methodVisitor.visitLdcInsn(floatLiteral.value);
    methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;");
  };

  private final Emitter longEmitter = node -> {
    final LongLiteral longLiteral = (LongLiteral) node;

    // Emit long wrappers.
    final MethodVisitor methodVisitor = AsmCodeEmitter.this.methodStack.peek();
    methodVisitor.visitLdcInsn(longLiteral.value);
    methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;");
  };

  private final Emitter doubleEmitter = node -> {
    final DoubleLiteral doubleLiteral = (DoubleLiteral) node;

    // Emit double wrappers.
    final MethodVisitor methodVisitor = AsmCodeEmitter.this.methodStack.peek();
    methodVisitor.visitLdcInsn(doubleLiteral.value);
    methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;");
  };

  private final Emitter bigIntegerEmitter = node -> {
    final BigIntegerLiteral bigIntegerLiteral = (BigIntegerLiteral) node;

    // Emit bigint wrappers.
    final MethodVisitor methodVisitor = AsmCodeEmitter.this.methodStack.peek();
    methodVisitor.visitTypeInsn(Opcodes.NEW, "java/math/BigInteger");
    methodVisitor.visitInsn(Opcodes.DUP);
    methodVisitor.visitLdcInsn(bigIntegerLiteral.value);
    methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/math/BigInteger", "<init>", "(Ljava/lang/String;)V");
  };

  private final Emitter bigDecimalEmitter = node -> {
    final BigDecimalLiteral bigDecimalLiteral = (BigDecimalLiteral) node;

    // Emit bigdecimal wrappers.
    final MethodVisitor methodVisitor = AsmCodeEmitter.this.methodStack.peek();
    methodVisitor.visitTypeInsn(Opcodes.NEW, "java/math/BigDecimal");
    methodVisitor.visitInsn(Opcodes.DUP);
    methodVisitor.visitLdcInsn(bigDecimalLiteral.value);
    methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/math/BigDecimal", "<init>", "(Ljava/lang/String;)V");
  };

  private final Emitter booleanEmitter = node -> {
    final BooleanLiteral booleanLiteral = (BooleanLiteral) node;

    // Emit boolean wrappers.
    final MethodVisitor methodVisitor = AsmCodeEmitter.this.methodStack.peek();
    methodVisitor.visitIntInsn(Opcodes.BIPUSH, booleanLiteral.value ? 1 : 0);
    methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;");

  };

  private final Emitter typeLiteralEmitter = node -> {
    final TypeLiteral type = (TypeLiteral) node;
    AsmCodeEmitter.this.trackLineAndColumn(type);

    final MethodVisitor methodVisitor = AsmCodeEmitter.this.methodStack.peek();
    if (TypeLiteral.NOTHING.equals(type.name)) {
      methodVisitor.visitInsn(Opcodes.ACONST_NULL);
    } else {
      final ClassDecl classDecl = AsmCodeEmitter.this.scope.resolve(type.name, true);

      if (classDecl != null) {
        throw new UnsupportedOperationException(); // TODO reflection.
      }

      final String fqn = AsmCodeEmitter.this.scope.resolveJavaType(type.name);
      if (fqn != null) {
        methodVisitor.visitLdcInsn(fqn);
        methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Class", "forName",
            "(Ljava/lang/String;)Ljava/lang/Class;");
      }
    }
  };

  private final Emitter javaLiteralEmitter = node -> {
    final JavaLiteral java = (JavaLiteral) node;
    AsmCodeEmitter.this.trackLineAndColumn(java);

    final MethodVisitor methodVisitor = AsmCodeEmitter.this.methodStack.peek();

    if (java.staticFieldAccess == null) {
      methodVisitor.visitLdcInsn(java.value);
    } else {
      methodVisitor.visitLdcInsn(java.value);
      methodVisitor.visitLdcInsn(java.staticFieldAccess);
      methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "loop/runtime/Caller", "getStatic",
          "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Object;");
    }
  };

  private final Emitter stringLiteralEmitter = node -> {
    final StringLiteral string = (StringLiteral) node;
    AsmCodeEmitter.this.trackLineAndColumn(string);

    if (string.parts != null) {
      final MethodVisitor methodVisitor = AsmCodeEmitter.this.methodStack.peek();

      // Emit stringbuilder for string interpolation.
      methodVisitor.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
      methodVisitor.visitInsn(Opcodes.DUP);
      methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V");

      for (final Node part : string.parts) {
        if (part instanceof StringLiteral) {
          methodVisitor.visitLdcInsn(((StringLiteral) part).value);
        } else {
          AsmCodeEmitter.this.emit(part);
        }

        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
            "(Ljava/lang/Object;)Ljava/lang/StringBuilder;");
      }

      methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString",
          "()Ljava/lang/String;");

    } else {
      AsmCodeEmitter.this.methodStack.peek().visitLdcInsn(string.value.substring(1, string.value.length() - 1));
    }
  };

  private final Emitter regexLiteralEmitter = node -> {
    throw new UnsupportedOperationException();
  };

  private final Emitter functionDeclEmitter = node -> {
    final FunctionDecl functionDecl = (FunctionDecl) node;
    String name = functionDecl.scopedName();
    final boolean isClosure = functionDecl.isAnonymous();
    if (isClosure) {
      // Function is anonymous, generate a globally unique name for it.
      name = "$fn_" + AsmCodeEmitter.functionNameSequence.incrementAndGet();
    }
    final Context innerContext = new Context(functionDecl);

    // Before we emit the body of this method into the class scope, let's
    // see if this is closure, and if it is, emit it as a function reference.
    List<Variable> freeVariables;
    if (isClosure) {
      final MethodVisitor currentVisitor = AsmCodeEmitter.this.methodStack.peek();

      // Discover any free variables and save them to this closure.
      freeVariables = new ArrayList<Variable>();
      AsmCodeEmitter.this.detectFreeVariables(functionDecl, functionDecl.arguments(), freeVariables);
      functionDecl.freeVariables = freeVariables;

      // Add them to the argument list of the function.
      for (final Variable freeVariable1 : freeVariables) {
        functionDecl.arguments().add(new ArgDeclList.Argument(freeVariable1.name, null));
      }

      currentVisitor.visitTypeInsn(Opcodes.NEW, "loop/runtime/Closure");
      currentVisitor.visitInsn(Opcodes.DUP);
      currentVisitor.visitLdcInsn(functionDecl.moduleName);
      currentVisitor.visitLdcInsn(name);

      if (!freeVariables.isEmpty()) {
        final Context outerContext = AsmCodeEmitter.this.functionStack.peek();
        final int arrayIndex = outerContext.localVarIndex(outerContext.newLocalVariable());

        // push free variables as array.
        currentVisitor.visitIntInsn(Opcodes.BIPUSH, freeVariables.size()); // size of array
        currentVisitor.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
        currentVisitor.visitVarInsn(Opcodes.ASTORE, arrayIndex);
        int i2 = 0;
        for (final Variable freeVariable2 : freeVariables) {
          currentVisitor.visitVarInsn(Opcodes.ALOAD, arrayIndex); // array
          currentVisitor.visitIntInsn(Opcodes.BIPUSH, i2); // index
          AsmCodeEmitter.this.emit(freeVariable2); // value

          currentVisitor.visitInsn(Opcodes.AASTORE);
          i2++;
        }

        // Load the array back in.
        currentVisitor.visitVarInsn(Opcodes.ALOAD, arrayIndex);
        currentVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, "loop/runtime/Closure", "<init>",
            "(Ljava/lang/String;Ljava/lang/String;[Ljava/lang/Object;)V");
      } else {
        currentVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, "loop/runtime/Closure", "<init>",
            "(Ljava/lang/String;Ljava/lang/String;)V");
      }
    }

    // ******* BEGIN FUNCTION SIGNATURE ********

    // Start writing this function in its own scope.
    final StringBuilder args = new StringBuilder("(");
    final List<Node> children = functionDecl.arguments().children();
    for (int functionArgIndex = 0, childrenSize1 = children.size(); functionArgIndex < childrenSize1; functionArgIndex++) {
      final Node arg = children.get(functionArgIndex);
      final String argName = ((ArgDeclList.Argument) arg).name();
      innerContext.arguments.add(argName);
      innerContext.argumentIndex.put(argName, functionArgIndex);

      args.append("Ljava/lang/Object;");
    }
    args.append(")");
    AsmCodeEmitter.this.functionStack.push(innerContext);
    AsmCodeEmitter.this.scope.pushScope(innerContext);

    final MethodVisitor methodVisitor = AsmCodeEmitter.this.classWriter.visitMethod((functionDecl.isPrivate ? 0 /* default */
        : Opcodes.ACC_PUBLIC) + Opcodes.ACC_STATIC, AsmCodeEmitter.normalizeMethodName(name),
        args.append("Ljava/lang/Object;").toString(), null, null);
    AsmCodeEmitter.this.methodStack.push(methodVisitor);
    AsmCodeEmitter.this.trackLineAndColumn(functionDecl);

    methodVisitor.visitLabel(innerContext.startOfFunction);

    // ******* BEGIN CELL TRANSACTION ********
    if (functionDecl.cell != null) {
      final int thisIndex = innerContext.newLocalVariable("this");

      // Load the cell in a transactional wrapper into the "this" variable.
      methodVisitor.visitLdcInsn(functionDecl.cell);
      methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "loop/runtime/Cells", "beginTransaction",
          "(Ljava/lang/String;)Ljava/lang/Object;");
      methodVisitor.visitVarInsn(Opcodes.ASTORE, thisIndex);
    }

    // ******* BEGIN WHERE BLOCK LOCALS ********

    // Emit static definitions in all parent where blocks.
    for (int i1 = 0, functionStackSize = AsmCodeEmitter.this.functionStack.size(); i1 < functionStackSize - 1; i1++) {
      final FunctionDecl parent = AsmCodeEmitter.this.functionStack.get(i1).thisFunction;

      for (final Node helper1 : parent.whereBlock()) {
        if (helper1 instanceof Assignment) {
          AsmCodeEmitter.this.emit(helper1);
        }
      }
    }

    // Emit locally-scoped helper functions and variables.
    for (final Node helper2 : functionDecl.whereBlock()) {
      // Rewrite helper functions to be namespaced inside the parent function.
      if (helper2 instanceof FunctionDecl) {
        AsmCodeEmitter.this.scopeNestedFunction(functionDecl, innerContext, (FunctionDecl) helper2);
      }
      AsmCodeEmitter.this.emit(helper2);
    }

    // ******* BEGIN PATTERN HELPER LOCALS ********

    // Set up some helper local vars to make it easier to pattern match certain types (lists).
    if (functionDecl.patternMatching) {
      boolean checkIfLists = false, checkIfString = false;
      for (final Node child : functionDecl.children()) {
        final PatternRule patternRule = (PatternRule) child;

        for (final Node pattern : patternRule.patterns) {
          if (pattern instanceof ListDestructuringPattern || pattern instanceof ListStructurePattern) {
            checkIfLists = true;
          } else if (pattern instanceof StringPattern) {
            checkIfString = true;
          }
        }
      }

      if (checkIfLists) {
        final List<Node> children1 = functionDecl.arguments().children();
        for (int i3 = 0, children1Size = children1.size(); i3 < children1Size; i3++) {
          final int isList = innerContext.newLocalVariable(AsmCodeEmitter.IS_LIST_VAR_PREFIX + i3);
          final int runtimeListSize = innerContext.newLocalVariable(AsmCodeEmitter.RUNTIME_LIST_SIZE_VAR_PREFIX + i3);

          methodVisitor.visitVarInsn(Opcodes.ALOAD, i3);
          methodVisitor.visitTypeInsn(Opcodes.INSTANCEOF, "java/util/List");
          methodVisitor.visitIntInsn(Opcodes.ISTORE, isList);

          // Initialize register for local var.
          methodVisitor.visitIntInsn(Opcodes.BIPUSH, -1);
          methodVisitor.visitIntInsn(Opcodes.ISTORE, runtimeListSize);

          final Label notAList = new Label();
          methodVisitor.visitIntInsn(Opcodes.ILOAD, isList);
          methodVisitor.visitJumpInsn(Opcodes.IFEQ, notAList);

          methodVisitor.visitVarInsn(Opcodes.ALOAD, i3);
          methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, "java/util/List");
          methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List", "size", "()I");
          methodVisitor.visitIntInsn(Opcodes.ISTORE, runtimeListSize);
          methodVisitor.visitLabel(notAList);
        }
      }
      if (checkIfString) {
        for (int i4 = 0, childrenSize2 = children.size(); i4 < childrenSize2; i4++) {
          final int isString = innerContext.newLocalVariable(AsmCodeEmitter.IS_STRING_PREFIX + i4);
          final int isReader = innerContext.newLocalVariable(AsmCodeEmitter.IS_READER_PREFIX + i4);
          final int runtimeStringLen = innerContext.newLocalVariable(AsmCodeEmitter.RUNTIME_STR_LEN_PREFIX + i4);

          // Initialize all local vars we're going to use.
          methodVisitor.visitIntInsn(Opcodes.BIPUSH, 0);
          methodVisitor.visitVarInsn(Opcodes.ISTORE, isString);
          methodVisitor.visitIntInsn(Opcodes.BIPUSH, 0);
          methodVisitor.visitVarInsn(Opcodes.ISTORE, isReader);
          methodVisitor.visitIntInsn(Opcodes.BIPUSH, -1);
          methodVisitor.visitVarInsn(Opcodes.ISTORE, runtimeStringLen);

          methodVisitor.visitVarInsn(Opcodes.ALOAD, i4);
          methodVisitor.visitInsn(Opcodes.DUP);
          methodVisitor.visitTypeInsn(Opcodes.INSTANCEOF, "java/lang/String");
          methodVisitor.visitIntInsn(Opcodes.ISTORE, isString);
          methodVisitor.visitTypeInsn(Opcodes.INSTANCEOF, "java/io/Reader");
          methodVisitor.visitIntInsn(Opcodes.ISTORE, isReader);
          methodVisitor.visitIntInsn(Opcodes.BIPUSH, 1);

          final Label skipStringChecks = new Label();
          methodVisitor.visitJumpInsn(Opcodes.IFNE, skipStringChecks);
          methodVisitor.visitVarInsn(Opcodes.ALOAD, i4);
          methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/String");
          methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "length", "()I");
          methodVisitor.visitIntInsn(Opcodes.ISTORE, runtimeStringLen);

          methodVisitor.visitLabel(skipStringChecks);
        }
      }
    }
    // ******* BEGIN FUNCTION BODY ********

    // ******* BEGIN TRY ********
    final Map<String, Label> catchBlocks = new LinkedHashMap<String, Label>();
    FunctionDecl exceptionHandler = null;
    if (functionDecl.exceptionHandler != null) {
      exceptionHandler = AsmCodeEmitter.this.scope.resolveFunctionOnStack(functionDecl.exceptionHandler);

      final Label tryStart = new Label();

      // Determine exception types.
      Label firstCatchStart = null;
      for (String type : exceptionHandler.handledExceptions()) {
        final Label catchStart = new Label();
        catchBlocks.put(type, catchStart);

        if (null == firstCatchStart) {
          firstCatchStart = catchStart;
        }

        // Resolve java exception type, against imported types.
        final String resolvedType = AsmCodeEmitter.this.scope.resolveJavaType(type);
        if (resolvedType != null) {
          type = resolvedType;
        }

        methodVisitor.visitTryCatchBlock(tryStart, firstCatchStart, catchStart, type.replace('.', '/'));
      }
      methodVisitor.visitLabel(tryStart);
    }

    // ******* BEGIN INSTRUCTIONS ********

    AsmCodeEmitter.this.emitChildren(node);

    if (functionDecl.patternMatching) {
      methodVisitor.visitLdcInsn("Non-exhaustive pattern rules in " + functionDecl.name());
      methodVisitor.visitInsn(Opcodes.DUP); // This is necessary just to maintain stack height consistency. =/
      methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "loop/Loop", "error", "(Ljava/lang/String;)V");
    }

    methodVisitor.visitLabel(innerContext.endOfFunction);
    methodVisitor.visitInsn(Opcodes.ARETURN);

    // ******* END FUNCTION BODY ********

    if (functionDecl.exceptionHandler != null) {
      for (final Map.Entry<String, Label> typeLabel : catchBlocks.entrySet()) {
        methodVisitor.visitLabel(typeLabel.getValue());

        // Emit call to handler.
        // TODO probably need to resolve this to the correct module.
        methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, AsmCodeEmitter.this.scope.getModuleName(),
            exceptionHandler.scopedName(), "(Ljava/lang/Object;)Ljava/lang/Object;");
        methodVisitor.visitInsn(Opcodes.ARETURN);
      }
    }

    methodVisitor.visitMaxs(0, 0);
    methodVisitor.visitEnd();

    AsmCodeEmitter.this.methodStack.pop();
    AsmCodeEmitter.this.functionStack.pop();
    AsmCodeEmitter.this.scope.popScope();
  };

  private void detectFreeVariables(final Node top, final ArgDeclList args, final List<Variable> vars) {
    // Pre-order traversal.
    for (final Node node : top.children()) {
      this.detectFreeVariables(node, args, vars);
    }

    if (top instanceof Variable) {
      final Variable local = (Variable) top;

      boolean free = true;
      if (args != null) {
        for (final Node arg : args.children()) {
          final ArgDeclList.Argument argument = (ArgDeclList.Argument) arg;

          if (argument.name().equals(local.name)) {
            free = false;
          }
        }
      }

      if (free) {
        vars.add(local);
      }

    } else if (top instanceof Call) {
      final Call call = (Call) top;
      this.detectFreeVariables(call.args(), args, vars);
    }

  }

  private void scopeNestedFunction(final FunctionDecl parent, final Context context, final FunctionDecl function) {
    final String unscopedName = function.name();

    String newName;
    if (parent.name().startsWith(AsmCodeEmitter.WHERE_SCOPE_FN_PREFIX)) {
      newName = parent.name() + '$' + unscopedName;
    } else {
      newName = AsmCodeEmitter.WHERE_SCOPE_FN_PREFIX + parent.name() + '$' + unscopedName;
    }

    // Apply the scoped name globally.
    function.scopedName(newName);
    context.newLocalFunction(unscopedName, function);
  }

  private final Emitter privateFieldEmitter = node -> {
    final PrivateField privateField = (PrivateField) node;
    AsmCodeEmitter.this.trackLineAndColumn(privateField);
    final String name = privateField.name().substring(1); // Strip @

    AsmCodeEmitter.this.methodStack.peek().visitLdcInsn(name);
  };

  private final Emitter argDeclEmitter = node -> {
  };

  private final Emitter inlineListEmitter = node -> {
    final InlineListDef inlineListDef = (InlineListDef) node;

    final MethodVisitor methodVisitor = AsmCodeEmitter.this.methodStack.peek();
    final Context context = AsmCodeEmitter.this.functionStack.peek();

    final String listType = inlineListDef.isSet ? "java/util/HashSet" : "java/util/ArrayList";

    final int listVar = context.localVarIndex(context.newLocalVariable());
    methodVisitor.visitTypeInsn(Opcodes.NEW, listType);
    methodVisitor.visitInsn(Opcodes.DUP);
    methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, listType, "<init>", "()V");
    methodVisitor.visitVarInsn(Opcodes.ASTORE, listVar);

    for (final Node child : inlineListDef.children()) {
      methodVisitor.visitVarInsn(Opcodes.ALOAD, listVar);
      AsmCodeEmitter.this.emit(child);

      methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Collection", "add", "(Ljava/lang/Object;)Z");
      methodVisitor.visitInsn(Opcodes.POP); // discard result of add()
    }

    methodVisitor.visitVarInsn(Opcodes.ALOAD, listVar);
  };

  private final Emitter inlineListRangeEmitter = node -> {
    final ListRange range = (ListRange) node;

    final MethodVisitor methodVisitor = AsmCodeEmitter.this.methodStack.peek();

    AsmCodeEmitter.this.emit(range.from);
    AsmCodeEmitter.this.emit(range.to);
    methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "loop/runtime/Caller", "range",
        "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
  };

  private final Emitter inlineMapEmitter = node -> {
    final InlineMapDef inlineMapDef = (InlineMapDef) node;

    final MethodVisitor methodVisitor = AsmCodeEmitter.this.methodStack.peek();
    final Context context = AsmCodeEmitter.this.functionStack.peek();

    final String mapType = inlineMapDef.isTree ? "java/util/TreeMap" : "java/util/HashMap";

    final int mapVar = context.localVarIndex(context.newLocalVariable());
    methodVisitor.visitTypeInsn(Opcodes.NEW, mapType);
    methodVisitor.visitInsn(Opcodes.DUP);
    methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, mapType, "<init>", "()V");
    methodVisitor.visitVarInsn(Opcodes.ASTORE, mapVar);

    for (final Iterator<Node> iterator = inlineMapDef.children().iterator(); iterator.hasNext();) {
      final Node key = iterator.next();
      methodVisitor.visitVarInsn(Opcodes.ALOAD, mapVar);
      AsmCodeEmitter.this.emit(key);
      AsmCodeEmitter.this.emit(iterator.next()); // value.

      methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Map", "put",
          "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
      methodVisitor.visitInsn(Opcodes.POP); // disard put() result
    }

    methodVisitor.visitVarInsn(Opcodes.ALOAD, mapVar);
  };

  private final Emitter callChainEmitter = node -> {
    final CallChain callChain = (CallChain) node;
    final List<Node> children = callChain.children();

    for (int i = 0, childrenSize = children.size(); i < childrenSize; i++) {
      final Node child = children.get(i);

      if (i == 0 && callChain.nullSafe && child instanceof Variable && childrenSize > 1) {
        AsmCodeEmitter.this.trackLineAndColumn(child);
      }
      AsmCodeEmitter.this.emit(child);
    }
  };

  private final Emitter indexIntoListEmitter = node -> {
    final IndexIntoList indexIntoList = (IndexIntoList) node;
    final MethodVisitor methodVisitor = AsmCodeEmitter.this.methodStack.peek();

    final Node from = indexIntoList.from();
    if (from != null) {
      AsmCodeEmitter.this.emit(from);
    }

    final boolean hasTo = indexIntoList.to() != null;
    if (hasTo) {
      AsmCodeEmitter.this.emit(indexIntoList.to());

      if (indexIntoList.isSlice() && from == null) {
        methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "loop/runtime/Collections", "sliceTo",
            "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
      } else {
        methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "loop/runtime/Collections", "obtain",
            "(Ljava/lang/Object;Ljava/lang/Integer;Ljava/lang/Integer;)Ljava/lang/Object;");
      }
    } else {
      if (indexIntoList.isSlice()) {
        methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "loop/runtime/Collections", "sliceFrom",
            "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
      } else {
        methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "loop/runtime/Collections", "obtain",
            "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
      }
    }
  };

  private final Emitter comprehensionEmitter = node -> {
    final Comprehension comprehension = (Comprehension) node;

    final Context context = AsmCodeEmitter.this.functionStack.peek();
    final MethodVisitor methodVisitor = AsmCodeEmitter.this.methodStack.peek();

    // First create our output list.
    final int outVarIndex = context.localVarIndex(context.newLocalVariable());

    methodVisitor.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList");
    methodVisitor.visitInsn(Opcodes.DUP);
    methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V");
    methodVisitor.visitVarInsn(Opcodes.ASTORE, outVarIndex);

    // Now loop through the target variable.
    final int iVarIndex = context.localVarIndex(context.newLocalVariable());

    // iterator = collection.iterator()
    AsmCodeEmitter.this.emit(comprehension.inList());
    methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, "java/util/Collection");
    methodVisitor
        .visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Collection", "iterator", "()Ljava/util/Iterator;");
    methodVisitor.visitVarInsn(Opcodes.ASTORE, iVarIndex);

    final Label start = new Label();
    final Label end = new Label();
    // {
    // if !iterator.hasNext() jump to end
    methodVisitor.visitLabel(start);
    methodVisitor.visitVarInsn(Opcodes.ALOAD, iVarIndex);
    methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z");
    methodVisitor.visitJumpInsn(Opcodes.IFEQ, end);

    // var = iterator.next()
    methodVisitor.visitVarInsn(Opcodes.ALOAD, iVarIndex);
    methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;");
    final int nextIndex = context.localVarIndex(context.newLocalVariable(comprehension.var()));
    methodVisitor.visitVarInsn(Opcodes.ASTORE, nextIndex);

    // if (filter_expression)
    if (comprehension.filter() != null) {
      AsmCodeEmitter.this.emit(comprehension.filter());
      // Convert to primitive type.
      methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z");
      methodVisitor.visitJumpInsn(Opcodes.IFEQ, start);
    }

    // Dump this value into the out list.
    methodVisitor.visitVarInsn(Opcodes.ALOAD, outVarIndex);
    methodVisitor.visitVarInsn(Opcodes.ALOAD, nextIndex);

    // Transform the value first using the projection expression.
    if (comprehension.projection() != null) {
      methodVisitor.visitInsn(Opcodes.POP);
      for (final Node projection : comprehension.projection()) {
        AsmCodeEmitter.this.emit(projection);
      }
    }

    methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List", "add", "(Ljava/lang/Object;)Z");

    methodVisitor.visitInsn(Opcodes.POP); // Discard result of add()
    methodVisitor.visitJumpInsn(Opcodes.GOTO, start);
    // }
    methodVisitor.visitLabel(end);

    // Finally.
    methodVisitor.visitVarInsn(Opcodes.ALOAD, outVarIndex);
  };

  private final Emitter patternRuleEmitter = node -> {
    final PatternRule rule = (PatternRule) node;
    final Context context = AsmCodeEmitter.this.functionStack.peek();
    final MethodVisitor methodVisitor = AsmCodeEmitter.this.methodStack.peek();

    if (context.arguments.isEmpty()) {
      throw new RuntimeException("Incorrect number of arguments for pattern matching");
    }

    if (context.arguments.size() != rule.patterns.size()) {
      throw new RuntimeException("Incorrect number of pattern rules. Expected pattern rules for " + context.arguments
          + " but found " + rule.patterns.size() + " rule(s): " + Parser.stringify(rule.patterns));
    }

    final Label matchedClause = new Label();
    final Label endOfClause = new Label();

    for (int i = 0, argumentsSize = context.arguments.size(); i < argumentsSize; i++) {

      final Node pattern = rule.patterns.get(i);
      if (pattern instanceof ListDestructuringPattern) {
        AsmCodeEmitter.this.emitListDestructuringPatternRule(rule, methodVisitor, context, endOfClause, i);
      } else if (pattern instanceof ListStructurePattern) {
        AsmCodeEmitter.this.emitListStructurePatternRule(rule, methodVisitor, context, matchedClause, endOfClause, i);
      } else if (pattern instanceof StringLiteral || pattern instanceof PrivateField || pattern instanceof IntLiteral
          || pattern instanceof BooleanLiteral) {

        methodVisitor.visitVarInsn(Opcodes.ALOAD, i);
        AsmCodeEmitter.this.emit(pattern);

        if (!(pattern instanceof BooleanLiteral)) {
          methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "loop/runtime/Operations", "equal",
              "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Boolean;");
        }
        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z");

        methodVisitor.visitJumpInsn(Opcodes.IFEQ, endOfClause);
      } else if (pattern instanceof TypeLiteral) {
        AsmCodeEmitter.this.emitTypePatternRule(methodVisitor, matchedClause, endOfClause, i, (TypeLiteral) pattern);
      } else if (pattern instanceof RegexLiteral) {
        final String regex = ((RegexLiteral) pattern).value;
        methodVisitor.visitLdcInsn(regex);

        // Discover named capturing groups if any.
        final NamedPattern namedPattern = NamedPattern.compile(regex);
        final List<String> groupNames = namedPattern.groupNames();
        for (final String groupVarName : groupNames) {
          final int varIndex = context.newLocalVariable(groupVarName);
          methodVisitor.visitInsn(Opcodes.ACONST_NULL);
          methodVisitor.visitVarInsn(Opcodes.ASTORE, varIndex); // initialize part to null.
        }

        final int matcherVar = context.localVarIndex(context.newLocalVariable());

        methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "loop/runtime/regex/NamedPattern", "compile",
            "(Ljava/lang/String;)Lloop/runtime/regex/NamedPattern;");

        methodVisitor.visitVarInsn(Opcodes.ALOAD, i);
        methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/String");
        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "loop/runtime/regex/NamedPattern", "matcher",
            "(Ljava/lang/CharSequence;)Lloop/runtime/regex/NamedMatcher;");

        methodVisitor.visitVarInsn(Opcodes.ASTORE, matcherVar);
        methodVisitor.visitVarInsn(Opcodes.ALOAD, matcherVar);
        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "loop/runtime/regex/NamedMatcher", "matches", "()Z");

        methodVisitor.visitJumpInsn(Opcodes.IFEQ, endOfClause);

        // Now extract the capturing group names.
        for (final String groupNameVar : groupNames) {
          methodVisitor.visitVarInsn(Opcodes.ALOAD, matcherVar);
          methodVisitor.visitLdcInsn(groupNameVar);
          methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "loop/runtime/regex/NamedMatcher", "group",
              "(Ljava/lang/String;)Ljava/lang/String;");
          methodVisitor.visitVarInsn(Opcodes.ASTORE, context.localVarIndex(groupNameVar));
        }

      } else if (pattern instanceof StringPattern) {
        AsmCodeEmitter.this.emitStringPatternRule(rule, context, endOfClause, i);
      } else if (pattern instanceof MapPattern) {
        AsmCodeEmitter.this.emitMapPatternRule(rule, context, matchedClause, endOfClause, i);
      } else if (pattern instanceof WildcardPattern) {
        // Always matches.
      }
    }

    methodVisitor.visitLabel(matchedClause);
    AsmCodeEmitter.this.emitPatternClauses(rule);
    methodVisitor.visitJumpInsn(Opcodes.GOTO, context.endOfFunction);
    methodVisitor.visitLabel(endOfClause);
  };

  private void emitTypePatternRule(final MethodVisitor methodVisitor, final Label matchedClause,
      final Label endOfClause, final int argIndex, final TypeLiteral pattern) {
    String typeName;
    final ClassDecl resolved = this.scope.resolve(pattern.name, true);
    if (resolved != null) {
      typeName = resolved.name;
    } else {
      typeName = this.scope.resolveJavaType(pattern.name);
    }

    methodVisitor.visitVarInsn(Opcodes.ALOAD, argIndex);
    methodVisitor.visitTypeInsn(Opcodes.INSTANCEOF, typeName.replace('.', '/'));
    methodVisitor.visitJumpInsn(Opcodes.IFEQ, endOfClause);
    methodVisitor.visitJumpInsn(Opcodes.GOTO, matchedClause);
  }

  private void emitPatternClauses(final PatternRule rule) {
    if (rule.rhs != null) {
      this.emit(rule.rhs);
    } else {
      this.emitGuards(rule);
    }
  }

  private void emitGuards(final PatternRule rule) {
    final MethodVisitor methodVisitor = this.methodStack.peek();

    final List<Node> children = rule.children();
    for (int i = 0, childrenSize = children.size(); i < childrenSize; i++) {
      final Node node = children.get(i);
      if (!(node instanceof Guard)) {
        throw new RuntimeException("Apparent pattern rule missing guards: " + Parser.stringify(rule));
      }
      final Guard guard = (Guard) node;
      final Label endOfClause = new Label();
      final Label matchedClause = new Label();

      // The "Otherwise" expression is a plain else.
      final boolean notElse = !(guard.expression instanceof OtherwiseGuard);
      if (notElse) {
        this.emit(guard.expression);

        methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Boolean");
        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z");
        methodVisitor.visitJumpInsn(Opcodes.IFEQ, endOfClause);
      }

      methodVisitor.visitLabel(matchedClause);
      this.emit(guard.line);
      methodVisitor.visitJumpInsn(Opcodes.GOTO, this.functionStack.peek().endOfFunction);
      methodVisitor.visitLabel(endOfClause);

      if (i == childrenSize - 1 && notElse) {
        methodVisitor.visitInsn(Opcodes.ACONST_NULL);
      }
    }
  }

  private void emitMapPatternRule(final PatternRule rule, final Context context, final Label matchedClause,
      final Label endOfClause, final int argIndex) {
    final MapPattern pattern = (MapPattern) rule.patterns.get(argIndex);
    final MethodVisitor methodVisitor = this.methodStack.peek();

    boolean hasType = false;
    final List<Node> children = pattern.children();
    for (int i = 0, childrenSize = children.size(); i < childrenSize; i++) {
      final Node child = children.get(i);
      if (child instanceof TypeLiteral) {
        hasType = true;

        final TypeLiteral typeLiteral = (TypeLiteral) child;
        String typeName;
        final ClassDecl resolved = this.scope.resolve(typeLiteral.name, true);
        if (resolved != null) {
          typeName = resolved.name;
        } else {
          typeName = this.scope.resolveJavaType(typeLiteral.name);
        }

        methodVisitor.visitVarInsn(Opcodes.ALOAD, argIndex);
        methodVisitor.visitTypeInsn(Opcodes.INSTANCEOF, typeName.replace('.', '/'));
        continue;
      }

      // Guard the first destructuring pair if there is a type pattern.
      if (hasType && i == 1) {
        methodVisitor.visitJumpInsn(Opcodes.IFEQ, endOfClause);
      }

      final DestructuringPair pair = (DestructuringPair) child;

      final int destructuredVar = context.localVarIndex(context.newLocalVariable((Variable) pair.lhs));
      this.emit(pair.rhs);

      methodVisitor.visitVarInsn(Opcodes.ASTORE, destructuredVar);
      methodVisitor.visitVarInsn(Opcodes.ALOAD, destructuredVar);
      methodVisitor.visitJumpInsn(Opcodes.IFNULL, endOfClause);
    }
    methodVisitor.visitJumpInsn(Opcodes.GOTO, matchedClause);
  }

  private void emitStringPatternRule(final PatternRule rule, final Context context, final Label endOfClause,
      final int argIndex) {
    final MethodVisitor methodVisitor = this.methodStack.peek();

    methodVisitor.visitVarInsn(Opcodes.ILOAD, context.localVarIndex(AsmCodeEmitter.IS_STRING_PREFIX + argIndex));
    methodVisitor.visitJumpInsn(Opcodes.IFEQ, endOfClause); // Not a string, so skip

    final List<Node> children = rule.patterns.get(argIndex).children();
    int i = 0;
    final int childrenSize = children.size();

    boolean splittable = false;
    final int lastIndex = context.localVarIndex(context.newLocalVariable()); // The last index of split (i.e. pattern
                                                                             // delimiter).

    // Start from offset if the first bit is a constant (corner case)
    if (childrenSize > 0 && children.get(0) instanceof StringLiteral) {
      methodVisitor.visitLdcInsn(((StringLiteral) children.get(0)).unquotedValue().length());
    } else {
      methodVisitor.visitIntInsn(Opcodes.BIPUSH, -1);
    }

    methodVisitor.visitIntInsn(Opcodes.ISTORE, lastIndex);

    for (int j = 0; j < childrenSize; j++) {
      final Node child = children.get(j);

      if (child instanceof Variable) {
        if (j < childrenSize - 1) {
          context.localVarIndex(context.newLocalVariable((Variable) child));

          final Node next = children.get(j + 1);
          if (next instanceof StringLiteral) {
            // If the next node is a string literal, then we must split this
            // string across occurrences of the given literal.
            final int thisIndex = context.localVarIndex(context.newLocalVariable());

            methodVisitor.visitVarInsn(Opcodes.ALOAD, argIndex);
            methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/String");
            this.emit(next);

            // If this is the second or greater pattern matcher, seek from the last location.
            if (splittable) {
              methodVisitor.visitIntInsn(Opcodes.ILOAD, lastIndex);
              methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "indexOf",
                  "(Ljava/lang/String;I)I");
            } else {
              methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "indexOf",
                  "(Ljava/lang/String;)I");
            }
            methodVisitor.visitIntInsn(Opcodes.ISTORE, thisIndex);

            methodVisitor.visitIntInsn(Opcodes.ILOAD, thisIndex);
            methodVisitor.visitIntInsn(Opcodes.BIPUSH, -1);
            methodVisitor.visitJumpInsn(Opcodes.IF_ICMPLE, endOfClause); // Jump out of this clause

            final int matchedPieceVar = context.localVarIndex(context.newLocalVariable((Variable) child));

            methodVisitor.visitVarInsn(Opcodes.ALOAD, argIndex);
            methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/String");

            methodVisitor.visitIntInsn(Opcodes.ILOAD, lastIndex);

            final Label startFromLastIndex = new Label();
            final Label startFromZeroIndex = new Label();
            methodVisitor.visitIntInsn(Opcodes.BIPUSH, -1);
            methodVisitor.visitJumpInsn(Opcodes.IF_ICMPNE, startFromLastIndex);

            // Either start from 0 or lastindex of split.
            methodVisitor.visitIntInsn(Opcodes.BIPUSH, 0);
            methodVisitor.visitJumpInsn(Opcodes.GOTO, startFromZeroIndex);
            methodVisitor.visitLabel(startFromLastIndex);
            methodVisitor.visitIntInsn(Opcodes.ILOAD, lastIndex);
            methodVisitor.visitLabel(startFromZeroIndex);

            methodVisitor.visitIntInsn(Opcodes.ILOAD, thisIndex);
            methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "substring",
                "(II)Ljava/lang/String;");

            // Save this piece into our variable.
            methodVisitor.visitVarInsn(Opcodes.ASTORE, matchedPieceVar);

            // Advance the index by the length of this match.
            methodVisitor.visitLdcInsn(((StringLiteral) next).unquotedValue().length());
            methodVisitor.visitIntInsn(Opcodes.ILOAD, thisIndex);
            methodVisitor.visitInsn(Opcodes.IADD);

            methodVisitor.visitIntInsn(Opcodes.ISTORE, lastIndex);

            splittable = true;
          } else {
            methodVisitor.visitVarInsn(Opcodes.ALOAD, argIndex);
            final int matchedPieceVar = context.localVarIndex(context.newLocalVariable((Variable) child));
            methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/String");
            methodVisitor.visitLdcInsn(i);
            methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "charAt", "(I)C");
            methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Character", "valueOf",
                "(C)Ljava/lang/Character;");
            methodVisitor.visitVarInsn(Opcodes.ASTORE, matchedPieceVar);
          }
        } else {
          final int matchedPieceVar = context.localVarIndex(context.newLocalVariable((Variable) child));
          final int strLen = context.localVarIndex(AsmCodeEmitter.RUNTIME_STR_LEN_PREFIX + argIndex);

          // methodVisitor.visitVarInsn(ALOAD, argIndex);
          methodVisitor.visitIntInsn(Opcodes.ILOAD, strLen);
          methodVisitor.visitIntInsn(Opcodes.BIPUSH, 1);

          final Label restOfString = new Label();
          final Label assignToPiece = new Label();

          methodVisitor.visitJumpInsn(Opcodes.IF_ICMPNE, restOfString);
          methodVisitor.visitLdcInsn("");
          methodVisitor.visitJumpInsn(Opcodes.GOTO, assignToPiece);
          methodVisitor.visitLabel(restOfString);

          methodVisitor.visitVarInsn(Opcodes.ALOAD, argIndex);
          methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/String");

          methodVisitor.visitIntInsn(Opcodes.ILOAD, lastIndex);
          methodVisitor.visitIntInsn(Opcodes.BIPUSH, -1);

          final Label restOfStringFromI = new Label();
          final Label reduceString = new Label();
          methodVisitor.visitJumpInsn(Opcodes.IF_ICMPLE, restOfStringFromI);
          methodVisitor.visitIntInsn(Opcodes.ILOAD, lastIndex);
          methodVisitor.visitJumpInsn(Opcodes.GOTO, reduceString);
          methodVisitor.visitLabel(restOfStringFromI);
          methodVisitor.visitLdcInsn(i);
          methodVisitor.visitLabel(reduceString);
          methodVisitor
              .visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "substring", "(I)Ljava/lang/String;");

          methodVisitor.visitLabel(assignToPiece);
          methodVisitor.visitVarInsn(Opcodes.ASTORE, matchedPieceVar);
        }
        i++;
      }
    }
  }

  private void emitListStructurePatternRule(final PatternRule rule, final MethodVisitor methodVisitor,
      final Context context, final Label matchedClause, final Label endOfClause, final int argIndex) {
    final ListStructurePattern listPattern = (ListStructurePattern) rule.patterns.get(argIndex);
    final List<Node> children = listPattern.children();

    final int runtimeListSizeVar = context.localVarIndex(AsmCodeEmitter.RUNTIME_LIST_SIZE_VAR_PREFIX + argIndex);
    methodVisitor.visitIntInsn(Opcodes.ILOAD, runtimeListSizeVar);
    methodVisitor.visitIntInsn(Opcodes.BIPUSH, children.size());
    methodVisitor.visitJumpInsn(Opcodes.IF_ICMPNE, endOfClause);

    // Slice the list by terminals in the pattern list.
    for (int j = 0, childrenSize = children.size(); j < childrenSize; j++) {
      final Node child = children.get(j);
      if (child instanceof Variable) {
        this.trackLineAndColumn(child);

        // Store into structure vars.
        final int localVar = context.localVarIndex(context.newLocalVariable((Variable) child));

        methodVisitor.visitVarInsn(Opcodes.ALOAD, argIndex);
        methodVisitor.visitIntInsn(Opcodes.BIPUSH, j); // We dont support matching >128 args ;)
        methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List", "get", "(I)Ljava/lang/Object;");
        methodVisitor.visitVarInsn(Opcodes.ASTORE, localVar);
      }
    }

    methodVisitor.visitJumpInsn(Opcodes.GOTO, matchedClause);
  }

  private void emitListDestructuringPatternRule(final PatternRule rule, final MethodVisitor methodVisitor,
      final Context context, final Label endOfClause, final int argIndex) {
    final ListDestructuringPattern listPattern = (ListDestructuringPattern) rule.patterns.get(argIndex);
    final Label noMatch = new Label();

    final int runtimeListSizeVar = context.localVarIndex(AsmCodeEmitter.RUNTIME_LIST_SIZE_VAR_PREFIX + argIndex);

    final int size = listPattern.children().size();
    if (size == 0) {
      methodVisitor.visitIntInsn(Opcodes.ILOAD, runtimeListSizeVar);
      // methodVisitor.visitJumpInsn(IFEQ, matchedClause);
      // methodVisitor.visitJumpInsn(GOTO, endOfClause);
      methodVisitor.visitJumpInsn(Opcodes.IFNE, endOfClause);
    } else if (size == 1) {
      methodVisitor.visitIntInsn(Opcodes.ILOAD, runtimeListSizeVar);
      methodVisitor.visitIntInsn(Opcodes.BIPUSH, 1);
      // methodVisitor.visitJumpInsn(IFNE, matchedClause);
      // methodVisitor.visitJumpInsn(GOTO, endOfClause);
      methodVisitor.visitJumpInsn(Opcodes.IFNE, endOfClause);

    } else {
      // Slice the list by terminals in the pattern list.
      int i = 0;
      final List<Node> children = listPattern.children();
      for (int j = 0, childrenSize = children.size(); j < childrenSize; j++) {
        final Node child = children.get(j);
        if (child instanceof Variable) {
          this.trackLineAndColumn(child);
          final int localVar = context.localVarIndex(context.newLocalVariable((Variable) child));

          if (j < childrenSize - 1) {
            methodVisitor.visitVarInsn(Opcodes.ALOAD, argIndex);
            methodVisitor.visitLdcInsn(i);
            methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List", "get", "(I)Ljava/lang/Object;");
            methodVisitor.visitVarInsn(Opcodes.ASTORE, localVar);
          } else {
            methodVisitor.visitIntInsn(Opcodes.ILOAD, runtimeListSizeVar);
            methodVisitor.visitIntInsn(Opcodes.BIPUSH, 1);
            final Label storeEmptyList = new Label();
            methodVisitor.visitJumpInsn(Opcodes.IF_ICMPEQ, storeEmptyList);

            // Otherwise store a slice of the list.
            methodVisitor.visitVarInsn(Opcodes.ALOAD, argIndex);
            methodVisitor.visitLdcInsn(i);
            methodVisitor.visitIntInsn(Opcodes.ILOAD, runtimeListSizeVar);
            methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List", "subList", "(II)Ljava/util/List;");
            methodVisitor.visitVarInsn(Opcodes.ASTORE, localVar);

            final Label endOfStoreEmptyList = new Label();
            methodVisitor.visitJumpInsn(Opcodes.GOTO, endOfStoreEmptyList);

            methodVisitor.visitLabel(storeEmptyList);
            methodVisitor.visitFieldInsn(Opcodes.GETSTATIC, "java/util/Collections", "EMPTY_LIST", "Ljava/util/List;");
            methodVisitor.visitVarInsn(Opcodes.ASTORE, localVar);
            methodVisitor.visitLabel(endOfStoreEmptyList);
            // methodVisitor.visitJumpInsn(GOTO, matchedClause);

          }

          i++;
        }
      }
    }

    methodVisitor.visitLabel(noMatch);
  }
}
