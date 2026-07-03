package poeregex

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.readText

// ── raw JSON types (internal) ─────────────────────────────────────────────────

@Serializable
data class RawSpawnWeight(
    val tag: String,
    val weight: Int,
)

@Serializable
data class RawMod(
    val name: String,
    val text: String? = null,
    val groups: List<String> = emptyList(),
    @SerialName("generation_type") val generationType: String,
    val domain: String,
    @SerialName("spawn_weights") val spawnWeights: List<RawSpawnWeight> = emptyList(),
    @SerialName("required_level") val requiredLevel: Int,
    @SerialName("is_essence_only") val essenceMod: Boolean,
)

@Serializable
data class RawBaseItem(
    @SerialName("item_class") val itemClass: String,
    @SerialName("drop_level") val dropLevel: Int,
    @SerialName("release_state") val releaseState: String,
    val name: String,
    val tags: List<String> = emptyList(),
)

private val EXCLUDED_BASES = setOf("Ring")


private val MARKUP_RE = Regex("""\[(\w*)(\|([\w ]*))?\]""")

/** Resolves [display|link] markup to the display part. */
internal fun cleanText(raw: String): String = MARKUP_RE.replace(raw) {
    it.groupValues[3].ifEmpty { it.groupValues[1] }
}

// ── core parsing logic (internal, testable without I/O) ───────────────────────

private val json = Json { ignoreUnknownKeys = true }

fun GameData.filter(
    itemFilter: (RawBaseItem) -> Boolean,
    modFilter: (RawMod) -> Boolean
): ModsData {
    val relevantItemBases = items.values
        .filter { itemFilter(it) }
        .filter { it.name !in EXCLUDED_BASES }
        .filter { it.releaseState == "released" }

    // Build tag → set<item_class> from base_items

    val (tagToClasses, tagToBases) = buildTaggingMaps(relevantItemBases)

    val relevantItemClasses = relevantItemBases.map { it.itemClass }.distinct()

    // Filter and convert mods
    val mods: Map<String, ModEntry> = this.mods
        .asSequence()
        .filter { (id, mod) ->
            modFilter(mod)
        }
        .map { (id, raw) ->
            val resolvedItemTypes = raw.spawnWeights.asSequence()
                .filter { it.weight > 0 }
                .flatMap { tagToClasses[it.tag] ?: emptySet() }
                .filter { it in relevantItemClasses }
                .distinct()
                .sorted()
                .map { cls -> ItemTypeRef(id = cls, name = cls) }
                .toList()
            val resolvedBaseTypes = raw.spawnWeights.asSequence()
                .filter { it.weight > 0 }
                .flatMap { tagToBases[it.tag] ?: emptySet() }
                .filter { base -> relevantItemBases.any { it.name == base} }
                .distinct()
                .sorted()
                .map { cls -> ItemTypeRef(id = cls, name = cls) }
                .toList()

            ModEntry(
                id = id,
                name = raw.name,
                text = raw.text?.let(::cleanText) ?: "",
                groups = raw.groups,
                itemTypes = resolvedItemTypes,
                itemBases = resolvedBaseTypes
            )
        }
        .filter { it.itemTypes.isNotEmpty() }
        .associateBy { it.id }

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

    return ModsData(
        mods = mods,
        groups = groups,
        itemClasses = relevantItemBases.groupBy { it.itemClass }
    )
}

private fun buildTaggingMaps(relevantItemBases: List<RawBaseItem>): Pair<Map<String, Set<String>>, Map<String, Set<String>>> {
    val classes = mutableMapOf<String, MutableSet<String>>()
    val bases = mutableMapOf<String, MutableSet<String>>()
    for (item in relevantItemBases) {
        for (tag in item.tags) {
//            if (tag == "default") continue
            classes.getOrPut(tag) { mutableSetOf() }.also {
                it.add(item.itemClass)
            }
            bases.getOrPut(tag) { mutableSetOf() }.also {
                it.add(item.name)
            }
        }
    }
    return classes to bases
}

data class GameData(
    val mods: Map<String, RawMod>,
    val items: Map<String, RawBaseItem>
)

/**
 * Ensures both data files exist under [projectDir] (downloading if absent) and parses them.
 */
fun loadGameData(projectDir: Path): GameData {
    val (modsFile, baseItemsFile) = ensureDataFiles(projectDir)
    val rawMods = json.decodeFromString<Map<String, RawMod>>(modsFile.readText())
    val rawBaseItems = json.decodeFromString<Map<String, RawBaseItem>>(baseItemsFile.readText())
    return GameData(rawMods, rawBaseItems)
}
