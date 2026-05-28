package poeregex

/** A mod's in-game name field, e.g. "of the Brute". */
typealias ModName = String

/** An item_class value from base_items.json, e.g. "Ring" or "Body Armour". */
typealias ItemType = String

/**
 * The fully parsed and indexed representation of the PoE mod data,
 * ready for use by [buildVendorRegex].
 *
 * @property mods        All valid mods keyed by [ModName].
 * @property groups      Maps each group id to the list of [ModName]s in that group
 *                       (only valid mods — item-domain, non-unique, non-empty name).
 * @property itemClasses All distinct item_class values present in base_items.json.
 * @property allStrings  The solver's A set: itemClasses ∪ modNames ∪ modTexts.
 */
class ModsData(
    val mods: Map<ModName, ModEntry>,
    val groups: Map<String, List<ModName>>,
    val itemClasses: List<String>
)