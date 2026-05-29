package poeregex

import poeregex.PatternSolver.Searchable

/**
 * Thrown when an element of [b] cannot be covered by any substring pattern without
 * also matching strings in [a] that are not in [b].
 */
class UnresolvablePatternException(val element: Searchable, val collisions: List<Searchable>) :
    Exception("No valid pattern exists for element '${element.searchString}': every substring also matches strings outside B (${collisions})")

/**
 * Finds the minimum list of substring patterns [C] such that:
 *  - For every pattern c in C: every string in A that contains c is also in B  (no false positives)
 *  - The union of all matches across C equals B exactly                         (full coverage)
 *  - Every pattern in C matches at least one element of B                       (no redundancy)
 *  - |C| is minimised                                                           (exact minimum)
 *
 * @throws UnresolvablePatternException if any element of [b] has no valid covering pattern.
 * @throws IllegalArgumentException if [b] contains elements not present in [a].
 */
interface PatternSolver {
    interface Searchable {
        val searchString: String
    }

    fun solve(a: List<Searchable>, b: List<Searchable>,
              candidatesProgressCallback: ((Int,Int)->Unit)? = null,
              reductionProgressCallback: ((Int,Int)->Unit)? = null): List<String>
}
