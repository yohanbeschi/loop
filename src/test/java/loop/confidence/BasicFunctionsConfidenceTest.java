package loop.confidence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.TreeMap;

import loop.LoopError;
import loop.LoopTest;
import loop.TestFilesLoader;

import org.junit.Test;

/**
 * Confidence tests run a bunch of semi-realistic programs and assert that their results are
 * as expected. This is meant to be our functional regression test suite.
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public class BasicFunctionsConfidenceTest extends LoopTest {
  @Test
  public final void reverseListPatternMatching() {
    assertEquals(Arrays.asList(3, 2, 1), TestFilesLoader.run("loop/confidence/reverse.loop"));
  }

  @Test
  public final void simpleAssignment() {
    assertEquals(Arrays.asList(1, 2, 3), TestFilesLoader.run("loop/confidence/assignment.loop"));
  }

  @Test
  public final void counterTailCallElimination() {
    // Such a high number would normally throw a StackOverflowError. But using TCO,
    // we can count this high with just one stack frame.
    assertEquals(500000, TestFilesLoader.run("loop/confidence/forward.loop"));
  }

  @Test
  public final void counterTailCallEliminationWithIf() {
    // Such a high number would normally throw a StackOverflowError. But using TCO,
    // we can count this high with just one stack frame.
    assertEquals(500000, TestFilesLoader.run("loop/confidence/forward2.loop"));
  }

  @Test
  public final void freeExpressionsInInitializerBlock() {
    assertEquals("bye", TestFilesLoader.run("loop/confidence/free_exprs.loop"));
  }

  @Test
  public final void counterIfThenElseTailCallElimination() {
    // Such a high number would normally throw a StackOverflowError. But using TCO,
    // we can count this high with just one stack frame.
    assertEquals(500000, TestFilesLoader.run("loop/confidence/forward_if-then-else.loop"));
  }

  @Test
  public final void counterIfThenElseTailCallElimination2() {
    // Such a high number would normally throw a StackOverflowError. But using TCO,
    // we can count this high with just one stack frame.
    assertEquals(500000, TestFilesLoader.run("loop/confidence/forward_if-then-else2.loop"));
  }

  @Test
  public final void groupPostfixCalling1() {
    assertEquals(8, TestFilesLoader.run("loop/confidence/group_postfix_call_1.loop"));
  }

  @Test
  public final void groupPostfixCalling2() {
    assertEquals(8, TestFilesLoader.run("loop/confidence/group_postfix_call_2.loop"));
  }

  @Test
  public final void groupPostfixCalling3() {
    assertEquals(12, TestFilesLoader.run("loop/confidence/group_postfix_call_3.loop"));
  }

  @Test
  public final void dynamicPostfixCalling1() {
    assertEquals(3, TestFilesLoader.run("loop/confidence/postfix_calling_adv_1.loop"));
  }

  @Test
  public final void dynamicPostfixCalling2() {
    assertEquals(4, TestFilesLoader.run("loop/confidence/postfix_calling_adv_2.loop"));
  }

  @Test
  public final void nothingInstance1() {
    assertNull(TestFilesLoader.run("loop/confidence/nothing_1.loop"));
  }

  @Test
  public final void nothingInstance2() {
    assertNull(TestFilesLoader.run("loop/confidence/nothing_2.loop"));
  }

  @Test
  public final void javaClassRefs() {
    assertEquals("java.util.Date", TestFilesLoader.run("loop/confidence/java_class_ref_1.loop"));
  }

  @Test
  public final void booleanOps() {
    assertEquals(true, TestFilesLoader.run("loop/confidence/and_or.loop"));
  }

  @Test
  public final void embiggenInteger() {
    assertEquals(BigInteger.valueOf(4), TestFilesLoader.run("loop/confidence/embiggen_1.loop"));
  }

  @Test
  public final void embiggenLong() {
    assertEquals(BigInteger.valueOf(4), TestFilesLoader.run("loop/confidence/embiggen_2.loop"));
  }

  @Test
  public final void embiggenDouble() {
    assertEquals(BigDecimal.valueOf(4.0), TestFilesLoader.run("loop/confidence/embiggen_3.loop"));
  }

  @Test
  public final void embiggenString() {
    assertEquals("SMALLEST MAN", TestFilesLoader.run("loop/confidence/embiggen_4.loop"));
  }

  @Test
  public final void functionReferences() {
    assertEquals(2, TestFilesLoader.run("loop/confidence/func_refs.loop"));
  }

  @Test
  public final void longArithmetic() {
    assertEquals(true, TestFilesLoader.run("loop/confidence/longs.loop"));
  }

  @Test
  public final void longArithmetic2() {
    assertEquals(true, TestFilesLoader.run("loop/confidence/longs_2.loop"));
  }

  @Test
  public final void doubleArithmetic() {
    assertEquals(true, TestFilesLoader.run("loop/confidence/doubles.loop"));
  }

  @Test
  public final void doubleArithmetic2() {
    assertEquals(true, TestFilesLoader.run("loop/confidence/doubles_2.loop"));
  }

  @Test
  public final void bigIntegerArithmetic() {
    assertEquals(true, TestFilesLoader.run("loop/confidence/big_ints.loop"));
  }

  @Test
  public final void bigIntegerArithmetic2() {
    assertEquals(true, TestFilesLoader.run("loop/confidence/big_ints_2.loop"));
  }
  @Test
  public final void bigDecimalArithmetic() {
    assertEquals(true, TestFilesLoader.run("loop/confidence/big_dec.loop"));
  }

  @Test
  public final void bigDecimalArithmetic2() {
    assertEquals(true, TestFilesLoader.run("loop/confidence/big_dec_2.loop"));
  }

  @Test
  public final void notEqual() {
    assertEquals(true, TestFilesLoader.run("loop/confidence/not_equal.loop"));
  }

  @Test
  public final void multilineFunctions() {
    assertEquals(true, TestFilesLoader.run("loop/confidence/multiline.loop"));
  }

  @Test
  public final void bangAndQnInIdentifiers() {
    assertEquals(true, TestFilesLoader.run("loop/confidence/identifiers.loop"));
  }

  @Test
  public final void notEqual2() {
    assertEquals(true, TestFilesLoader.run("loop/confidence/not_equal_2.loop"));
  }

  @Test
  public final void reverseListPatternMatchingGuarded1() {
    assertEquals(Arrays.asList(3, 2, 1), TestFilesLoader.run("loop/confidence/reverse_guarded_1.loop"));
  }

  @Test
  public final void reverseListPatternMatchingGuarded2() {
    // Doesn't reverse the list if the first element is >= 10.
    assertEquals(Arrays.asList(10, 20, 30), TestFilesLoader.run("loop/confidence/reverse_guarded_2.loop"));
  }

  @Test
  public final void listStructurePatternMatchingGuarded1() {
    assertEquals(Arrays.asList(2, 3, 10), TestFilesLoader.run("loop/confidence/list_pattern_guarded_1.loop"));
  }

  @Test
  public final void listStructurePatternMatchingGuarded2() {
    assertEquals(Arrays.asList(5, 2, 3), TestFilesLoader.run("loop/confidence/list_pattern_guarded_2.loop"));
  }

  @Test
  public final void listStructurePatternMatchingGuarded3() {
    assertEquals(Arrays.asList(55), TestFilesLoader.run("loop/confidence/list_pattern_guarded_3.loop"));
  }

  @Test
  public final void reverseListPatternMatchingUsingWhereBlock() {
    assertEquals(Arrays.asList(3, 2, 1), TestFilesLoader.run("loop/confidence/whereblock_1.loop"));
  }

  @Test
  public final void reverseListPatternMatchingUsingNestedWhereBlocks() {
    assertEquals(Arrays.asList(4, 3, 2, 1), TestFilesLoader.run("loop/confidence/whereblock_2.loop"));
  }

  @Test
  public final void whereBlockAssignments() {
    assertEquals(26208, TestFilesLoader.run("loop/confidence/whereblock_3.loop"));
  }

  @Test
  public final void objectPatternMatch1() {
    assertEquals("Stephen", TestFilesLoader.run("loop/confidence/pattern_matching_objects_1.loop"));
  }

  @Test
  public final void symbolsAndPatternMatching() {
    assertEquals("Stephen", TestFilesLoader.run("loop/confidence/symbols_1.loop"));
  }

  @Test
  public final void patternMatchingAgainstSymbols() {
    assertEquals("1234", TestFilesLoader.run("loop/confidence/symbols_3.loop"));
  }

  @Test
  public final void symbolsSimple() {
    Object run = TestFilesLoader.run("loop/confidence/symbols_2.loop");
    assertTrue(run instanceof Boolean);
    assertTrue((Boolean) run);
  }

  @Test
  public final void objectPatternMatch2() {
    assertEquals("Stephenpa", TestFilesLoader.run("loop/confidence/pattern_matching_objects_2.loop"));
  }

  @Test
  public final void reverseStringPatternMatching() {
    assertEquals("olleh", TestFilesLoader.run("loop/confidence/reverse_string.loop"));
  }

  @Test
  public final void splitLinesStringPatternMatching() {
    assertEquals("hellotheredude", TestFilesLoader.run("loop/confidence/split_lines_string.loop"));
  }

  @Test
  public final void stringLiteralIsUnescaped() {
    assertEquals("hello\nthere\ndude", TestFilesLoader.run("loop/confidence/stringthing.loop"));
  }

//  @Test
  public final void splitLinesStringMultiargPatternMatching() {
    assertEquals("hellotheredude", TestFilesLoader.run("loop/confidence/split_lines_string_2.loop"));
  }

  @Test
  public final void splitVariousStringsPatternMatching() {
    assertEquals("1234", TestFilesLoader.run("loop/confidence/split_various_string.loop"));
  }

  @Test
  public final void splitVariousStringsPatternMatchingWithWildcards() {
    assertEquals("3", TestFilesLoader.run("loop/confidence/split_various_selective.loop"));
  }

  @Test
  public final void splitVariousStringsPatternMatchingWithWildcards2() {
    assertEquals("1234", TestFilesLoader.run("loop/confidence/split_various_selective_2.loop"));
  }

  @Test
  public final void splitVariousStringsPatternMatchingSimple() {
    assertEquals("Prime, Optimus", TestFilesLoader.run("loop/confidence/split_various_selective_3.loop"));
  }

  @Test
  public final void localVarsShouldBeScopedOverFunctionRefs() {
    assertEquals("Prime, Optimus", TestFilesLoader.run("loop/confidence/local_vars_scope.loop"));
  }

  @Test(expected = RuntimeException.class)
  public final void splitVariousStringsPatternMatchingNotAllMatches() {
    assertTrue(TestFilesLoader.run("loop/confidence/split_various_string_error.loop") instanceof LoopError);
  }

  @Test(expected = RuntimeException.class)
  public final void reverseLoopPatternMissingError() {
    assertTrue(TestFilesLoader.run("loop/confidence/reverse_error.loop") instanceof LoopError);
  }

  @Test
  public final void callJavaMethodOnString() {
    assertEquals("hello", TestFilesLoader.run("loop/confidence/java_call_on_string.loop"));
  }

  @Test
  public final void nullSafeCallChain1() {
    assertNull(TestFilesLoader.run("loop/confidence/nullsafe_1.loop"));
  }

  @Test
  public final void nullSafeCallChain2() {
    assertEquals(null, TestFilesLoader.run("loop/confidence/nullsafe_2.loop"));
  }

  @Test
  public final void stringInterpolation1() {
    assertEquals("Hello, Dhanji", TestFilesLoader.run("loop/confidence/string_lerp_1.loop"));
  }

  @Test
  public final void stringInterpolation2() {
    assertEquals("There are 8 things going on in England",
        TestFilesLoader.run("loop/confidence/string_lerp_2.loop"));
  }

  @Test
  public final void stringInterpolation3() {
    assertEquals("There are @{2 + 6} things going @{\"on\"} in @{name}land",
        TestFilesLoader.run("loop/confidence/string_lerp_3.loop"));
  }

  @Test
  public final void intLiteralPatternMatching() {
    Map<String, String> map = new HashMap<String, String>();
    map.put("name", "Michael");
    map.put("age", "212");

    assertEquals(map, TestFilesLoader.run("loop/confidence/literal_pattern_matching.loop"));
  }

  @Test
  public final void wildcardPatternMatchingGuarded1() {
    Map<String, String> map = new HashMap<String, String>();
    map.put("count", "10");

    assertEquals(map, TestFilesLoader.run("loop/confidence/wildcard_pattern_matching_guarded_1.loop"));
  }

  @Test
  public final void wildcardPatternMatchingGuarded2() {
    Map<String, String> map = new HashMap<String, String>();
    map.put("count", "100");

    assertEquals(map, TestFilesLoader.run("loop/confidence/wildcard_pattern_matching_guarded_2.loop"));
  }

  @Test
  public final void wildcardPatternMatchingGuarded3() {
    Map<String, String> map = new HashMap<String, String>();
    map.put("count", "infinity");

    assertEquals(map, TestFilesLoader.run("loop/confidence/wildcard_pattern_matching_guarded_3.loop"));
  }

  @Test
  public final void regexPatternMatching() {
    Map<String, String> map = new HashMap<String, String>();
    map.put("name", "Michael");
    map.put("age", "212");

    assertEquals(map, TestFilesLoader.run("loop/confidence/regex_pattern_matching.loop"));
  }

  @Test
  public final void regexPatternMatchingGuarded1() {
    Map<String, String> map = new HashMap<String, String>();
    map.put("name", "Dhanji");
    map.put("age", "20");

    assertEquals(map, TestFilesLoader.run("loop/confidence/regex_pattern_matching_guarded_1.loop"));
  }

  @Test
  public final void regexPatternMatchingGuarded2() {
    Map<String, String> map = new HashMap<String, String>();

    assertEquals(map, TestFilesLoader.run("loop/confidence/regex_pattern_matching_guarded_2.loop"));
  }

  @Test
  public final void regexPatternMatchingGuarded3() {
    Map<String, String> map = new HashMap<String, String>();
    map.put("name", "Unknown");
    map.put("age", "-1");

    assertEquals(map, TestFilesLoader.run("loop/confidence/regex_pattern_matching_guarded_3.loop"));
  }

  @Test
  public final void patternMatchingMultipleArg1() {
    Map<String, String> map = new HashMap<String, String>();
    map.put("count", "10");

    assertEquals(map, TestFilesLoader.run("loop/confidence/pattern_matching_multiarg_1.loop"));
  }

  @Test
  public final void propertyNavigation1() {
    assertEquals("Peter", TestFilesLoader.run("loop/confidence/property_nav_1.loop"));
  }

  @Test
  public final void simpleSet() {
    assertEquals(new HashSet<Integer>(Arrays.asList(1, 2, 3, 5)),
        TestFilesLoader.run("loop/confidence/sets_1.loop"));
  }

  @Test
  public final void stringSet() {
    assertEquals(new HashSet<String>(Arrays.asList("hi")),
        TestFilesLoader.run("loop/confidence/sets_2.loop"));
  }

  @Test
  @SuppressWarnings("unchecked")
  public final void simpleTree() {
    Object tree = TestFilesLoader.run("loop/confidence/trees_1.loop");
    assertTrue(tree instanceof TreeMap);
    assertTrue(Arrays.asList(
        "l",
        "o",
        "o",
        "p").equals(new ArrayList<String>(((Map) tree).values())));
  }

  @Test
  public final void setAndPutValues() {
    Object result = TestFilesLoader.run("loop/confidence/set_put.loop");

    Map<String, String> map = new HashMap<String, String>();
    map.put("name", "Sol");

    assertEquals(map, result);
  }

  @Test
  public final void setAndPutValueIntoJavaObject() {
    Object result = TestFilesLoader.run("loop/confidence/set_put_2.loop");
    assertEquals(new Date(0), result);
  }
}
