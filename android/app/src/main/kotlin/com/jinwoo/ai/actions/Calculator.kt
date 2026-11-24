package com.jinwoo.ai.actions

import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow

/** Spoken-math helper: "what is 12 plus 3 times 4" -> 24. Pure Kotlin, unit tested. */
object Calculator {
    private val words = listOf(
        "multiplied by" to "*", "divided by" to "/", "to the power of" to "^",
        "plus" to "+", "minus" to "-", "times" to "*", "over" to "/", " x " to "*", " into " to "*",
    )
    private val fillers = Regex("""\b(what is|what's|whats|calculate|compute|solve|how much is|equals|equal to|the answer to|please)\b""")

    /** Returns a clean math expression if the whole utterance is arithmetic, otherwise null. */
    fun extractExpression(raw: String): String? {
        var s = " " + raw.lowercase(Locale.ROOT).replace(",", "") + " "
        for ((w, sym) in words) s = s.replace(w, " $sym ")
        s = fillers.replace(s, " ").replace("?", " ").trim()
        if (s.isEmpty() || s.none { it.isDigit() }) return null
        if (!s.all { it.isDigit() || it in "+-*/^().% " }) return null
        if (s.none { it in "+-*/^%" }) return null
        return s.replace(" ", "")
    }

    fun evaluate(expr: String): Double? = try {
        val v = Parser(expr).parse()
        if (v.isNaN() || v.isInfinite()) null else v
    } catch (ignored: Exception) { null }

    fun format(v: Double): String =
        if (v == v.toLong().toDouble() && abs(v) < 1e15) v.toLong().toString()
        else String.format(Locale.US, "%.6f", v).trimEnd('0').trimEnd('.')

    private class Parser(val s: String) {
        var i = 0
        fun parse(): Double { val v = expr(); if (i != s.length) throw IllegalArgumentException("trailing"); return v }
        fun expr(): Double {
            var v = term()
            while (i < s.length && (s[i] == '+' || s[i] == '-')) { val op = s[i++]; val r = term(); v = if (op == '+') v + r else v - r }
            return v
        }
        fun term(): Double {
            var v = factor()
            while (i < s.length && (s[i] == '*' || s[i] == '/' || s[i] == '%')) {
                val op = s[i++]; val r = factor()
                v = when (op) { '*' -> v * r; '/' -> { if (r == 0.0) throw ArithmeticException(); v / r }; else -> v % r }
            }
            return v
        }
        fun factor(): Double { val b = unary(); if (i < s.length && s[i] == '^') { i++; return b.pow(factor()) }; return b }
        fun unary(): Double {
            if (i < s.length && s[i] == '-') { i++; return -unary() }
            if (i < s.length && s[i] == '+') { i++; return unary() }
            return primary()
        }
        fun primary(): Double {
            if (i < s.length && s[i] == '(') {
                i++; val v = expr()
                if (i >= s.length || s[i] != ')') throw IllegalArgumentException("paren")
                i++; return v
            }
            val st = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            if (st == i) throw IllegalArgumentException("number")
            return s.substring(st, i).toDouble()
        }
    }
}
