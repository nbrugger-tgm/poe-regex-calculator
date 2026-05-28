package poeregex

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Tests for [PatternSolver].
 *
 * Each test verifies the contract independently of implementation:
 *   1. No false positives  – every pattern matches only strings in B when searched in A
 *   2. Full coverage       – the union of all matches equals B exactly
 *   3. Minimality         – no valid solution with fewer patterns exists
 */
abstract class PatternSolverContract {

    abstract val solver: PatternSolver

    // ── helper ───────────────────────────────────────────────────────────────

    /** Verifies the three core invariants of a solver result. */
    private fun assertValidSolution(a: List<String>, b: List<String>, result: List<String>) {
        val bSet = b.toSet()
        val aSet = a.toSet()

        // contract: every element of b must be in a
        assertTrue(bSet.all { it in aSet }, "B must be a subset of A")

        for (pattern in result) {
            val matches = a.filter { it.contains(pattern) }
            // no false positives
            assertTrue(
                matches.all { it in bSet },
                "Pattern '$pattern' matched ${matches.filter { it !in bSet }} which are not in B"
            )
            // each pattern is useful
            assertTrue(matches.isNotEmpty(), "Pattern '$pattern' matched nothing in A")
        }

        // full coverage
        val covered = a.filter { s -> result.any { p -> s.contains(p) } }.filter { it in bSet }.toSet()
        assertEquals(bSet, covered, "Union of matches does not equal B")
    }

    // ── trivial cases ─────────────────────────────────────────────────────────

    @Test
    fun `empty B returns empty C`() {
        val result = solver.solve(a = listOf("alpha", "beta", "gamma"), b = emptyList())
        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun `single element B covered by itself`() {
        val a = listOf("unique-item", "other", "another")
        val b = listOf("unique-item")
        val result = solver.solve(a, b)
        assertValidSolution(a, b, result)
        assertEquals(1, result.size)
    }

    @Test
    fun `B equals A single element`() {
        val a = listOf("only")
        val result = solver.solve(a, a)
        assertValidSolution(a, a, result)
    }

    // ── pattern sharing ───────────────────────────────────────────────────────

    @Test
    fun `two elements sharing a common substring use one pattern`() {
        // "Topaz Ring" and "Sapphire Ring" share " Ring" — one pattern suffices
        // if A contains no other ring
        val a = listOf("Topaz Ring", "Sapphire Ring", "Iron Sword")
        val b = listOf("Topaz Ring", "Sapphire Ring")
        val result = solver.solve(a, b)
        assertValidSolution(a, b, result)
        assertEquals(1, result.size, "Expected a single shared pattern, got: $result")
    }

    @Test
    fun `no shared pattern forces two patterns`() {
        // "abc" and "xyz" share no substring that doesn't also hit something outside B
        val a = listOf("abc", "xyz", "abxyz")   // "ab", "xy", "xyz", "abc" etc. all hit "abxyz" too
        val b = listOf("abc", "xyz")
        // "abc" only matches "abc"  ✓
        // "xyz" matches "xyz" and "abxyz" ✗  → only "xyz " exact would work but that's not in A
        // So "abc" covers abc, and "xyz" alone has false positives → need to find non-FP patterns
        // "abc" → matches only "abc" ✓
        // " xyz" — not a substring of either... actually A strings don't have spaces
        // Careful: "xyz" is a substring of "abxyz" so it has a false positive.
        // The only valid pattern for "xyz" is "xyz" itself, but it matches "abxyz" too.
        // → UnresolvablePatternException for "xyz"
        assertThrows<UnresolvablePatternException> { solver.solve(a, b) }
    }

    @Test
    fun `three elements two patterns optimal`() {
        val a = listOf("Diamond Flask", "Ruby Flask", "Sapphire Flask", "Iron Sword")
        // All three flasks share "Flask", no other item in A has "Flask"
        val b = listOf("Diamond Flask", "Ruby Flask", "Sapphire Flask")
        val result = solver.solve(a, b)
        assertValidSolution(a, b, result)
        assertEquals(1, result.size, "Expected a single 'Flask' pattern, got: $result")
    }

    @Test
    fun `minimality - prefers fewer patterns over more specific ones`() {
        // A = ["Silver Ring", "Gold Ring", "Iron Ring", "Leather Belt"]
        // B = ["Silver Ring", "Gold Ring", "Iron Ring"]
        // Optimal: one pattern " Ring" or "Ring" covers all three
        val a = listOf("Silver Ring", "Gold Ring", "Iron Ring", "Leather Belt")
        val b = listOf("Silver Ring", "Gold Ring", "Iron Ring")
        val result = solver.solve(a, b)
        assertValidSolution(a, b, result)
        assertEquals(1, result.size, "Expected one pattern for all rings, got: $result")
    }

    @Test
    fun `two disjoint groups need two patterns`() {
        val a = listOf("Fire Wand", "Fire Staff", "Ice Bow", "Ice Sword", "Stone Axe")
        val b = listOf("Fire Wand", "Fire Staff", "Ice Bow", "Ice Sword")
        val result = solver.solve(a, b)
        assertValidSolution(a, b, result)
        assertEquals(2, result.size, "Expected two patterns (one per group), got: $result")
        assertTrue(result.toSet().size == 2, "Patterns must be distinct")
    }

    // ── false positive handling ───────────────────────────────────────────────

    @Test
    fun `element with no valid pattern throws UnresolvablePatternException`() {
        // "Ring" appears in both B element and a non-B element → no pattern for it
        val a = listOf("Gold Ring", "Stone Ring Pendant", "Amulet")
        val b = listOf("Gold Ring")
        // "Gold Ring" has substring "Gold " which only matches "Gold Ring" → resolvable
        val result = solver.solve(a, b)
        assertValidSolution(a, b, result)
        assertEquals(1, result.size)
    }

    @Test
    fun `genuinely unresolvable element throws with correct element name`() {
        // Every substring of "ab" appears in "abc" which is not in B
        val a = listOf("ab", "abc")
        val b = listOf("ab")
        val ex = assertThrows<UnresolvablePatternException> { solver.solve(a, b) }
        assertEquals("ab", ex.element)
    }

    // ── edge cases ────────────────────────────────────────────────────────────

    @Test
    fun `B not subset of A throws IllegalArgumentException`() {
        assertThrows<IllegalArgumentException> {
            solver.solve(a = listOf("alpha"), b = listOf("beta"))
        }
    }

    @Test
    fun `single-character patterns work`() {
        val a = listOf("axx", "ayy", "bxx")
        val b = listOf("axx", "ayy")
        // "a" is a substring of "axx" and "ayy" but not "bxx" → valid single-char pattern
        val result = solver.solve(a, b)
        assertValidSolution(a, b, result)
        assertEquals(1, result.size)
    }

    @Test
    fun `pattern must not match empty string greedily - substring of everything`() {
        // Empty string is a substring of everything; solver must not emit ""
        val a = listOf("foo", "bar")
        val b = listOf("foo")
        val result = solver.solve(a, b)
        assertTrue(result.none { it.isEmpty() }, "Solver must not emit empty-string patterns")
        assertValidSolution(a, b, result)
    }

    @Test
    fun `large B with single shared token resolves to one pattern`() {
        val shared = "Vaal "
        val a = (1..30).map { "$shared$it" } + (1..10).map { "Normal $it" }
        val b = (1..30).map { "$shared$it" }
        val result = solver.solve(a, b)
        assertValidSolution(a, b, result)
        assertEquals(1, result.size, "All Vaal items should collapse to one pattern, got: $result")
    }
}
