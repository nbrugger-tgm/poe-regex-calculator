package poeregex.ui

import poeregex.RawBaseItem
import poeregex.RawMod

val WEAPON_CLASSES = setOf(
    "Bow", "Claw", "Crossbow", "Dagger", "Flail",
    "One Hand Axe", "One Hand Mace", "One Hand Sword",
    "Sceptre", "Spear", "Staff",
    "Two Hand Axe", "Two Hand Mace", "Two Hand Sword",
    "Wand", "Warstaff", "Talisman"
)

val ARMOUR_CLASSES = setOf(
    "Body Armour", "Boots", "Buckler", "Focus",
    "Gloves", "Helmet", "Shield",
)

val JEWELLERY_CLASSES = setOf("Amulet", "Belt", "Ring")
val VENDOR_CLASSES = WEAPON_CLASSES + ARMOUR_CLASSES + JEWELLERY_CLASSES

val TABLET_CLASSES = setOf("TowerAugmentation")
val WAYSTONE_CLASSES = setOf("Map")

val CAMPAIGN_VENDOR_CONFIG = RegexType(
    "Campaign Vendors",
    { mod ->
        mod.domain == "item" &&
                mod.generationType != "unique" &&
                mod.name.isNotEmpty() &&
                mod.requiredLevel <= 65 &&
                !mod.essenceMod
    },
    { item ->
        item.releaseState == "released" &&
                item.dropLevel <= 65 &&
                item.itemClass in VENDOR_CLASSES
    },
    modBasis = RegexType.SelectionBasis.ITEM_CLASS
)
val REGEX_TYPE_CONFIGURATIONS = listOf(
    CAMPAIGN_VENDOR_CONFIG, RegexType(
        "Maps",
        { mod ->
            mod.domain == "area" &&
            mod.generationType != "unique" &&
                    mod.name.isNotEmpty() &&
                    !mod.essenceMod
        },
        { item ->
            item.releaseState == "released" &&
                    item.itemClass in WAYSTONE_CLASSES
        },
        modBasis = RegexType.SelectionBasis.ITEM_CLASS
    ), RegexType(
        "Tablets",
        { mod ->
            mod.domain == "tablet" &&
            mod.name.isNotEmpty() &&
                    !mod.essenceMod
        },
        { item ->
            item.releaseState == "released" &&
                    item.itemClass in TABLET_CLASSES
        },
        modBasis = RegexType.SelectionBasis.BASE_TYPE
    ))

data class RegexType(
    val name: String,
    val modFilter: (RawMod) -> Boolean,
    val itemFilter: (RawBaseItem) -> Boolean,
    val modBasis: SelectionBasis
) {
    enum class SelectionBasis {
        ITEM_CLASS,
        BASE_TYPE
    }
}