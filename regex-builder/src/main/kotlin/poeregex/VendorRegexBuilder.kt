package poeregex

import poeregex.PatternSolver.Searchable

private val RANGE_RE = Regex("""\(\d+(\.\d+)?-\d+(\.\d+)?\)""")

fun replaceRanges(firstModText: String, placeholder: Char): String =
    RANGE_RE.replace(firstModText, "$placeholder")

class ModPattern(val mod: ModEntry) : Searchable {
    override val searchString: String = mod.searchableModName()
    override fun toString(): String {
        return mod.toString()
    }
}

data class ItemPattern(override val searchString: String) : Searchable

/**
 * Builds a PoE vendor search regex string from a selection of item types and mods.
 *
 * Each entry in [selection] produces one output line in the format:
 *
 *     "typePattern1|typePattern2" "modPattern1|modPattern2"
 *
 * The two quoted sections are:
 *  - **Item type section**: minimum patterns that identify [ItemType] in the A set
 *    without matching any mod name or mod text.
 *  - **Mods section**: minimum patterns derived per group rule:
 *      - If **all** mods in a group are selected → B2 uses cleaned **texts**
 *        (the solver finds the shortest shared pattern, e.g. "to Strength").
 *      - If only a **subset** of a group is selected → B2 uses mod **names**.
 *
 * Multiple lines are joined by `"\n"`.
 *
 * @throws UnresolvablePatternException if any element of a B set cannot be covered
 *   without false positives in the A set.
 * @throws IllegalArgumentException if a [ModName] in [selection] is not present in [modsData].
 */
fun buildVendorRegex(
    modsData: ModsData,
    selection: Map<ItemType, List<ModName>>,
): String {
    if (selection.isEmpty()) return ""

    val solver = MinimumPatternSolver()

    val typePattern = solver.solve(
        a = modsData.allStrings,
        b = selection.keys.map { ItemPattern(it) },
    ).joinToString("|")
    val b2 = buildB2(modsData, selection.entries.flatMap { it.value })
    val modRestrictedList = allStrings(modsData.itemClasses, modsData.mods.filter {
        it.value.itemTypes.any { it.id in selection.keys }
    })
    val modPattern = solver.solve(
        a = modRestrictedList,
        b = b2,
    ).joinToString("|")

    return "\"$typePattern\" \"$modPattern\"".replace("#", "\\d+")
}

/**
 * Derives the B2 set (strings to cover with the mods regex) for a given list of selected mods.
 *
 * For each group that contains at least one selected mod:
 *  - All mods in the group are selected → contribute the cleaned **text** of each selected mod.
 *  - Only a subset is selected → contribute the mod **names** of the selected mods.
 */
private fun buildB2(modsData: ModsData, modIDs: List<String>): List<Searchable> {
    return modIDs.map {
        ModPattern(modsData.mods[it]!!)
    }
}

fun ModEntry.searchableModName(): String = "${name}^^^^^^^^^^^^^^^^^^^^^^^^^^^^^${replaceRanges(text, '#')}".uppercase()
    .replace("\n", "$^^^^^^^^^^^^^^^^^^^^^^^^^^^^")

val ModsData.allStrings: List<Searchable> get() = allStrings(itemClasses, mods)
fun allStrings(itemClasses: List<String>, mods: Map<String, ModEntry>): List<Searchable> = (
        itemClasses.map { ItemPattern(it) } + mods.map { (k, v) -> ModPattern(v) }
        ).distinct()
