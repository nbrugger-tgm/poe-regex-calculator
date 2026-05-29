package poeregex

/**
 * Finds the exact minimum set cover of [universe] using [candidates].
 *
 * Algorithm: depth-first search with two pruning strategies:
 *   1. **Upper-bound seed** – a greedy solution is computed first; any branch whose
 *      depth already matches the greedy size is cut immediately.
 *   2. **Lower-bound pruning** – at each node, a lower bound on the remaining cost is
 *      `ceil(|uncovered| / maxCoverageOfAnyRemainingCandidate)`.  If
 *      `currentDepth + lowerBound >= bestKnown` the branch is abandoned.
 *
 * Branching strategy: always branch on patterns that cover the *first* uncovered element;
 * this guarantees that every branch makes progress and limits the branching factor to the
 * number of valid patterns for one particular element.
 *
 * @param candidates Map from pattern to the subset of [universe] it covers.
 * @param universe   The full set that must be covered.
 * @return Minimum-size list of patterns whose union equals [universe].
 */
internal fun exactSetCover(
    candidates: Map<String, Set<String>>,
    universe: Set<String>,
    progressCallback: ((Int, Int) -> Unit)? = null,
): Set<String> {
    if (universe.isEmpty()) return emptySet()

    // Sort candidates: prefer those covering more elements (better branching order),
    // break ties by shorter pattern (more useful to the caller).
    val sortedCandidates: List<Pair<String, Set<String>>> = candidates.entries
        .map { (k, v) -> k to v }
        .sortedWith(compareByDescending<Pair<String, Set<String>>> { it.second.size }.thenBy { it.first.length })

    // Greedy upper bound to seed the search.
    val greedySolution = greedy(sortedCandidates, universe)

    var best = greedySolution

    val universeSize = universe.size

    var callCount = 0

    fun dfs(uncovered: Set<String>, chosen: Set<String>) {
        if(callCount++ >= 1000) {
            callCount = 0
            if(Thread.interrupted()) throw InterruptedException()
        }
        progressCallback?.invoke(universeSize - uncovered.size, universeSize)

        if (uncovered.isEmpty()) {
            if (chosen.size < best.size) best = chosen
            return
        }
        // Pruning: lower bound
        val maxCoverage = sortedCandidates.fold(0) { acc, (_, cov) ->
            if(acc > cov.size) acc
            else {
                val intersection = cov.count { uncovered.contains(it) }
                maxOf(acc, intersection)
            }
        }
        if (maxCoverage == 0) return  // no remaining candidate covers anything
        val lowerBound = (uncovered.size + maxCoverage - 1) / maxCoverage
        if (chosen.size + lowerBound >= best.size) return

        // Branch on patterns that cover the first uncovered element.
        val pivot = uncovered.first()
        val newUncovered = mutableSetOf<String>()
        val newChosen = mutableSetOf<String>()
        for ((pattern, coverage) in sortedCandidates) {
            if (pivot !in coverage) continue
            newUncovered.clear()
            uncovered.filterNotTo(newUncovered) { it in coverage }

            newChosen.clear()
            newChosen.addAll(chosen)
            newChosen.add(pattern)
            dfs(newUncovered, newChosen)
        }
    }

    dfs(universe, emptySet())
    progressCallback?.invoke(universeSize, universeSize)
    return best
}

/** Greedy set cover: repeatedly pick the candidate covering the most uncovered elements. */
private fun greedy(
    candidates: List<Pair<String, Set<String>>>,
    universe: Set<String>,
): Set<String> {
    val uncovered = universe.toMutableSet()
    val chosen = mutableSetOf<String>()
    while (uncovered.isNotEmpty()) {
        val (pattern, coverage) = candidates.maxByOrNull { (_, cov) -> (cov intersect uncovered).size }
            ?: break
        if ((coverage intersect uncovered).isEmpty()) break
        chosen += pattern
        uncovered -= coverage
    }
    return chosen
}
