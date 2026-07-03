package poeregex

import poeregex.PatternSolver.Searchable

private val RARITIES = setOf("normal", "rare", "magic", "unique")
private val damageTypes = setOf("Physical", "Fire", "Cold", "Lightning", "Chaos")
private val defences = setOf("Armour", "Energy shield", "Evasion")

private val RANGE_REGEX = Regex("""\(\d+(\.\d+)?-\d+(\.\d+)?\)""")
private val PLACEHOLDER_CHARS = mapOf(
    '#' to "\\d+"
)


fun replaceRanges(firstModText: String, placeholder: Char): String =
    RANGE_REGEX.replace(firstModText, "$placeholder")

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
        fun ofModName(mod: ModEntry) = ItemPattern(mod.name)
    }
}

val msLimit = 500

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
    filterByClass: Boolean = true,
    candidatesProgressCallback: ((Int, Int) -> Unit)? = null,
    reductionProgressCallback: ((Int, Int) -> Unit)? = null,
): String {
    if (selection.isEmpty()) return ""

    val solver = MinimumPatternSolver()
    //mods that appear on non-selected bases
    val typeRestrictedModPool = modsData.mods.filter<String, ModEntry> {
        if (filterByClass) it.value.itemTypes.any { it.id !in selection.keys }
        else it.value.itemBases.any { it.id !in selection.keys }
    }
    val typeRestrictedList = allStrings(
        modsData.itemClasses.mapValues { (itemClass, bases) ->
            if (filterByClass) {
                if (selection.containsKey(itemClass)) listOf() else bases
            } else {
                bases
            }
        },
        typeRestrictedModPool, filterByClass
    )
    val itemClassesToFind = if(filterByClass) selection.keys.map { ItemPattern.ofItemClass(it) }
    else selection.keys.map { selectedBase -> ItemPattern.ofItemBase(modsData.itemClasses.values.flatten().find { it.name ==selectedBase }!!) }

    var lastUpdate = System.currentTimeMillis()
    val typePattern = solver.solve(
        a = typeRestrictedList,
        b = itemClassesToFind,
        candidatesProgressCallback = { progress, total ->
            if (progress == total || System.currentTimeMillis() - lastUpdate >= msLimit) {
                println("Type candidate progress $progress/$total")
                lastUpdate = System.currentTimeMillis()
                candidatesProgressCallback?.invoke(progress, total)
            }
        },
        reductionProgressCallback = { progress, total ->
            if (progress == total || System.currentTimeMillis() - lastUpdate >= msLimit) {
                println("Type reduction progress $progress/$total")
                lastUpdate = System.currentTimeMillis()
                reductionProgressCallback?.invoke(progress, total)
            }
        }
    ).joinToString("|")
    println("Type pattern : $typePattern")
    val modsToFind = buildB2(modsData, selection.entries.flatMap { it.value })
    //mods that can appear on the selected bases
    val modRestrictedModPool = modsData.mods.filter<String, ModEntry> {
        if (filterByClass) it.value.itemTypes.any { it.id in selection.keys }
        else it.value.itemBases.any { it.id in selection.keys }
    }
    val modRestrictedList = allStrings(modsData.itemClasses.filter { (itemClass, itemBase) ->
        if (filterByClass) !selection.containsKey(itemClass)
        else !itemBase.map { it.name }.any { selection.containsKey(it) }
    }, modRestrictedModPool, filterByClass)
    candidatesProgressCallback?.invoke(0, 1)
    reductionProgressCallback?.invoke(0, 1)
    val modPattern = solver.solve(
        a = modRestrictedList,
        b = modsToFind,
        candidatesProgressCallback = { progress, total ->
            if (progress == total || System.currentTimeMillis() - lastUpdate >= msLimit) {
                println("Mod candidate progress $progress/$total")
                lastUpdate = System.currentTimeMillis()
                candidatesProgressCallback?.invoke(progress, total)
            }
        },
        reductionProgressCallback = { progress, total ->
            if (progress == total || System.currentTimeMillis() - lastUpdate >= msLimit) {
                println("Mod reduction progress $progress/$total")
                lastUpdate = System.currentTimeMillis()
                reductionProgressCallback?.invoke(progress, total)
            }
        }
    ).joinToString("|")

    return "\"$typePattern\" \"$modPattern\"".asPoeRegex()
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

fun ModEntry.searchableModName(): String = replaceRanges(text, '#').searchable()

fun String.searchable(): String = "^" + uppercase().replace("\n", "$^")
    .replace("+", "\\+")
    .replace("\n", "$^")
    .replace(".", "\\.") + "$"

val ModsData.allStrings: List<Searchable> get() = allStrings(itemClasses, mods, true)
fun allStrings(itemClasses: Map<String, List<RawBaseItem>>, mods: Map<String, ModEntry>, ignoreBaseTypes: Boolean): List<Searchable> {
    return (
            itemClasses.flatMap { (itemClass, bases) ->
                bases.map { ItemPattern.ofItemBase(it) } + ItemPattern.ofItemClass(itemClass)
            } + mods.map { (k, v) -> ModPattern(v) } +
                    RARITIES.map { ItemPattern.ofRarity(it.uppercase()) } +
                    defences.map { ItemPattern.baseStat(it) } +
                    damageTypes.map { ItemPattern.damageStat(it) } +
                    ItemPattern.ofRequirement()
            ).distinct() + mods.map {
        ItemPattern.ofModName(it.value)
    } + (if(ignoreBaseTypes) emptyList() else itemClasses.flatMap{ it.value}
        .distinctBy {  it.name  }
        .map { ItemPattern.ofItemBase(it) })
}

private fun String.asPoeRegex(): String {
    var new = this
    PLACEHOLDER_CHARS.forEach { (ch, string) ->
        new = new.replace("$ch", string)
    }
    return new
}

