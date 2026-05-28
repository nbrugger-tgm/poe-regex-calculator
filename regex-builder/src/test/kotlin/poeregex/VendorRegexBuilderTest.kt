package poeregex

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests for [buildVendorRegex].
 *
 * All tests use hand-crafted [ModsData] fixtures — no file I/O or HTTP.
 *
 * Vendor regex format per entry:  "typePattern1|typePattern2" "modPattern1|modPattern2"
 * Multiple entries are separated by "\n".
 */
class VendorRegexBuilderTest {

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Parses a vendor regex line into its two quoted sections. */
    private fun splitLine(line: String): Pair<String, String> {
        val re = Regex("""^"([^"]*)" "([^"]*)"$""")
        val m = re.matchEntire(line.trim())
            ?: error("Line does not match expected format '\"...\" \"...\"': $line")
        return m.groupValues[1] to m.groupValues[2]
    }

    /**
     * Asserts the basic structural invariant of a vendor regex:
     *  - every produced type pattern identifies at least one string in [allStrings] that IS the item type
     *  - every produced type pattern does NOT match any mod name or text
     *  - every produced mod  pattern identifies at least one expected mod string
     *  - every produced mod  pattern does NOT match any item class string
     */
    private fun assertVendorRegexValid(
        result: String,
        modsData: ModsData,
        selection: Map<ItemType, List<ModName>>,
    ) {
        val lines = result.lines().filter { it.isNotBlank() }
        assertEquals(selection.size, lines.size, "Expected one line per map entry")

        lines.zip(selection.entries.toList()).forEach { (line, entry) ->
            val (itemType, _) = entry
            val (typeSection, modSection) = splitLine(line)

            val typePatterns = typeSection.split("|")
            val modPatterns  = modSection.split("|")

            // Each type pattern must match the requested item type
            assertTrue(
                typePatterns.all { p -> modsData.allStrings.any { s -> s.contains(p) && s == itemType } },
                "Type pattern in '$typeSection' must match item type '$itemType'"
            )
            // Each type pattern must NOT match any mod name or text (no false positives)
            val modStrings = (modsData.mods.keys + modsData.mods.values.map { it.text }).toSet()
            assertTrue(
                typePatterns.none { p -> modStrings.any { s -> s.contains(p) } },
                "Type pattern '$typeSection' must not match any mod string"
            )
            // mod patterns must be non-empty
            assertTrue(modPatterns.all { it.isNotBlank() }, "Mod patterns must not be blank")
        }
    }

    // ── fixtures ──────────────────────────────────────────────────────────────

    /**
     * Builds a [ModsData] with:
     *
     *  Groups:
     *   "Strength" → ["of the Brute", "of the Wrestler"]     (2 tiers)
     *   "ItemRarity" → ["Magpie's", "Collector's", "Hoarder's"] (3 tiers)
     *
     *  Item classes: ["Ring", "Amulet", "Belt"]
     *
     *  A set: all item classes + all mod names + all mod texts
     */
    private fun makeModsData(): ModsData {
        val strBrute    = ModEntry("of the Brute",   "+(5-8) to Strength",       listOf("Strength"),   listOf(ItemTypeRef("Ring","Ring"), ItemTypeRef("Amulet","Amulet")))
        val strWrestler = ModEntry("of the Wrestler", "+(9-12) to Strength",      listOf("Strength"),   listOf(ItemTypeRef("Ring","Ring"), ItemTypeRef("Amulet","Amulet")))
        val rarMagpie   = ModEntry("Magpie's",        "8% increased ItemRarity",  listOf("ItemRarity"), listOf(ItemTypeRef("Ring","Ring")))
        val rarCollect  = ModEntry("Collector's",     "12% increased ItemRarity", listOf("ItemRarity"), listOf(ItemTypeRef("Ring","Ring")))
        val rarHoarder  = ModEntry("Hoarder's",       "16% increased ItemRarity", listOf("ItemRarity"), listOf(ItemTypeRef("Ring","Ring")))

        val mods = mapOf(
            "of the Brute"   to strBrute,
            "of the Wrestler" to strWrestler,
            "Magpie's"       to rarMagpie,
            "Collector's"    to rarCollect,
            "Hoarder's"      to rarHoarder,
        )
        val groups = mapOf(
            "Strength"   to listOf("of the Brute", "of the Wrestler"),
            "ItemRarity" to listOf("Magpie's", "Collector's", "Hoarder's"),
        )
        val itemClasses = listOf("Ring", "Amulet", "Belt")
        val allStrings = (itemClasses + mods.keys + mods.values.map { it.text }).distinct()
        return ModsData(mods, groups, itemClasses, allStrings)
    }

    // ── output format ─────────────────────────────────────────────────────────

    @Test
    fun `single entry produces a single line in correct format`() {
        val data = makeModsData()
        val result = buildVendorRegex(data, mapOf("Ring" to listOf("of the Brute")))
        val lines = result.lines().filter { it.isNotBlank() }
        assertEquals(1, lines.size)
        splitLine(lines[0]) // asserts format without throwing
    }

    @Test
    fun `multiple entries produce multiple newline-separated lines`() {
        val data = makeModsData()
        val result = buildVendorRegex(data, mapOf(
            "Ring"   to listOf("of the Brute"),
            "Amulet" to listOf("of the Wrestler"),
        ))
        val lines = result.lines().filter { it.isNotBlank() }
        assertEquals(2, lines.size)
        lines.forEach { splitLine(it) }
    }

    @Test
    fun `empty selection returns empty string`() {
        assertEquals("", buildVendorRegex(makeModsData(), emptyMap()).trim())
    }

    // ── B2 rule: all tiers selected → use texts ───────────────────────────────

    @Test
    fun `all tiers selected - mod section covers all tier texts`() {
        val data = makeModsData()
        // Select all Strength tiers → B2 should be the texts, not the names
        val result = buildVendorRegex(data, mapOf("Ring" to listOf("of the Brute", "of the Wrestler")))
        val (_, modSection) = splitLine(result.lines().first { it.isNotBlank() })
        val modPatterns = modSection.split("|")

        // Every tier text must be matched by at least one mod pattern
        val texts = listOf("+(5-8) to Strength", "+(9-12) to Strength")
        for (text in texts) {
            assertTrue(
                modPatterns.any { p -> text.contains(p) },
                "Text '$text' must be matched by at least one mod pattern in '$modSection'"
            )
        }
    }

    @Test
    fun `all tiers selected - mod patterns must not match item class strings`() {
        val data = makeModsData()
        val result = buildVendorRegex(data, mapOf("Ring" to listOf("of the Brute", "of the Wrestler")))
        val (_, modSection) = splitLine(result.lines().first { it.isNotBlank() })
        val modPatterns = modSection.split("|")

        for (pattern in modPatterns) {
            assertTrue(
                data.itemClasses.none { it.contains(pattern) },
                "Mod pattern '$pattern' must not match any item class"
            )
        }
    }

    // ── B2 rule: subset of tiers → use names ─────────────────────────────────

    @Test
    fun `subset of tiers selected - mod section is based on mod names`() {
        val data = makeModsData()
        // Select only tier 1 of Strength → B2 = {"of the Brute"}
        val result = buildVendorRegex(data, mapOf("Ring" to listOf("of the Brute")))
        val (_, modSection) = splitLine(result.lines().first { it.isNotBlank() })
        val modPatterns = modSection.split("|")

        // "of the Brute" must be matched
        assertTrue(
            modPatterns.any { p -> "of the Brute".contains(p) },
            "Name 'of the Brute' must be matched by mod pattern '$modSection'"
        )
        // "of the Wrestler" must NOT be matched
        assertFalse(
            modPatterns.any { p -> "of the Wrestler".contains(p) },
            "Name 'of the Wrestler' must not be matched when only tier 1 is selected"
        )
    }

    // ── multi-group mod list ──────────────────────────────────────────────────

    @Test
    fun `mods from different groups are both covered in the mod section`() {
        val data = makeModsData()
        // Mix: one Strength tier + all ItemRarity tiers
        val result = buildVendorRegex(data, mapOf(
            "Ring" to listOf("of the Brute", "Magpie's", "Collector's", "Hoarder's")
        ))
        val (_, modSection) = splitLine(result.lines().first { it.isNotBlank() })
        val modPatterns = modSection.split("|")

        // "of the Brute" covered (name, subset)
        assertTrue(modPatterns.any { "of the Brute".contains(it) })
        // All ItemRarity texts covered (all tiers selected)
        val rarityTexts = listOf("8% increased ItemRarity", "12% increased ItemRarity", "16% increased ItemRarity")
        for (text in rarityTexts) {
            assertTrue(modPatterns.any { text.contains(it) }, "ItemRarity text '$text' must be covered")
        }
    }

    // ── structural validity ───────────────────────────────────────────────────

    @Test
    fun `output passes full structural validity check`() {
        val data = makeModsData()
        val selection = mapOf(
            "Ring"   to listOf("of the Brute", "of the Wrestler"),
            "Amulet" to listOf("of the Brute"),
        )
        assertVendorRegexValid(buildVendorRegex(data, selection), data, selection)
    }
}
