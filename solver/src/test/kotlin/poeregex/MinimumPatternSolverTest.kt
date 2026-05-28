package poeregex

import org.junit.jupiter.api.Nested

class MinimumPatternSolverTest : PatternSolverContract() {
    override val solver: PatternSolver = MinimumPatternSolver()

    // Any MinimumPatternSolver-specific tests can be added here as @Nested classes.
}
