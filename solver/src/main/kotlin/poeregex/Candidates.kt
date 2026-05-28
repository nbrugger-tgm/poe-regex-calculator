package poeregex

import poeregex.PatternSolver.Searchable

/**
 * Generates all non-empty substrings of [s], shortest first.
 */
internal fun substrings(s: String): Sequence<String> = sequence {
    for (len in 1..s.length) {
        for (start in 0..s.length - len) {
            yield(s.substring(start, start + len))
        }
    }
}

/**
 * Builds the candidate map for an exact-minimum-set-cover search.
 *
 * A pattern is *valid* when every string in [a] that contains it is also in [b].
 *
 * @return Map from valid pattern to the non-empty subset of [b] it covers,
 *         deduplicated so each (pattern → coverage) entry is unique.
 * @throws UnresolvablePatternException for the first element of [b] that has no valid pattern.
 */
internal fun buildCandidates(a: List<Searchable>, b: List<Searchable>): Map<String, Set<Searchable>> {
    // pattern → set of b-elements it covers (only valid patterns are kept)
    val candidates = mutableMapOf<String, MutableSet<Searchable>>()

    val bStringSet = b.asSequence().map { it.searchString }.toSet()

    for (element in b) {
        var covered = false
        for (pattern in substrings(element.searchString)) {
            val matchesInA = a.filter { it.searchString.contains(pattern) }
            if (matchesInA.all { it.searchString in bStringSet }) {
                candidates.getOrPut(pattern) { mutableSetOf() }
                    .addAll(matchesInA.filter { it.searchString in bStringSet })
                covered = true
            }
        }
        if (!covered) {
            val collisions = a.asSequence().filter {
                it.searchString.contains(element.searchString)
            }.filter {
                !b.none { be -> be.searchString.contains(it.searchString) }
            }.toList()
            throw UnresolvablePatternException(element, collisions)
        }
    }

    return candidates
}
