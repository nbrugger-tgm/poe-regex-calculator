package poeregex

/**
 * [PatternSolver] implementation that returns the exact minimum number of patterns.
 */
class MinimumPatternSolver : PatternSolver {

    override fun solve(a: List<PatternSolver.Searchable>, b: List<PatternSolver.Searchable>,
                       candidatesProgressCallback: ((Int,Int)->Unit)?,
                       reductionProgressCallback: ((Int,Int)->Unit)?
    ): List<String> {
        val aStringSet = a.asSequence().map { it.searchString }.toSet()
        val bStringSet = b.asSequence().map { it.searchString }.toSet()

        require(bStringSet.all { it in aStringSet }) {
            "B contains elements not present in A: ${bStringSet - aStringSet}"
        }

        if (b.isEmpty()) return emptyList()

        val candidates = buildCandidates(a, b, candidatesProgressCallback)
        return exactSetCover(candidates.mapValues { it.value.asSequence().map { it.searchString }.toSet() }, bStringSet, reductionProgressCallback)
    }
}
