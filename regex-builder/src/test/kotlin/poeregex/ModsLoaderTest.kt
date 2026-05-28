package poeregex

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests for [buildModsData] — the pure parsing/indexing function that
 * takes raw JSON strings and returns a [ModsData].
 *
 * No HTTP or file-system access; all inputs are inline JSON fixtures.
 */
class ModsLoaderTest {

    // ── fixtures ──────────────────────────────────────────────────────────────

    /**
     * Minimal mods.json fixture.
     *
     *  Strength1 / Strength2  — item domain, suffix, non-empty name  → INCLUDE
     *  StrUnique              — generation_type "unique"             → EXCLUDE
     *  MonsterMod1            — domain "monster"                     → EXCLUDE
     *  EmptyNameMod           — name is ""                           → EXCLUDE
     */
    private val modsJson = """
        {
          "Strength1": {
            "domain": "item",
            "generation_type": "suffix",
            "name": "of the Brute",
            "text": "+(5-8) to [Strength|Strength]",
            "groups": ["Strength"],
            "spawn_weights": [
              {"tag": "ring",   "weight": 1},
              {"tag": "amulet", "weight": 1},
              {"tag": "default","weight": 0}
            ]
          },
          "Strength2": {
            "domain": "item",
            "generation_type": "suffix",
            "name": "of the Wrestler",
            "text": "+(9-12) to [Strength|Strength]",
            "groups": ["Strength"],
            "spawn_weights": [
              {"tag": "ring",   "weight": 1},
              {"tag": "amulet", "weight": 1},
              {"tag": "default","weight": 0}
            ]
          },
          "StrUnique": {
            "domain": "item",
            "generation_type": "unique",
            "name": "of Unique Strength",
            "text": "+99 to [Strength|Strength]",
            "groups": ["Strength"],
            "spawn_weights": [{"tag": "default", "weight": 1}]
          },
          "MonsterMod1": {
            "domain": "monster",
            "generation_type": "suffix",
            "name": "of the Monster",
            "text": "Monster bonus",
            "groups": ["MonsterGroup"],
            "spawn_weights": [{"tag": "default", "weight": 1}]
          },
          "EmptyNameMod": {
            "domain": "item",
            "generation_type": "suffix",
            "name": "",
            "text": "+(1-2) to [Strength|Strength]",
            "groups": ["Strength"],
            "spawn_weights": [{"tag": "ring", "weight": 1}]
          }
        }
    """.trimIndent()

    /**
     * Minimal base_items.json fixture.
     *
     *  ring   tag → item_class "Ring"
     *  amulet tag → item_class "Amulet"
     *  helmet tag → item_class "Helmet"   (not used by any mod above, but present in A set)
     */
    private val baseItemsJson = """
        {
          "Metadata/Items/Rings/Ring1": {
            "item_class": "Ring",
            "tags": ["ring", "default"]
          },
          "Metadata/Items/Rings/Ring2": {
            "item_class": "Ring",
            "tags": ["ring", "default"]
          },
          "Metadata/Items/Amulets/Amulet1": {
            "item_class": "Amulet",
            "tags": ["amulet", "default"]
          },
          "Metadata/Items/Armours/Helmets/Helmet1": {
            "item_class": "Helmet",
            "tags": ["helmet", "default"]
          }
        }
    """.trimIndent()

    private val data: ModsData by lazy { buildModsData(modsJson, baseItemsJson) }

    // ── filtering ─────────────────────────────────────────────────────────────

    @Test
    fun `unique mods are excluded`() {
        assertFalse("of Unique Strength" in data.mods, "unique mod should be filtered out")
    }

    @Test
    fun `non-item-domain mods are excluded`() {
        assertFalse("of the Monster" in data.mods, "monster-domain mod should be filtered out")
    }

    @Test
    fun `empty-name mods are excluded`() {
        assertTrue(data.mods.values.none { it.name.isEmpty() }, "empty-name mods should be filtered out")
    }

    @Test
    fun `valid item-domain mods are included`() {
        assertTrue("of the Brute" in data.mods, "Strength1 should be included")
        assertTrue("of the Wrestler" in data.mods, "Strength2 should be included")
        assertEquals(2, data.mods.size)
    }

    // ── text cleaning ─────────────────────────────────────────────────────────

    @Test
    fun `bracket markup is resolved to display part`() {
        assertEquals("+(5-8) to Strength", data.mods["of the Brute"]?.text)
        assertEquals("+(9-12) to Strength", data.mods["of the Wrestler"]?.text)
    }

    // ── groups index ──────────────────────────────────────────────────────────

    @Test
    fun `groups map contains only valid mods`() {
        val strengthGroup = data.groups["Strength"]
        assertNotNull(strengthGroup)
        assertEquals(
            setOf("of the Brute", "of the Wrestler"),
            strengthGroup!!.toSet(),
        )
    }

    @Test
    fun `excluded mods do not appear in groups`() {
        val strengthGroup = data.groups["Strength"] ?: emptyList()
        assertFalse("of Unique Strength" in strengthGroup)
        assertFalse("" in strengthGroup)
        assertNull(data.groups["MonsterGroup"])
    }

    // ── item type resolution ──────────────────────────────────────────────────

    @Test
    fun `spawn_weights with weight 0 are not resolved`() {
        val itemTypes = data.mods["of the Brute"]!!.itemTypes.map { it.id }
        assertFalse("default" in itemTypes, "tag with weight=0 must not appear in itemTypes")
    }

    @Test
    fun `spawn_weights with weight gt 0 resolve to item_class values`() {
        val ids = data.mods["of the Brute"]!!.itemTypes.map { it.id }.toSet()
        assertEquals(setOf("Ring", "Amulet"), ids)
    }

    @Test
    fun `ItemTypeRef id and name are equal`() {
        val refs = data.mods["of the Brute"]!!.itemTypes
        assertTrue(refs.all { it.id == it.name }, "id and name must be equal (both are item_class)")
    }

    @Test
    fun `item types are deduplicated when multiple tags resolve to the same item_class`() {
        // Both ring tags point to "Ring" — should appear once
        val modsWithDupTags = """
            {
              "Mod1": {
                "domain": "item", "generation_type": "suffix",
                "name": "of Dup", "text": "dup",
                "groups": ["G"],
                "spawn_weights": [
                  {"tag": "ring",   "weight": 1},
                  {"tag": "ring",   "weight": 1},
                  {"tag": "default","weight": 0}
                ]
              }
            }
        """.trimIndent()
        val d = buildModsData(modsWithDupTags, baseItemsJson)
        val ids = d.mods["of Dup"]!!.itemTypes.map { it.id }
        assertEquals(1, ids.count { it == "Ring" }, "Ring must appear exactly once")
    }

    // ── item classes ──────────────────────────────────────────────────────────

    @Test
    fun `itemClasses contains all distinct item_class values from base_items`() {
        assertEquals(setOf("Ring", "Amulet", "Helmet"), data.itemClasses.toSet())
    }

    // ── A set (allStrings) ────────────────────────────────────────────────────

    @Test
    fun `allStrings contains all item class values`() {
        assertTrue("Ring" in data.allStrings)
        assertTrue("Amulet" in data.allStrings)
        assertTrue("Helmet" in data.allStrings)
    }

    @Test
    fun `allStrings contains all valid mod names`() {
        assertTrue("of the Brute" in data.allStrings)
        assertTrue("of the Wrestler" in data.allStrings)
    }

    @Test
    fun `allStrings contains cleaned mod texts`() {
        assertTrue("+(5-8) to Strength" in data.allStrings)
        assertTrue("+(9-12) to Strength" in data.allStrings)
    }

    @Test
    fun `allStrings does not contain raw markup text`() {
        assertTrue(data.allStrings.none { "[Strength|Strength]" in it })
    }

    @Test
    fun `allStrings contains no duplicates`() {
        assertEquals(data.allStrings.size, data.allStrings.toSet().size)
    }

    @Test
    fun `excluded mod names and texts are absent from allStrings`() {
        assertFalse("of Unique Strength" in data.allStrings)
        assertFalse("of the Monster" in data.allStrings)
        assertFalse("Monster bonus" in data.allStrings)
    }
}
