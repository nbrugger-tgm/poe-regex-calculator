package poeregex

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.readText

// ── raw JSON types (internal) ─────────────────────────────────────────────────

@Serializable
private data class RawSpawnWeight(
    val tag: String,
    val weight: Int,
)

@Serializable
private data class RawMod(
    val name: String,
    val text: String? = null,
    val groups: List<String> = emptyList(),
    @SerialName("generation_type") val generationType: String,
    val domain: String,
    @SerialName("spawn_weights") val spawnWeights: List<RawSpawnWeight> = emptyList(),
)

@Serializable
private data class RawBaseItem(
    @SerialName("item_class") val itemClass: String,
    val tags: List<String> = emptyList(),
)

// ── text cleaning ─────────────────────────────────────────────────────────────

private val WEAPON_CLASSES = setOf(
    "Bow", "Claw", "Crossbow", "Dagger", "Flail",
    "One Hand Axe", "One Hand Mace", "One Hand Sword",
    "Sceptre", "Spear", "Staff",
    "Two Hand Axe", "Two Hand Mace", "Two Hand Sword",
    "Wand", "Warstaff",
)

private val ARMOUR_CLASSES = setOf(
    "Body Armour", "Boots", "Buckler", "Focus",
    "Gloves", "Helmet", "Shield",
)

private val JEWELLERY_CLASSES = setOf("Amulet", "Belt", "Ring")

private val RELEVANT_ITEM_CLASSES = WEAPON_CLASSES + ARMOUR_CLASSES + JEWELLERY_CLASSES

private val MARKUP_RE = Regex("""\[(\w*)(\|([\w ]*))?\]""")

/** Resolves [display|link] markup to the display part. */
internal fun cleanText(raw: String): String = MARKUP_RE.replace(raw) {
    it.groupValues[3].ifEmpty {it.groupValues[1]}
}

// ── core parsing logic (internal, testable without I/O) ───────────────────────

private val json = Json { ignoreUnknownKeys = true }

/**
 * Parses raw JSON strings and builds [ModsData].
 * Exposed as `internal` so tests can call it directly without touching the file system.
 */
internal fun buildModsData(modsJson: String, baseItemsJson: String): ModsData {
    val rawMods      = json.decodeFromString<Map<String, RawMod>>(modsJson)
    val rawBaseItems = json.decodeFromString<Map<String, RawBaseItem>>(baseItemsJson)

    // Build tag → set<item_class> from base_items
    val tagToClasses: Map<String, MutableSet<String>> = buildMap {
        for (item in rawBaseItems.values) {
            for (tag in item.tags) {
                getOrPut(tag) { mutableSetOf() }.also {
                    it.add(item.itemClass)
                }
            }
        }
    }

    val itemClasses: List<String> = rawBaseItems.values
        .map { it.itemClass }
        .filter { it in RELEVANT_ITEM_CLASSES }
        .distinct()
        .sorted()

    // Filter and convert mods
    val mods: Map<ModName, ModEntry> = rawMods.values
        .filter { it.domain == "item" && it.generationType != "unique" && it.name.isNotEmpty() }
        .map { raw ->
            val resolvedItemTypes = raw.spawnWeights.asSequence()
                .filter { it.weight > 0 }
                .flatMap { tagToClasses[it.tag] ?: emptySet() }
                .filter{ it in RELEVANT_ITEM_CLASSES }
                .distinct()
                .sorted()
                .map { cls -> ItemTypeRef(id = cls, name = cls) }
                .toList()

            ModEntry(
                name      = raw.name,
                text      = raw.text?.let(::cleanText) ?: "",
                groups    = raw.groups,
                itemTypes = resolvedItemTypes,
            )
        }
        .filter { it.itemTypes.isNotEmpty() }
        .associateBy { it.name }

    // Group index: group id → list of mod names (only valid mods)
    val groups: Map<String, MutableList<ModName>> = buildMap {
        for ((_, entry) in mods) {
            for (group in entry.groups) {
                getOrPut(group) { mutableListOf() }.also {
                    it.add(entry.name)
                }
            }
        }
    }

    val allStrings: List<String> = (
        itemClasses +
        mods.map { (k,v) -> searchableModName(k, v) }
    ).distinct()

    return ModsData(
        mods        = mods,
        groups      = groups,
        itemClasses = itemClasses,
        allStrings  = allStrings,
    )
}

fun searchableModName(k: ModName, v: ModEntry): String = "$k^^^^^^^^^^^^^^^^^^^${replaceRanges(v.text, '#')}"

/**
 * Ensures both data files exist under [projectDir] (downloading if absent),
 * parses them, and returns a fully indexed [ModsData].
 */
fun loadModsData(projectDir: Path): ModsData {
    val (modsFile, baseItemsFile) = ensureDataFiles(projectDir)
    return buildModsData(modsFile.readText(), baseItemsFile.readText())
}
