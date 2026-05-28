package poeregex

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
internal fun buildCandidates(a: List<String>, b: List<String>): Map<String, Set<String>> {
    val bSet = b.toSet()

    // pattern → set of b-elements it covers (only valid patterns are kept)
    val candidates = mutableMapOf<String, MutableSet<String>>()

    for (element in b) {
        var covered = false
        for (pattern in substrings(element)) {
            val matchesInA = a.filter { it.contains(pattern) }
            if (matchesInA.all { it in bSet }) {
                candidates.getOrPut(pattern) { mutableSetOf() }
                    .addAll(matchesInA.filter { it in bSet })
                covered = true
            }
        }
        if (!covered) throw UnresolvablePatternException(element)
    }

    return candidates
}
