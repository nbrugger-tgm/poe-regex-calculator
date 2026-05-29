package poeregex

import poeregex.PatternSolver.Searchable

private val RANGE_RE = Regex("""\(\d+(\.\d+)?-\d+(\.\d+)?\)""")
private val RARITIES = setOf("normal","rare","magic","unique")

fun replaceRanges(firstModText: String, placeholder: Char): String =
    RANGE_RE.replace(firstModText, "$placeholder")

class ModPattern(val mod: ModEntry) : Searchable {
    override val searchString: String = mod.searchableModName()
    override fun toString(): String {
        return mod.toString()
    }
}

class ItemPattern private constructor(itemName: String) : Searchable {
    override val searchString: String = itemName.uppercase()

    companion object {
        fun ofItemClass(itemClass: String): ItemPattern = ItemPattern("item class: $itemClass")
        fun ofItemBase(base: RawBaseItem) = ItemPattern(base.name)
        fun ofRarity(rarity: String) = ItemPattern("rarity: $rarity")
        fun baseStat(it: String) = ItemPattern("$it: #")
        fun damageStat(uppercase: String) = ItemPattern("$uppercase damage: #-#")
        fun ofRequirement() = ItemPattern("requires: #")
    }
}

val msLimit = 250

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
    candidatesProgressCallback: ((Int, Int) -> Unit)? = null,
    reductionProgressCallback: ((Int, Int) -> Unit)? = null,
): String {
    if (selection.isEmpty()) return ""

    val solver = MinimumPatternSolver()
    val typeRestrictedList = allStrings(
        modsData.itemClasses.mapValues { (itemClass, bases) -> if(selection.containsKey(itemClass)) listOf() else bases },
        modsData.mods.filter {
            it.value.itemTypes.any { it.id !in selection.keys }
        }
    )
    val itemClassesToFind = selection.keys.map { ItemPattern.ofItemClass(it) }

    var lastUpdate = System.currentTimeMillis()
    val typePattern = solver.solve(
        a = typeRestrictedList,
        b = itemClassesToFind,
        candidatesProgressCallback = { progress, total ->
            if (progress == total || System.currentTimeMillis()-lastUpdate>=msLimit) {
                println("Type candidate progress $progress/$total")
                lastUpdate = System.currentTimeMillis()
                candidatesProgressCallback?.invoke(progress,total)
            }
        },
        reductionProgressCallback = { progress, total ->
            if (progress == total || System.currentTimeMillis()-lastUpdate>=msLimit) {
                println("Type reduction progress $progress/$total")
                lastUpdate = System.currentTimeMillis()
                reductionProgressCallback?.invoke(progress,total)
            }
        }
    ).joinToString("|")
    println("Type pattern : $typePattern")
    val b2 = buildB2(modsData, selection.entries.flatMap { it.value })
    val modRestrictedList = allStrings(modsData.itemClasses.filterKeys { !selection.containsKey(it) }, modsData.mods.filter {
        it.value.itemTypes.any { it.id in selection.keys }
    })
    candidatesProgressCallback?.invoke(0,1)
    reductionProgressCallback?.invoke(0,1)
    val modPattern = solver.solve(
        a = modRestrictedList,
        b = b2,
        candidatesProgressCallback = { progress, total ->
            if (progress == total || System.currentTimeMillis()-lastUpdate>=msLimit) {
                println("Mod candidate progress $progress/$total")
                lastUpdate = System.currentTimeMillis()
                candidatesProgressCallback?.invoke(progress,total)
            }
        },
        reductionProgressCallback = { progress, total ->
            if (progress == total || System.currentTimeMillis()-lastUpdate>=msLimit) {
                println("Mod reduction progress $progress/$total")
                lastUpdate = System.currentTimeMillis()
                reductionProgressCallback?.invoke(progress,total)
            }
        }
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

fun ModEntry.searchableModName(): String = "^"+replaceRanges(text, '#').uppercase()
    .replace("\n", "$^")
    .replace("+", "\\+")
    .replace(".", "\\.") + "$"

fun String.searchable(): String = "^"+ uppercase().replace("\n", "$^")
    .replace("+", "\\+")
    .replace(".", "\\.") + "$"

val ModsData.allStrings: List<Searchable> get() = allStrings(itemClasses, mods)
fun allStrings(itemClasses: Map<String, List<RawBaseItem>>, mods: Map<String, ModEntry>): List<Searchable> = (
        itemClasses.flatMap { (itemClass, bases) ->
            bases.map{ ItemPattern.ofItemBase(it) } + ItemPattern.ofItemClass(itemClass)
        } + mods.map { (k, v) -> ModPattern(v) } +
                RARITIES.map { ItemPattern.ofRarity(it.uppercase()) } +
                setOf("Armour", "Energy shield","Evasion").map { ItemPattern.baseStat(it) } +
                setOf("Physical", "Fire","Cold","Lightning","Chaos").map { ItemPattern.damageStat(it) } +
                ItemPattern.ofRequirement()
).distinct()

