package com.enginotes.app

import kotlin.math.*

// ============================================================================================
// FORMULA ENGINE — Excel-style formulas for TableItem
// ============================================================================================
// Architecture, top to bottom:
//   1. FV               — the value type formulas produce (number/text/bool/error)
//   2. CellRefUtil       — "A1" <-> (row, col) conversions
//   3. Tok / tokenize()  — turns "=SUM(A1:A3)*2" into a flat token list
//   4. Node / Parser     — turns tokens into an expression tree (standard Excel precedence)
//   5. Evaluator         — walks the tree, resolving cell refs via a caller-supplied callback
//   6. FunctionLibrary   — the dispatch table of built-in functions (~110 of them)
//   7. FormulaEngine     — the only PUBLIC entry point; everything above is file-private
//   8. TableItem.recalcAllFormulas() — walks every cell, evaluates formulas, caches results,
//      with circular-reference detection. This is the one thing MainActivity actually calls
//      after every cell edit.
//
// Deliberately NOT implemented: all ~450 official Excel functions. Many of those (XIRR, BESSEL*,
// GAMMA.DIST, array/spill formulas like FILTER/UNIQUE/LAMBDA, etc.) are deep enough that hand-
// writing them without a test suite risks silent math bugs. This covers the ~110 functions people
// actually reach for day to day — math, logic, text, dates, lookup, stats, info — organized as a
// single dispatch map, so adding function #111 later is a one-line addition, not a redesign.
//
// Also not implemented: reference-shifting on row/column insert/delete (like Excel adjusting
// "=A1" to "=A2" when you insert a row above). Formulas keep referring to the same row/col index
// regardless of later structural edits. Worth knowing if you build on this.
// ============================================================================================

// -------------------- Value type --------------------
private sealed class FV {
    data class Num(val v: Double) : FV()
    data class Str(val v: String) : FV()
    data class Bool(val v: Boolean) : FV()
    data class Err(val code: String) : FV()

    val isError: Boolean get() = this is Err

    fun asDouble(): Double = when (this) {
        is Num -> v
        is Bool -> if (v) 1.0 else 0.0
        is Str -> v.trim().toDoubleOrNull() ?: Double.NaN
        is Err -> Double.NaN
    }

    fun asString(): String = when (this) {
        is Num -> FormulaEngine.formatNumber(v)
        is Str -> v
        is Bool -> if (v) "TRUE" else "FALSE"
        is Err -> code
    }

    fun asBool(): Boolean = when (this) {
        is Bool -> v
        is Num -> v != 0.0
        is Str -> v.equals("TRUE", ignoreCase = true)
        is Err -> false
    }

    val isBlank: Boolean get() = this is Str && v.isEmpty()
}

// -------------------- Cell reference helpers --------------------
private object CellRefUtil {
    fun colIndexFromLabel(label: String): Int {
        var n = 0
        for (ch in label.uppercase()) {
            if (ch < 'A' || ch > 'Z') return -1
            n = n * 26 + (ch - 'A' + 1)
        }
        return n - 1
    }

    // Attempts to parse a cell reference (optionally $-prefixed on column and/or row) starting
    // at `start`. Returns (row, col, charsConsumed) or null if what's there isn't a valid ref.
    fun parseRefAt(s: String, start: Int): Triple<Int, Int, Int>? {
        var i = start
        if (i < s.length && s[i] == '$') i++
        val colStart = i
        while (i < s.length && s[i].uppercaseChar() in 'A'..'Z') i++
        if (i == colStart) return null
        val colLabel = s.substring(colStart, i)
        if (i < s.length && s[i] == '$') i++
        val rowStart = i
        while (i < s.length && s[i].isDigit()) i++
        if (i == rowStart) return null
        val rowLabel = s.substring(rowStart, i)
        val col = colIndexFromLabel(colLabel)
        val row = rowLabel.toIntOrNull()?.minus(1) ?: return null
        if (col < 0 || row < 0) return null
        return Triple(row, col, i - start)
    }
}

// -------------------- Tokenizer --------------------
private sealed class Tok {
    data class Num(val v: Double) : Tok()
    data class Str(val v: String) : Tok()
    data class Ref(val row: Int, val col: Int) : Tok()
    object Colon : Tok()
    object Comma : Tok()
    object LParen : Tok()
    object RParen : Tok()
    data class Ident(val name: String) : Tok()
    data class Op(val s: String) : Tok()
    object Percent : Tok()
}

private fun tokenize(src: String): List<Tok> {
    val toks = ArrayList<Tok>()
    var i = 0
    val n = src.length
    while (i < n) {
        val c = src[i]
        when {
            c.isWhitespace() -> i++
            c == '(' -> { toks.add(Tok.LParen); i++ }
            c == ')' -> { toks.add(Tok.RParen); i++ }
            c == ',' -> { toks.add(Tok.Comma); i++ }
            c == ':' -> { toks.add(Tok.Colon); i++ }
            c == '%' -> { toks.add(Tok.Percent); i++ }
            c == '"' -> {
                val sb = StringBuilder(); i++
                while (i < n && src[i] != '"') {
                    if (src[i] == '\\' && i + 1 < n) { sb.append(src[i + 1]); i += 2 } else { sb.append(src[i]); i++ }
                }
                if (i < n) i++ // closing quote
                toks.add(Tok.Str(sb.toString()))
            }
            c == '<' || c == '>' -> {
                if (i + 1 < n && src[i + 1] == '=') { toks.add(Tok.Op(src.substring(i, i + 2))); i += 2 }
                else if (c == '<' && i + 1 < n && src[i + 1] == '>') { toks.add(Tok.Op("<>")); i += 2 }
                else { toks.add(Tok.Op(c.toString())); i++ }
            }
            c == '=' || c == '+' || c == '-' || c == '*' || c == '/' || c == '^' || c == '&' -> { toks.add(Tok.Op(c.toString())); i++ }
            c.isDigit() || (c == '.' && i + 1 < n && src[i + 1].isDigit()) -> {
                val start = i
                while (i < n && (src[i].isDigit() || src[i] == '.')) i++
                if (i < n && (src[i] == 'e' || src[i] == 'E')) {
                    val save = i; i++
                    if (i < n && (src[i] == '+' || src[i] == '-')) i++
                    if (i < n && src[i].isDigit()) { while (i < n && src[i].isDigit()) i++ } else i = save
                }
                toks.add(Tok.Num(src.substring(start, i).toDouble()))
            }
            c == '$' || c.uppercaseChar() in 'A'..'Z' -> {
                val refResult = CellRefUtil.parseRefAt(src, i)
                if (refResult != null) {
                    val (row, col, consumed) = refResult
                    // Function names that happen to look like refs (e.g. LOG10) are only real
                    // refs if NOT immediately followed by "(" — that means a function call.
                    var peek = i + consumed
                    while (peek < n && src[peek].isWhitespace()) peek++
                    if (peek < n && src[peek] == '(') {
                        val start = i
                        while (i < n && (src[i].isLetterOrDigit() || src[i] == '_' || src[i] == '.')) i++
                        toks.add(Tok.Ident(src.substring(start, i)))
                    } else {
                        toks.add(Tok.Ref(row, col)); i += consumed
                    }
                } else {
                    val start = i
                    while (i < n && (src[i].isLetterOrDigit() || src[i] == '_' || src[i] == '.')) i++
                    if (i == start) i++ else toks.add(Tok.Ident(src.substring(start, i)))
                }
            }
            else -> i++ // skip anything unrecognized rather than hard-failing the whole formula
        }
    }
    return toks
}

// -------------------- AST --------------------
private sealed class Node
private data class NNum(val v: Double) : Node()
private data class NStr(val v: String) : Node()
private data class NRef(val row: Int, val col: Int) : Node()
private data class NRange(val r1: Int, val c1: Int, val r2: Int, val c2: Int) : Node()
private data class NUnary(val op: Char, val e: Node) : Node()
private data class NBin(val op: String, val l: Node, val r: Node) : Node()
private data class NPercent(val e: Node) : Node()
private data class NCall(val name: String, val args: List<Node>) : Node()

// -------------------- Parser (recursive descent, standard Excel precedence) --------------------
// comparison  <  concat (&)  <  additive (+ -)  <  multiplicative (* /)  <  unary (- +)
//   < power (^, right-assoc)  <  postfix percent (%)  <  primary
private class Parser(private val toks: List<Tok>) {
    private var pos = 0
    private fun peek(): Tok? = toks.getOrNull(pos)
    private fun next(): Tok? = toks.getOrNull(pos)?.also { pos++ }

    fun parse(): Node = parseComparison()

    private fun parseComparison(): Node {
        var left = parseConcat()
        while (true) {
            val t = peek()
            if (t is Tok.Op && (t.s == "=" || t.s == "<>" || t.s == "<" || t.s == ">" || t.s == "<=" || t.s == ">=")) {
                next(); left = NBin(t.s, left, parseConcat())
            } else break
        }
        return left
    }

    private fun parseConcat(): Node {
        var left = parseAdditive()
        while (true) {
            val t = peek()
            if (t is Tok.Op && t.s == "&") { next(); left = NBin("&", left, parseAdditive()) } else break
        }
        return left
    }

    private fun parseAdditive(): Node {
        var left = parseMultiplicative()
        while (true) {
            val t = peek()
            if (t is Tok.Op && (t.s == "+" || t.s == "-")) { next(); left = NBin(t.s, left, parseMultiplicative()) } else break
        }
        return left
    }

    private fun parseMultiplicative(): Node {
        var left = parseUnary()
        while (true) {
            val t = peek()
            if (t is Tok.Op && (t.s == "*" || t.s == "/")) { next(); left = NBin(t.s, left, parseUnary()) } else break
        }
        return left
    }

    private fun parseUnary(): Node {
        val t = peek()
        if (t is Tok.Op && (t.s == "-" || t.s == "+")) { next(); return NUnary(t.s[0], parseUnary()) }
        return parsePower()
    }

    private fun parsePower(): Node {
        val left = parsePostfix()
        val t = peek()
        return if (t is Tok.Op && t.s == "^") { next(); NBin("^", left, parseUnary()) } else left
    }

    private fun parsePostfix(): Node {
        var e = parsePrimary()
        while (peek() is Tok.Percent) { next(); e = NPercent(e) }
        return e
    }

    private fun parsePrimary(): Node {
        val t = next() ?: return NNum(0.0)
        return when (t) {
            is Tok.Num -> NNum(t.v)
            is Tok.Str -> NStr(t.v)
            is Tok.Ref -> {
                if (peek() is Tok.Colon) {
                    next()
                    val t2 = next()
                    if (t2 is Tok.Ref) NRange(t.row, t.col, t2.row, t2.col) else NRef(t.row, t.col)
                } else NRef(t.row, t.col)
            }
            is Tok.LParen -> {
                val e = parseComparison()
                if (peek() is Tok.RParen) next()
                e
            }
            is Tok.Ident -> {
                val name = t.name.uppercase()
                if (peek() is Tok.LParen) {
                    next()
                    val args = ArrayList<Node>()
                    if (peek() !is Tok.RParen) {
                        args.add(parseComparison())
                        while (peek() is Tok.Comma) { next(); args.add(parseComparison()) }
                    }
                    if (peek() is Tok.RParen) next()
                    NCall(name, args)
                } else {
                    when (name) {
                        "TRUE" -> NCall("TRUE", emptyList())
                        "FALSE" -> NCall("FALSE", emptyList())
                        else -> NCall("__NAME_ERROR__", emptyList())
                    }
                }
            }
            else -> NNum(0.0)
        }
    }
}

// -------------------- Evaluator --------------------
private class Evaluator(private val resolve: (Int, Int) -> FV) {
    fun eval(node: Node): FV = when (node) {
        is NNum -> FV.Num(node.v)
        is NStr -> FV.Str(node.v)
        is NRef -> resolve(node.row, node.col)
        // A range used where a scalar is expected: Excel takes the top-left cell.
        is NRange -> resolve(min(node.r1, node.r2), min(node.c1, node.c2))
        is NUnary -> {
            val v = eval(node.e)
            if (v.isError) v else FV.Num(if (node.op == '-') -v.asDouble() else v.asDouble())
        }
        is NPercent -> {
            val v = eval(node.e)
            if (v.isError) v else FV.Num(v.asDouble() / 100.0)
        }
        is NBin -> evalBin(node.op, node.l, node.r)
        is NCall -> evalCall(node.name, node.args)
    }

    private fun evalBin(op: String, ln: Node, rn: Node): FV {
        val l = eval(ln); if (l.isError) return l
        val r = eval(rn); if (r.isError) return r
        return when (op) {
            "+" -> numOrErr(l.asDouble() + r.asDouble())
            "-" -> numOrErr(l.asDouble() - r.asDouble())
            "*" -> numOrErr(l.asDouble() * r.asDouble())
            "/" -> if (r.asDouble() == 0.0) FV.Err("#DIV/0!") else numOrErr(l.asDouble() / r.asDouble())
            "^" -> numOrErr(l.asDouble().pow(r.asDouble()))
            "&" -> FV.Str(l.asString() + r.asString())
            "=" -> FV.Bool(compareLoose(l, r) == 0)
            "<>" -> FV.Bool(compareLoose(l, r) != 0)
            "<" -> FV.Bool(compareLoose(l, r) < 0)
            ">" -> FV.Bool(compareLoose(l, r) > 0)
            "<=" -> FV.Bool(compareLoose(l, r) <= 0)
            ">=" -> FV.Bool(compareLoose(l, r) >= 0)
            else -> FV.Err("#ERROR!")
        }
    }

    private fun numOrErr(d: Double): FV = if (d.isNaN()) FV.Err("#VALUE!") else FV.Num(d)

    private fun compareLoose(l: FV, r: FV): Int {
        if (l is FV.Str || r is FV.Str) return l.asString().compareTo(r.asString(), ignoreCase = true)
        return l.asDouble().compareTo(r.asDouble())
    }

    // A single cell becomes a 1-element list; a range becomes every cell in it, row-major.
    // Used by aggregate functions (SUM/AVERAGE/COUNT/etc) so SUM(A1:A3) sees 3 values.
    fun flatten(node: Node): List<FV> = when (node) {
        is NRange -> {
            val r1 = min(node.r1, node.r2); val r2 = max(node.r1, node.r2)
            val c1 = min(node.c1, node.c2); val c2 = max(node.c1, node.c2)
            val out = ArrayList<FV>()
            for (r in r1..r2) for (c in c1..c2) out.add(resolve(r, c))
            out
        }
        else -> listOf(eval(node))
    }

    fun cellAt(r: Int, c: Int): FV = resolve(r, c)

    private fun evalCall(name: String, args: List<Node>): FV {
        if (name == "__NAME_ERROR__") return FV.Err("#NAME?")
        val fn = FunctionLibrary.functions[name] ?: return FV.Err("#NAME?")
        return try { fn(FunctionContext(this, args)) } catch (e: Exception) { FV.Err("#VALUE!") }
    }
}

// Passed to every function implementation. Deliberately gives access to raw arg NODES (not just
// pre-evaluated values) so functions like IF only evaluate the branch they actually take.
private class FunctionContext(private val ev: Evaluator, val args: List<Node>) {
    val size get() = args.size
    fun arg(i: Int): FV = if (i < args.size) ev.eval(args[i]) else FV.Err("#N/A")
    fun flat(i: Int): List<FV> = if (i < args.size) ev.flatten(args[i]) else emptyList()
    fun allFlat(): List<FV> = args.flatMap { ev.flatten(it) }
    fun cell(r: Int, c: Int): FV = ev.cellAt(r, c)

    // If arg i is a cell/range reference, returns [r1,c1,r2,c2] (normalized so r1<=r2, c1<=c2).
    fun rangeOf(i: Int): IntArray? {
        val node = args.getOrNull(i) ?: return null
        return when (node) {
            is NRange -> intArrayOf(min(node.r1, node.r2), min(node.c1, node.c2), max(node.r1, node.r2), max(node.c1, node.c2))
            is NRef -> intArrayOf(node.row, node.col, node.row, node.col)
            else -> null
        }
    }
}

// ============================================================================================
// FUNCTION LIBRARY
// ============================================================================================
private object FunctionLibrary {

    private fun numsOf(values: List<FV>): List<Double> = values.filterIsInstance<FV.Num>().map { it.v }

    private fun firstError(values: List<FV>): FV? = values.firstOrNull { it.isError }

    private fun matchesCriteria(v: FV, criteria: FV): Boolean {
        val critStr = criteria.asString().trim()
        val ops = listOf(">=", "<=", "<>", ">", "<", "=")
        for (op in ops) {
            if (critStr.startsWith(op)) {
                val rhs = critStr.substring(op.length).trim()
                val rhsNum = rhs.toDoubleOrNull()
                return if (rhsNum != null) {
                    val vNum = v.asDouble()
                    when (op) {
                        ">=" -> vNum >= rhsNum; "<=" -> vNum <= rhsNum; "<>" -> vNum != rhsNum
                        ">" -> vNum > rhsNum; "<" -> vNum < rhsNum; else -> vNum == rhsNum
                    }
                } else {
                    val vStr = v.asString()
                    if (op == "<>") !vStr.equals(rhs, true) else vStr.equals(rhs, true)
                }
            }
        }
        val criteriaNum = critStr.toDoubleOrNull()
        return if (criteriaNum != null) v.asDouble() == criteriaNum else v.asString().equals(critStr, ignoreCase = true)
    }

    private fun parseDateParts(fv: FV): IntArray? {
        val s = fv.asString()
        val m = Regex("""(\d{4})-(\d{2})-(\d{2})""").find(s) ?: return null
        val y = m.groupValues[1].toIntOrNull() ?: return null
        val mo = m.groupValues[2].toIntOrNull() ?: return null
        val d = m.groupValues[3].toIntOrNull() ?: return null
        return intArrayOf(y, mo, d)
    }

    private fun toEpochDay(parts: IntArray): Long {
        val cal = java.util.GregorianCalendar(parts[0], parts[1] - 1, parts[2])
        return cal.timeInMillis / 86400000L
    }

    private fun fromEpochDay(days: Long): IntArray {
        val cal = java.util.GregorianCalendar()
        cal.timeInMillis = days * 86400000L
        return intArrayOf(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH))
    }

    private fun dateStr(y: Int, m: Int, d: Int): String = String.format("%04d-%02d-%02d", y, m, d)

    val functions: Map<String, (FunctionContext) -> FV> = buildMap {

        // ---------------- MATH ----------------
        put("SUM") { ctx -> val v = ctx.allFlat(); firstError(v) ?: FV.Num(numsOf(v).sum()) }
        put("PRODUCT") { ctx -> val v = ctx.allFlat(); firstError(v) ?: FV.Num(numsOf(v).fold(1.0) { a, b -> a * b }) }
        put("AVERAGE") { ctx ->
            val v = ctx.allFlat(); val err = firstError(v); if (err != null) err
            else { val nums = numsOf(v); if (nums.isEmpty()) FV.Err("#DIV/0!") else FV.Num(nums.average()) }
        }
        put("ABS") { ctx -> FV.Num(abs(ctx.arg(0).asDouble())) }
        put("ROUND") { ctx ->
            val x = ctx.arg(0).asDouble(); val d = if (ctx.size > 1) ctx.arg(1).asDouble() else 0.0
            val f = 10.0.pow(d); FV.Num(round(x * f) / f)
        }
        put("ROUNDUP") { ctx ->
            val x = ctx.arg(0).asDouble(); val d = if (ctx.size > 1) ctx.arg(1).asDouble() else 0.0
            val f = 10.0.pow(d); FV.Num((if (x >= 0) ceil(x * f) else floor(x * f)) / f)
        }
        put("ROUNDDOWN") { ctx ->
            val x = ctx.arg(0).asDouble(); val d = if (ctx.size > 1) ctx.arg(1).asDouble() else 0.0
            val f = 10.0.pow(d); FV.Num((if (x >= 0) floor(x * f) else ceil(x * f)) / f)
        }
        put("INT") { ctx -> FV.Num(floor(ctx.arg(0).asDouble())) }
        put("TRUNC") { ctx ->
            val x = ctx.arg(0).asDouble(); val d = if (ctx.size > 1) ctx.arg(1).asDouble() else 0.0
            val f = 10.0.pow(d); FV.Num((if (x >= 0) floor(x * f) else ceil(x * f)) / f)
        }
        put("MOD") { ctx ->
            val x = ctx.arg(0).asDouble(); val y = ctx.arg(1).asDouble()
            if (y == 0.0) FV.Err("#DIV/0!") else FV.Num(x - y * floor(x / y))
        }
        put("POWER") { ctx -> FV.Num(ctx.arg(0).asDouble().pow(ctx.arg(1).asDouble())) }
        put("SQRT") { ctx -> val x = ctx.arg(0).asDouble(); if (x < 0) FV.Err("#NUM!") else FV.Num(sqrt(x)) }
        put("EXP") { ctx -> FV.Num(exp(ctx.arg(0).asDouble())) }
        put("LN") { ctx -> val x = ctx.arg(0).asDouble(); if (x <= 0) FV.Err("#NUM!") else FV.Num(ln(x)) }
        put("LOG10") { ctx -> val x = ctx.arg(0).asDouble(); if (x <= 0) FV.Err("#NUM!") else FV.Num(log10(x)) }
        put("LOG") { ctx ->
            val x = ctx.arg(0).asDouble(); val base = if (ctx.size > 1) ctx.arg(1).asDouble() else 10.0
            if (x <= 0 || base <= 0 || base == 1.0) FV.Err("#NUM!") else FV.Num(ln(x) / ln(base))
        }
        put("PI") { _ -> FV.Num(PI) }
        put("CEILING") { ctx ->
            val x = ctx.arg(0).asDouble(); val sig = if (ctx.size > 1) ctx.arg(1).asDouble() else 1.0
            if (sig == 0.0) FV.Num(0.0) else FV.Num(ceil(x / sig) * sig)
        }
        put("FLOOR") { ctx ->
            val x = ctx.arg(0).asDouble(); val sig = if (ctx.size > 1) ctx.arg(1).asDouble() else 1.0
            if (sig == 0.0) FV.Num(0.0) else FV.Num(floor(x / sig) * sig)
        }
        put("SIGN") { ctx -> FV.Num(sign(ctx.arg(0).asDouble())) }
        put("RAND") { _ -> FV.Num(Math.random()) }
        put("RANDBETWEEN") { ctx ->
            val lo = ctx.arg(0).asDouble().toInt(); val hi = ctx.arg(1).asDouble().toInt()
            if (hi < lo) FV.Err("#NUM!") else FV.Num((lo + (Math.random() * (hi - lo + 1)).toInt()).toDouble())
        }
        put("GCD") { ctx ->
            val ns = numsOf(ctx.allFlat()).map { it.toLong() }
            fun gcd(a: Long, b: Long): Long = if (b == 0L) abs(a) else gcd(b, a % b)
            FV.Num((ns.fold(0L) { a, b -> gcd(a, b) }).toDouble())
        }
        put("LCM") { ctx ->
            val ns = numsOf(ctx.allFlat()).map { it.toLong() }
            fun gcd(a: Long, b: Long): Long = if (b == 0L) abs(a) else gcd(b, a % b)
            fun lcm(a: Long, b: Long): Long = if (a == 0L || b == 0L) 0L else abs(a * b) / gcd(a, b)
            FV.Num((ns.fold(1L) { a, b -> lcm(a, b) }).toDouble())
        }
        put("COMBIN") { ctx ->
            val n = ctx.arg(0).asDouble().toInt(); val k = ctx.arg(1).asDouble().toInt()
            if (k < 0 || n < 0 || k > n) FV.Err("#NUM!")
            else {
                var result = 1.0
                for (i in 0 until k) result = result * (n - i) / (i + 1)
                FV.Num(round(result))
            }
        }
        put("FACT") { ctx ->
            val n = ctx.arg(0).asDouble().toInt()
            if (n < 0) FV.Err("#NUM!") else { var r = 1.0; for (i in 2..n) r *= i; FV.Num(r) }
        }

        // ---------------- COUNT ----------------
        put("COUNT") { ctx -> FV.Num(numsOf(ctx.allFlat()).size.toDouble()) }
        put("COUNTA") { ctx -> FV.Num(ctx.allFlat().count { !it.isBlank }.toDouble()) }
        put("COUNTBLANK") { ctx -> FV.Num(ctx.allFlat().count { it.isBlank }.toDouble()) }
        put("COUNTIF") { ctx ->
            val range = ctx.flat(0); val crit = ctx.arg(1)
            FV.Num(range.count { matchesCriteria(it, crit) }.toDouble())
        }
        put("COUNTIFS") { ctx ->
            var count = 0
            val pairCount = ctx.size / 2
            if (pairCount > 0) {
                val ranges = (0 until pairCount).map { ctx.flat(it * 2) }
                val crits = (0 until pairCount).map { ctx.arg(it * 2 + 1) }
                val n = ranges.getOrNull(0)?.size ?: 0
                for (i in 0 until n) {
                    var ok = true
                    for (p in 0 until pairCount) { if (i >= ranges[p].size || !matchesCriteria(ranges[p][i], crits[p])) { ok = false; break } }
                    if (ok) count++
                }
            }
            FV.Num(count.toDouble())
        }

        // ---------------- LOGICAL ----------------
        put("IF") { ctx ->
            val cond = ctx.arg(0)
            if (cond.isError) cond
            else if (cond.asBool()) { if (ctx.size > 1) ctx.arg(1) else FV.Bool(true) }
            else { if (ctx.size > 2) ctx.arg(2) else FV.Bool(false) }
        }
        put("IFS") { ctx ->
            var result: FV = FV.Err("#N/A")
            var i = 0
            while (i + 1 < ctx.size) { if (ctx.arg(i).asBool()) { result = ctx.arg(i + 1); break }; i += 2 }
            result
        }
        put("AND") { ctx -> val v = ctx.allFlat(); firstError(v) ?: FV.Bool(v.isNotEmpty() && v.all { it.asBool() }) }
        put("OR") { ctx -> val v = ctx.allFlat(); firstError(v) ?: FV.Bool(v.any { it.asBool() }) }
        put("NOT") { ctx -> FV.Bool(!ctx.arg(0).asBool()) }
        put("XOR") { ctx -> val v = ctx.allFlat(); FV.Bool(v.count { it.asBool() } % 2 == 1) }
        put("TRUE") { _ -> FV.Bool(true) }
        put("FALSE") { _ -> FV.Bool(false) }
        put("IFERROR") { ctx -> val v = ctx.arg(0); if (v.isError) { if (ctx.size > 1) ctx.arg(1) else FV.Str("") } else v }
        put("IFNA") { ctx -> val v = ctx.arg(0); if (v is FV.Err && v.code == "#N/A") { if (ctx.size > 1) ctx.arg(1) else FV.Str("") } else v }
        put("SWITCH") { ctx ->
            val expr = ctx.arg(0)
            var result: FV = if (ctx.size % 2 == 0) FV.Err("#N/A") else ctx.arg(ctx.size - 1)
            var i = 1
            while (i + 1 < ctx.size) { if (matchesCriteria(expr, ctx.arg(i))) { result = ctx.arg(i + 1); break }; i += 2 }
            result
        }

        // ---------------- TEXT ----------------
        put("CONCATENATE") { ctx -> FV.Str(ctx.args.indices.joinToString("") { ctx.arg(it).asString() }) }
        put("CONCAT") { ctx -> FV.Str(ctx.allFlat().joinToString("") { it.asString() }) }
        put("LEFT") { ctx -> val s = ctx.arg(0).asString(); val n = (if (ctx.size > 1) ctx.arg(1).asDouble().toInt() else 1).coerceIn(0, s.length); FV.Str(s.substring(0, n)) }
        put("RIGHT") { ctx -> val s = ctx.arg(0).asString(); val n = (if (ctx.size > 1) ctx.arg(1).asDouble().toInt() else 1).coerceIn(0, s.length); FV.Str(s.substring(s.length - n)) }
        put("MID") { ctx ->
            val s = ctx.arg(0).asString(); val start = (ctx.arg(1).asDouble().toInt() - 1).coerceAtLeast(0)
            val len = ctx.arg(2).asDouble().toInt().coerceAtLeast(0)
            if (start >= s.length) FV.Str("") else FV.Str(s.substring(start, min(s.length, start + len)))
        }
        put("LEN") { ctx -> FV.Num(ctx.arg(0).asString().length.toDouble()) }
        put("UPPER") { ctx -> FV.Str(ctx.arg(0).asString().uppercase()) }
        put("LOWER") { ctx -> FV.Str(ctx.arg(0).asString().lowercase()) }
        put("PROPER") { ctx -> FV.Str(ctx.arg(0).asString().split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }) }
        put("TRIM") { ctx -> FV.Str(ctx.arg(0).asString().trim().replace(Regex(" +"), " ")) }
        put("SUBSTITUTE") { ctx ->
            val s = ctx.arg(0).asString(); val old = ctx.arg(1).asString(); val new = ctx.arg(2).asString()
            if (ctx.size > 3) {
                val instance = ctx.arg(3).asDouble().toInt(); var count = 0; val idx = ArrayList<Int>()
                var searchFrom = 0
                while (true) { val f = s.indexOf(old, searchFrom); if (f < 0) break; idx.add(f); searchFrom = f + old.length }
                if (instance - 1 in idx.indices) {
                    val pos = idx[instance - 1]
                    FV.Str(s.substring(0, pos) + new + s.substring(pos + old.length))
                } else FV.Str(s)
            } else FV.Str(s.replace(old, new))
        }
        put("REPLACE") { ctx ->
            val s = ctx.arg(0).asString(); val start = (ctx.arg(1).asDouble().toInt() - 1).coerceIn(0, s.length)
            val len = ctx.arg(2).asDouble().toInt().coerceAtLeast(0); val newText = ctx.arg(3).asString()
            val end = min(s.length, start + len)
            FV.Str(s.substring(0, start) + newText + s.substring(end))
        }
        put("FIND") { ctx ->
            val find = ctx.arg(0).asString(); val within = ctx.arg(1).asString()
            val start = (if (ctx.size > 2) ctx.arg(2).asDouble().toInt() - 1 else 0).coerceAtLeast(0)
            val idx = within.indexOf(find, start)
            if (idx < 0) FV.Err("#VALUE!") else FV.Num((idx + 1).toDouble())
        }
        put("SEARCH") { ctx ->
            val find = ctx.arg(0).asString(); val within = ctx.arg(1).asString()
            val start = (if (ctx.size > 2) ctx.arg(2).asDouble().toInt() - 1 else 0).coerceAtLeast(0)
            val idx = within.indexOf(find, start, ignoreCase = true)
            if (idx < 0) FV.Err("#VALUE!") else FV.Num((idx + 1).toDouble())
        }
        put("TEXT") { ctx ->
            val v = ctx.arg(0); val fmt = if (ctx.size > 1) ctx.arg(1).asString() else ""
            if (v !is FV.Num) FV.Str(v.asString())
            else {
                val decimals = fmt.substringAfter('.', "").count { it == '0' }
                if (decimals > 0) FV.Str(String.format("%.${decimals}f", v.v)) else FV.Str(FormulaEngine.formatNumber(v.v))
            }
        }
        put("VALUE") { ctx -> val d = ctx.arg(0).asString().trim().toDoubleOrNull(); if (d == null) FV.Err("#VALUE!") else FV.Num(d) }
        put("REPT") { ctx -> val s = ctx.arg(0).asString(); val n = ctx.arg(1).asDouble().toInt().coerceAtLeast(0); FV.Str(s.repeat(n)) }
        put("CHAR") { ctx -> FV.Str(ctx.arg(0).asDouble().toInt().toChar().toString()) }
        put("CODE") { ctx -> val s = ctx.arg(0).asString(); if (s.isEmpty()) FV.Err("#VALUE!") else FV.Num(s[0].code.toDouble()) }
        put("EXACT") { ctx -> FV.Bool(ctx.arg(0).asString() == ctx.arg(1).asString()) }
        put("TEXTJOIN") { ctx ->
            val delim = ctx.arg(0).asString(); val ignoreEmpty = ctx.arg(1).asBool()
            val parts = (2 until ctx.size).flatMap { ctx.flat(it) }.map { it.asString() }.filter { !ignoreEmpty || it.isNotEmpty() }
            FV.Str(parts.joinToString(delim))
        }

        // ---------------- DATE ----------------
        put("TODAY") { _ -> val c = java.util.Calendar.getInstance(); FV.Str(dateStr(c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH))) }
        put("NOW") { _ ->
            val c = java.util.Calendar.getInstance()
            FV.Str(dateStr(c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH)) +
                " " + String.format("%02d:%02d", c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE)))
        }
        put("DATE") { ctx ->
            val y = ctx.arg(0).asDouble().toInt(); val m = ctx.arg(1).asDouble().toInt(); val d = ctx.arg(2).asDouble().toInt()
            try { val days = toEpochDay(intArrayOf(y, m, d)); val p = fromEpochDay(days); FV.Str(dateStr(p[0], p[1], p[2])) } catch (e: Exception) { FV.Err("#NUM!") }
        }
        put("YEAR") { ctx -> val p = parseDateParts(ctx.arg(0)); if (p == null) FV.Err("#VALUE!") else FV.Num(p[0].toDouble()) }
        put("MONTH") { ctx -> val p = parseDateParts(ctx.arg(0)); if (p == null) FV.Err("#VALUE!") else FV.Num(p[1].toDouble()) }
        put("DAY") { ctx -> val p = parseDateParts(ctx.arg(0)); if (p == null) FV.Err("#VALUE!") else FV.Num(p[2].toDouble()) }
        put("WEEKDAY") { ctx ->
            val p = parseDateParts(ctx.arg(0))
            if (p == null) FV.Err("#VALUE!") else {
                val cal = java.util.GregorianCalendar(p[0], p[1] - 1, p[2])
                FV.Num(cal.get(java.util.Calendar.DAY_OF_WEEK).toDouble())
            }
        }
        put("DAYS") { ctx ->
            val pEnd = parseDateParts(ctx.arg(0)); val pStart = parseDateParts(ctx.arg(1))
            if (pEnd == null || pStart == null) FV.Err("#VALUE!") else FV.Num((toEpochDay(pEnd) - toEpochDay(pStart)).toDouble())
        }
        put("EDATE") { ctx ->
            val p = parseDateParts(ctx.arg(0)); val months = ctx.arg(1).asDouble().toInt()
            if (p == null) FV.Err("#VALUE!") else {
                val cal = java.util.GregorianCalendar(p[0], p[1] - 1, p[2]); cal.add(java.util.Calendar.MONTH, months)
                FV.Str(dateStr(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH)))
            }
        }
        put("EOMONTH") { ctx ->
            val p = parseDateParts(ctx.arg(0)); val months = ctx.arg(1).asDouble().toInt()
            if (p == null) FV.Err("#VALUE!") else {
                val cal = java.util.GregorianCalendar(p[0], p[1] - 1, p[2]); cal.add(java.util.Calendar.MONTH, months)
                cal.set(java.util.Calendar.DAY_OF_MONTH, cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH))
                FV.Str(dateStr(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH)))
            }
        }
        put("DATEDIF") { ctx ->
            val pStart = parseDateParts(ctx.arg(0)); val pEnd = parseDateParts(ctx.arg(1))
            val unit = if (ctx.size > 2) ctx.arg(2).asString().uppercase() else "D"
            if (pStart == null || pEnd == null) FV.Err("#VALUE!") else {
                val days = toEpochDay(pEnd) - toEpochDay(pStart)
                when (unit) {
                    "D" -> FV.Num(days.toDouble())
                    "M" -> FV.Num(((pEnd[0] - pStart[0]) * 12 + (pEnd[1] - pStart[1])).toDouble())
                    "Y" -> FV.Num((pEnd[0] - pStart[0]).toDouble())
                    else -> FV.Num(days.toDouble())
                }
            }
        }

        // ---------------- LOOKUP / REFERENCE ----------------
        put("ROW") { ctx -> val rb = ctx.rangeOf(0); if (rb == null) FV.Err("#REF!") else FV.Num((rb[0] + 1).toDouble()) }
        put("COLUMN") { ctx -> val rb = ctx.rangeOf(0); if (rb == null) FV.Err("#REF!") else FV.Num((rb[1] + 1).toDouble()) }
        put("ROWS") { ctx -> val rb = ctx.rangeOf(0); if (rb == null) FV.Err("#REF!") else FV.Num((rb[2] - rb[0] + 1).toDouble()) }
        put("COLUMNS") { ctx -> val rb = ctx.rangeOf(0); if (rb == null) FV.Err("#REF!") else FV.Num((rb[3] - rb[1] + 1).toDouble()) }
        put("CHOOSE") { ctx ->
            val idx = ctx.arg(0).asDouble().toInt()
            if (idx < 1 || idx >= ctx.size) FV.Err("#VALUE!") else ctx.arg(idx)
        }
        put("INDEX") { ctx ->
            val rb = ctx.rangeOf(0)
            if (rb == null) FV.Err("#REF!") else {
                val height = rb[2] - rb[0] + 1; val width = rb[3] - rb[1] + 1
                val r: Int; val c: Int
                if (ctx.size <= 2 && (height == 1 || width == 1)) {
                    // Single row or column with one index: that index walks along whichever
                    // dimension actually varies (INDEX(A1:C1,2) means "2nd column", not "row 2").
                    val idx = ctx.arg(1).asDouble().toInt()
                    if (height == 1) { r = rb[0]; c = rb[1] + idx - 1 } else { r = rb[0] + idx - 1; c = rb[1] }
                } else {
                    val rowOff = if (ctx.size > 1) ctx.arg(1).asDouble().toInt() else 1
                    val colOff = if (ctx.size > 2) ctx.arg(2).asDouble().toInt() else 1
                    r = rb[0] + rowOff - 1; c = rb[1] + colOff - 1
                }
                if (r < rb[0] || r > rb[2] || c < rb[1] || c > rb[3]) FV.Err("#REF!") else ctx.cell(r, c)
            }
        }
        put("MATCH") { ctx ->
            val target = ctx.arg(0); val range = ctx.flat(1)
            val matchType = if (ctx.size > 2) ctx.arg(2).asDouble().toInt() else 1
            var found = -1
            if (matchType == 0) {
                for (i in range.indices) { if (matchesEqual(range[i], target)) { found = i; break } }
            } else if (matchType == 1) {
                for (i in range.indices) { if (range[i].asDouble() <= target.asDouble()) found = i else if (found >= 0) break }
            } else {
                for (i in range.indices) { if (range[i].asDouble() >= target.asDouble()) found = i else if (found >= 0) break }
            }
            if (found < 0) FV.Err("#N/A") else FV.Num((found + 1).toDouble())
        }
        put("VLOOKUP") { ctx ->
            val lookupVal = ctx.arg(0); val rb = ctx.rangeOf(1)
            val colIndex = ctx.arg(2).asDouble().toInt()
            val exact = ctx.size <= 3 || !ctx.arg(3).asBool()
            if (rb == null) FV.Err("#REF!") else {
                var bestRow = -1
                for (r in rb[0]..rb[2]) {
                    val v = ctx.cell(r, rb[1])
                    if (exact) { if (matchesEqual(v, lookupVal)) { bestRow = r; break } }
                    else { if (v.asDouble() <= lookupVal.asDouble()) bestRow = r else if (bestRow >= 0) break }
                }
                val targetCol = rb[1] + (colIndex - 1)
                if (bestRow < 0) FV.Err("#N/A") else if (colIndex < 1 || targetCol > rb[3]) FV.Err("#REF!") else ctx.cell(bestRow, targetCol)
            }
        }
        put("HLOOKUP") { ctx ->
            val lookupVal = ctx.arg(0); val rb = ctx.rangeOf(1)
            val rowIndex = ctx.arg(2).asDouble().toInt()
            val exact = ctx.size <= 3 || !ctx.arg(3).asBool()
            if (rb == null) FV.Err("#REF!") else {
                var bestCol = -1
                for (c in rb[1]..rb[3]) {
                    val v = ctx.cell(rb[0], c)
                    if (exact) { if (matchesEqual(v, lookupVal)) { bestCol = c; break } }
                    else { if (v.asDouble() <= lookupVal.asDouble()) bestCol = c else if (bestCol >= 0) break }
                }
                val targetRow = rb[0] + (rowIndex - 1)
                if (bestCol < 0) FV.Err("#N/A") else if (rowIndex < 1 || targetRow > rb[2]) FV.Err("#REF!") else ctx.cell(targetRow, bestCol)
            }
        }

        // ---------------- STATISTICAL ----------------
        put("MAX") { ctx -> val v = ctx.allFlat(); firstError(v) ?: FV.Num(numsOf(v).maxOrNull() ?: 0.0) }
        put("MIN") { ctx -> val v = ctx.allFlat(); firstError(v) ?: FV.Num(numsOf(v).minOrNull() ?: 0.0) }
        put("MEDIAN") { ctx ->
            val nums = numsOf(ctx.allFlat()).sorted()
            if (nums.isEmpty()) FV.Err("#NUM!")
            else if (nums.size % 2 == 1) FV.Num(nums[nums.size / 2]) else FV.Num((nums[nums.size / 2 - 1] + nums[nums.size / 2]) / 2.0)
        }
        put("MODE") { ctx ->
            val nums = numsOf(ctx.allFlat())
            if (nums.isEmpty()) FV.Err("#N/A") else FV.Num(nums.groupingBy { it }.eachCount().maxByOrNull { it.value }!!.key)
        }
        put("STDEV") { ctx ->
            val nums = numsOf(ctx.allFlat())
            if (nums.size < 2) FV.Err("#DIV/0!") else { val m = nums.average(); FV.Num(sqrt(nums.sumOf { (it - m).pow(2) } / (nums.size - 1))) }
        }
        put("STDEVP") { ctx ->
            val nums = numsOf(ctx.allFlat())
            if (nums.isEmpty()) FV.Err("#DIV/0!") else { val m = nums.average(); FV.Num(sqrt(nums.sumOf { (it - m).pow(2) } / nums.size)) }
        }
        put("VAR") { ctx ->
            val nums = numsOf(ctx.allFlat())
            if (nums.size < 2) FV.Err("#DIV/0!") else { val m = nums.average(); FV.Num(nums.sumOf { (it - m).pow(2) } / (nums.size - 1)) }
        }
        put("VARP") { ctx ->
            val nums = numsOf(ctx.allFlat())
            if (nums.isEmpty()) FV.Err("#DIV/0!") else { val m = nums.average(); FV.Num(nums.sumOf { (it - m).pow(2) } / nums.size) }
        }
        put("LARGE") { ctx ->
            val nums = numsOf(ctx.flat(0)).sortedDescending(); val k = ctx.arg(1).asDouble().toInt()
            if (k < 1 || k > nums.size) FV.Err("#NUM!") else FV.Num(nums[k - 1])
        }
        put("SMALL") { ctx ->
            val nums = numsOf(ctx.flat(0)).sorted(); val k = ctx.arg(1).asDouble().toInt()
            if (k < 1 || k > nums.size) FV.Err("#NUM!") else FV.Num(nums[k - 1])
        }
        put("RANK") { ctx ->
            val target = ctx.arg(0).asDouble(); val nums = numsOf(ctx.flat(1))
            val descending = ctx.size <= 2 || ctx.arg(2).asDouble() == 0.0
            val sorted = if (descending) nums.sortedDescending() else nums.sorted()
            val idx = sorted.indexOfFirst { it == target }
            if (idx < 0) FV.Err("#N/A") else FV.Num((idx + 1).toDouble())
        }
        put("PERCENTILE") { ctx ->
            val nums = numsOf(ctx.flat(0)).sorted(); val k = ctx.arg(1).asDouble()
            if (nums.isEmpty() || k < 0.0 || k > 1.0) FV.Err("#NUM!")
            else {
                val idx = k * (nums.size - 1); val lo = floor(idx).toInt(); val hi = ceil(idx).toInt()
                FV.Num(if (lo == hi) nums[lo] else nums[lo] + (idx - lo) * (nums[hi] - nums[lo]))
            }
        }
        put("QUARTILE") { ctx ->
            val nums = numsOf(ctx.flat(0)).sorted(); val q = ctx.arg(1).asDouble().toInt()
            if (nums.isEmpty() || q < 0 || q > 4) FV.Err("#NUM!")
            else {
                val k = q / 4.0; val idx = k * (nums.size - 1); val lo = floor(idx).toInt(); val hi = ceil(idx).toInt()
                FV.Num(if (lo == hi) nums[lo] else nums[lo] + (idx - lo) * (nums[hi] - nums[lo]))
            }
        }
        put("SUMIF") { ctx ->
            val range = ctx.flat(0); val crit = ctx.arg(1); val sumRange = if (ctx.size > 2) ctx.flat(2) else range
            var total = 0.0
            for (i in range.indices) { if (matchesCriteria(range[i], crit) && i < sumRange.size) { val d = sumRange[i].asDouble(); if (!d.isNaN()) total += d } }
            FV.Num(total)
        }
        put("SUMIFS") { ctx ->
            val sumRange = ctx.flat(0)
            val pairCount = (ctx.size - 1) / 2
            var total = 0.0
            if (pairCount > 0) {
                val ranges = (0 until pairCount).map { ctx.flat(1 + it * 2) }
                val crits = (0 until pairCount).map { ctx.arg(2 + it * 2) }
                for (i in sumRange.indices) {
                    var ok = true
                    for (p in 0 until pairCount) { if (i >= ranges[p].size || !matchesCriteria(ranges[p][i], crits[p])) { ok = false; break } }
                    if (ok) { val d = sumRange[i].asDouble(); if (!d.isNaN()) total += d }
                }
            }
            FV.Num(total)
        }
        put("AVERAGEIF") { ctx ->
            val range = ctx.flat(0); val crit = ctx.arg(1); val avgRange = if (ctx.size > 2) ctx.flat(2) else range
            var total = 0.0; var count = 0
            for (i in range.indices) { if (matchesCriteria(range[i], crit) && i < avgRange.size) { val d = avgRange[i].asDouble(); if (!d.isNaN()) { total += d; count++ } } }
            if (count == 0) FV.Err("#DIV/0!") else FV.Num(total / count)
        }
        put("MAXIFS") { ctx ->
            val maxRange = ctx.flat(0); val pairCount = (ctx.size - 1) / 2
            val ranges = (0 until pairCount).map { ctx.flat(1 + it * 2) }
            val crits = (0 until pairCount).map { ctx.arg(2 + it * 2) }
            var best: Double? = null
            for (i in maxRange.indices) {
                var ok = true
                for (p in 0 until pairCount) { if (i >= ranges[p].size || !matchesCriteria(ranges[p][i], crits[p])) { ok = false; break } }
                if (ok) { val d = maxRange[i].asDouble(); if (!d.isNaN() && (best == null || d > best!!)) best = d }
            }
            FV.Num(best ?: 0.0)
        }
        put("MINIFS") { ctx ->
            val minRange = ctx.flat(0); val pairCount = (ctx.size - 1) / 2
            val ranges = (0 until pairCount).map { ctx.flat(1 + it * 2) }
            val crits = (0 until pairCount).map { ctx.arg(2 + it * 2) }
            var best: Double? = null
            for (i in minRange.indices) {
                var ok = true
                for (p in 0 until pairCount) { if (i >= ranges[p].size || !matchesCriteria(ranges[p][i], crits[p])) { ok = false; break } }
                if (ok) { val d = minRange[i].asDouble(); if (!d.isNaN() && (best == null || d < best!!)) best = d }
            }
            FV.Num(best ?: 0.0)
        }

        // ---------------- INFO ----------------
        put("ISBLANK") { ctx -> FV.Bool(ctx.arg(0).isBlank) }
        put("ISNUMBER") { ctx -> FV.Bool(ctx.arg(0) is FV.Num) }
        put("ISTEXT") { ctx -> FV.Bool(ctx.arg(0) is FV.Str && !ctx.arg(0).isBlank) }
        put("ISLOGICAL") { ctx -> FV.Bool(ctx.arg(0) is FV.Bool) }
        put("ISERROR") { ctx -> FV.Bool(ctx.arg(0).isError) }
        put("ISNA") { ctx -> val v = ctx.arg(0); FV.Bool(v is FV.Err && v.code == "#N/A") }
        put("ISEVEN") { ctx -> FV.Bool(ctx.arg(0).asDouble().toLong() % 2L == 0L) }
        put("ISODD") { ctx -> FV.Bool(ctx.arg(0).asDouble().toLong() % 2L != 0L) }
        put("N") { ctx -> FV.Num(ctx.arg(0).asDouble().let { if (it.isNaN()) 0.0 else it }) }
        put("TYPE") { ctx ->
            when (ctx.arg(0)) { is FV.Num -> FV.Num(1.0); is FV.Str -> FV.Num(2.0); is FV.Bool -> FV.Num(4.0); is FV.Err -> FV.Num(16.0) }
        }
    }

    private fun matchesEqual(a: FV, b: FV): Boolean = if (a is FV.Str || b is FV.Str) a.asString().equals(b.asString(), true) else a.asDouble() == b.asDouble()
}

// ============================================================================================
// PUBLIC SURFACE — this is the only object other files should touch.
// ============================================================================================
object FormulaEngine {

    // Number formatting matching how a spreadsheet displays results: whole numbers with no
    // decimal point, otherwise trimmed of trailing zeros so 2.50 shows as "2.5", not "2.500000".
    fun formatNumber(v: Double): String {
        if (v.isNaN()) return "#VALUE!"
        if (v.isInfinite()) return "#NUM!"
        if (v == v.toLong().toDouble() && abs(v) < 1e15) return v.toLong().toString()
        val s = String.format("%.10f", v).trimEnd('0').trimEnd('.')
        return s
    }

    private fun parseLiteral(raw: String): FV {
        if (raw.isEmpty()) return FV.Str("")
        val d = raw.toDoubleOrNull()
        return when {
            d != null -> FV.Num(d)
            raw.equals("TRUE", ignoreCase = true) -> FV.Bool(true)
            raw.equals("FALSE", ignoreCase = true) -> FV.Bool(false)
            else -> FV.Str(raw)
        }
    }

    private fun render(v: FV): String = when (v) {
        is FV.Num -> formatNumber(v.v)
        is FV.Str -> v.v
        is FV.Bool -> if (v.v) "TRUE" else "FALSE"
        is FV.Err -> v.code
    }

    private fun evalFormula(src: String, resolve: (Int, Int) -> FV): FV {
        return try {
            val ast = Parser(tokenize(src)).parse()
            Evaluator(resolve).eval(ast)
        } catch (e: Exception) {
            FV.Err("#ERROR!")
        }
    }

    // Live preview for the formula bar: evaluates against the table's CURRENT cached values
    // (doesn't trigger a full dependency-graph recalc — cheap enough to call on every keystroke).
    // Returns the raw text unchanged if it isn't a formula, so it's safe to call unconditionally.
    fun previewFormula(rawText: String, table: TableItem): String {
        if (!rawText.startsWith("=") || rawText.length <= 1) return rawText
        val result = evalFormula(rawText.substring(1)) { r, c ->
            if (r < 0 || c < 0 || r >= table.rows || c >= table.cols) FV.Err("#REF!")
            else {
                val cell = table.getCellPublic(r, c)
                if (cell.text.startsWith("=") && cell.text.length > 1) parseLiteral(cell.formulaCache) else parseLiteral(cell.text)
            }
        }
        return render(result)
    }

    // The actual dependency-aware recalculation, exposed as an extension so call sites read
    // naturally as `table.recalcAllFormulas()`. Walks every cell, evaluating formulas lazily and
    // memoizing results within this one pass; a cell referencing itself (directly or through a
    // chain of other formula cells) resolves to #CIRCULAR! instead of infinite-looping.
    fun recalc(table: TableItem) {
        val memo = HashMap<Long, FV>()
        val visiting = HashSet<Long>()
        fun key(r: Int, c: Int) = r.toLong() * 1_000_000L + c

        fun resolve(r: Int, c: Int): FV {
            if (r < 0 || c < 0 || r >= table.rows || c >= table.cols) return FV.Err("#REF!")
            val k = key(r, c)
            memo[k]?.let { return it }
            if (visiting.contains(k)) return FV.Err("#CIRCULAR!")
            visiting.add(k)
            val cell = table.getCellPublic(r, c)
            val raw = cell.text
            val result: FV = if (raw.startsWith("=") && raw.length > 1) {
                try { evalFormula(raw.substring(1)) { rr, cc -> resolve(rr, cc) } } catch (e: Exception) { FV.Err("#ERROR!") }
            } else parseLiteral(raw)
            visiting.remove(k)
            memo[k] = result
            cell.formulaCache = render(result)
            cell.formulaError = result.isError
            return result
        }

        for (r in 0 until table.rows) for (c in 0 until table.cols) resolve(r, c)
    }
}

// Extension so call sites read as `table.recalcAllFormulas()` rather than `FormulaEngine.recalc(table)`.
fun TableItem.recalcAllFormulas() = FormulaEngine.recalc(this)
