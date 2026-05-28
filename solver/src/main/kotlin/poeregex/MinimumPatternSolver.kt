package poeregex

/**
 * [PatternSolver] implementation that returns the exact minimum number of patterns.
 */
class MinimumPatternSolver : PatternSolver {

    override fun solve(a: List<String>, b: List<String>): List<String> {
        val aSet = a.toSet()
        val bSet = b.toSet()

        require(bSet.all { it in aSet }) {
            "B contains elements not present in A: ${bSet - aSet}"
        }

        if (b.isEmpty()) return emptyList()

        val candidates = buildCandidates(a, b)
        return exactSetCover(candidates, bSet)
    }
}
