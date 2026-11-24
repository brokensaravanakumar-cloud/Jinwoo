package com.jinwoo.ai

import com.jinwoo.ai.actions.Calculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalculatorTest {
    @Test fun precedence() = assertEquals(14.0, Calculator.evaluate("2+3*4")!!, 1e-9)
    @Test fun parentheses() = assertEquals(20.0, Calculator.evaluate("(2+3)*4")!!, 1e-9)
    @Test fun powerIsRightAssociative() = assertEquals(512.0, Calculator.evaluate("2^3^2")!!, 1e-9)
    @Test fun division() = assertEquals(2.5, Calculator.evaluate("10/4")!!, 1e-9)
    @Test fun divideByZeroIsNull() = assertNull(Calculator.evaluate("5/0"))
    @Test fun garbageIsNull() = assertNull(Calculator.evaluate("2+*3"))
    @Test fun spokenMath() = assertEquals("12+3*4", Calculator.extractExpression("What is 12 plus 3 times 4?"))
    @Test fun nonMathIsNull() = assertNull(Calculator.extractExpression("what is artificial intelligence"))
    @Test fun bareNumberIsNull() = assertNull(Calculator.extractExpression("what is 5"))
    @Test fun format() { assertEquals("24", Calculator.format(24.0)); assertEquals("2.5", Calculator.format(2.5)) }
}
