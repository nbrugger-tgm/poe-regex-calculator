package poeregex

/**
 * A resolved item type that a mod can roll on.
 *
 * Both fields carry the [item_class] value from base_items.json (e.g., "Ring",
 * "Body Armour"). They are intentionally kept separate so callers can substitute
 * a display-friendly label for [name] without losing the stable [id].
 */
data class ItemTypeRef(
    val id: String,
    val name: String,
)

/**
 * A single craftable mod from mods.json, filtered to item-domain, non-unique,
 * non-empty-name entries only.
 *
 * @property name      Tier-specific suffix/prefix name shown in-game, e.g. "of the Brute".
 * @property text      Human-readable mod effect with markup resolved, e.g. "+(5-8) to Strength".
 * @property groups    Mod family identifiers, e.g. ["Strength"]. Usually one entry.
 * @property itemTypes Item classes this mod can roll on, derived from spawn_weights > 0.
 */
data class ModEntry(
    val id: String,
    val name: String,
    val text: String,
    val groups: List<String>,
    val itemTypes: List<ItemTypeRef>,
)
