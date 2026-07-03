package poeregex.ui

import eu.niton.lifewire.ktx.Event
import eu.niton.lifewire.ktx.For
import eu.niton.lifewire.ktx.If
import eu.niton.lifewire.ktx.component
import eu.niton.lifewire.ktx.tags.*
import eu.niton.lifewire.signaling.invoke
import eu.nitonfx.signaling.api.Context
import eu.nitonfx.signaling.api.ListSignal
import eu.nitonfx.signaling.api.MapSignal
import eu.nitonfx.signaling.api.Signal
import kotlinx.serialization.json.Json
import poeregex.*
import poeregex.PatternSolver.Searchable
import poeregex.ui.RegexType.SelectionBasis.ITEM_CLASS
import java.nio.file.Paths
import java.util.*
import java.util.function.Supplier
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Duration
import kotlin.time.measureTime


private val settings = Paths.get("settings.json")

fun BodyContent.App(cx: Context, gameData: GameData) = component {
    val viewedItemType = cx.createNullSignal<String?>()
    val regexType = cx.createSignal(CAMPAIGN_VENDOR_CONFIG)
    val modsData = cx.createMemo {
        regexType()?.let { gameData.filter(it.itemFilter, it.modFilter) }?: ModsData(mapOf(),mapOf(),mapOf())
    }
    val selection = cx.createSignal(
        if (settings.exists()) Json.decodeFromString<Map<String, List<String>>>(settings.readText())
        else mapOf()
    )

    cx.createEffect {
        if (!settings.exists()) settings.createFile()
        val clonedMap = mutableMapOf<String, List<String>>()
        clonedMap.putAll(selection)
        settings.writeText(Json.encodeToString(clonedMap))
    }

    fun toggleMod(itemType: String, modName: String) {
        val current = selection.getOrDefault(itemType, emptyList())
        val updated = if (modName in current) current - modName else current + modName
        selection[itemType] = updated
    }

    script(src = { "https://cdn.jsdelivr.net/npm/@tailwindcss/browser@4" }) {}

    div(`class` = { "flex flex-col h-screen font-sans text-gray-800" }) {
        div(`class` = { "flex px-3 py-2 border-b border-gray-300 sticky" }) {
            Header(cx, setRegexType = {regexType.set(it)})
        }
        div(`class` = { "flex grow" }) {
            ItemTypePanel({ modsData() }, viewedItemType, selection, regexType)
            ModPanel(cx, { modsData() }, viewedItemType, selection, ::toggleMod, regexType)
            RegexPanel(cx, { modsData() }, selection,regexType)
        }
    }
}

private fun DivContent.Header(cx: Context,setRegexType: (RegexType)->Unit) {
    val regexTypes = cx.createSignal(REGEX_TYPE_CONFIGURATIONS)

    span(`class` = { "text-2xl" }) {
        +"POE Regex Calculator"
    }
    div(`class` = { "grow" }) {}//placeholder
    div(`class` = {"text-sm flex gap-2 item-center"}) {
        +"Regex Typ"
        Dropdown(
            cx,
            onSelect = { setRegexType(it) },
            options = regexTypes
        ) {
            +{ it.name }
        }
    }
}

private fun <T> DivContent.Dropdown(
    cx: Context,
    onSelect: (T) -> Unit,
    options: ListSignal<T>,
    allowNull:Boolean = false,
    body: DivContent.(T) -> Unit,
) {
    val selected = cx.createNullSignal<T>()
    val open = cx.createSignal(false)
    fun select(value: T) {
        selected.set(value)
        open.set(false)
        onSelect(value)
    }
    cx.createEffect {
        if(!allowNull && selected() == null && !options.isNotEmpty())
            select(options.first())
    }
    div(`class` = { "relative min-w-36" }) {
        div(`class`={"p-2 py-1 border border-gray-200 ${if (!open()) "rounded" else "rounded-t"}"}, onClick = { open.update { !it } }) {
            If({ selected() == null }) {
                +"Auswählen"
            }.Else {
                body(selected()!!)
            }
        }
        If({ open() }) {
            div(`class` = {"absolute border-b border-gray-200 bg-white border-x border-b rounded-b min-w-36"}) {
                For(options) { option ->
                    div(
                        `class` = { "p-2 py-1 ${if (selected() === option) "bg-blue-200" else "hover:bg-gray-200"}" },
                        onClick = { select(option) }
                    ) {
                        body(option)
                    }
                }
            }
        }
    }
}

data class DropdownOption<T>(val value: T, val body: DivBody)

private fun DivContent.ItemTypePanel(
    modsData: ()->ModsData,
    viewedItemType: Signal<String?>,
    selection: MapSignal<String, List<String>>,
    regexType: Supplier<RegexType?>
) {
    val elements = {
        when(regexType.get()?.modBasis) {
            null -> emptyList()
            ITEM_CLASS -> modsData().itemClasses.keys.sorted()
            RegexType.SelectionBasis.BASE_TYPE -> modsData().itemClasses.values.flatten().map { it.name }.sorted()
        }
    }
    component {
        div(`class` = { "w-56 border-r border-gray-200 overflow-y-auto flex-shrink-0 flex flex-col" }) {
            div(`class` = { "px-3 py-2 border-b border-gray-200" }) {
                span(`class` = { "font-semibold text-sm" }) { +"Item Types" }
            }
            For(elements = elements) { itemType ->
                ItemTypeCard(itemType, selection, selected = { viewedItemType() == itemType }, select = {
                    viewedItemType(itemType)
                })
            }
        }
    }
}

private fun DivContent.ItemTypeCard(
    itemType: String,
    selection: MapSignal<String, List<String>>,
    selected: () -> Boolean,
    select: () -> Unit
) = component {
    div(
        `class` = {
            "cursor-pointer px-3 py-1 text-sm " +
                    if (selected()) "bg-blue-100 font-medium"
                    else "hover:bg-gray-100"
        },
        onClick = {
            select()
        },
    ) {
        +{
            val count = selection.getOrDefault(itemType, emptyList()).size
            if (count > 0) "$itemType ($count)" else itemType
        }
    }
}

// ── center panel: mod list ────────────────────────────────────────────────────

private fun DivContent.ModPanel(
    cx: Context,
    modsData: ()->ModsData,
    viewedItemType: Signal<String?>,
    selection: MapSignal<String, List<String>>,
    toggleMod: (String, String) -> Unit,
    regexType: Supplier<RegexType>
) = component {
    div(`class` = { "flex-1 overflow-y-auto" }) {
        // Outer component: re-renders when viewedItemType changes.
        // Contains the search bar (re-inserted → autoFocus) and a nested component for the list.
        If(condition = { viewedItemType() == null }) {
            div(`class` = { "text-gray-400 text-sm mt-16 text-center" }) {
                +"Select an item type on the left"
            }
        }.Else {
            ModGroupView(cx, { viewedItemType()!! }, modsData, selection, toggleMod, regexType)
        }
    }
}

private fun DivContent.ModGroupView(
    cx: Context,
    viewedItemType: () -> String,
    modsData: ()->ModsData,
    selection: MapSignal<String, List<String>>,
    toggleMod: (String, String) -> Unit,
    regexType: Supplier<RegexType>
) = component {
    val searchQuery = cx.createSignal("")
    val query = { searchQuery().trim().lowercase() }
    div(`class` = { "sticky top-0 bg-white border-b border-gray-200 px-4 py-2 z-10" }) {
        div(`class` = { "font-bold text-base mb-2" }) { +viewedItemType() }
        input(
            `class` = { "w-full border border-gray-300 rounded px-2 py-1 text-sm focus:outline-none focus:border-blue-400" },
            placeholder = { "Search" },
            `value` = { searchQuery() },
            autoFocus = { true },
            onInput = { if (it is Event.Input) searchQuery(it.value) else searchQuery("") },
        )
    }

    // Inner component: re-renders when searchQuery changes (not the input above).
    div(`class` = { "p-4" }) {
        ModGroupList(cx, { viewedItemType() }, modsData, query, selection, toggleMod, regexType)
    }
}

private fun DivContent.ModGroupList(
    cx: Context,
    viewedItemType: () -> String,
    modsData:()-> ModsData,
    query: () -> String,
    selection: MapSignal<String, List<String>>,
    toggleMod: (String, String) -> Unit,
    regexType: Supplier<RegexType>
) = component {
    val allGroups = cx.createMemo {
        modsData().mods.values
            .filter { mod ->
                if(regexType.get().modBasis == RegexType.SelectionBasis.ITEM_CLASS) mod.itemTypes.any { it.id == viewedItemType() }
                else mod.itemBases.any { it.id == viewedItemType() }
            }
            .groupBy { it.groups.firstOrNull() ?: "(other)" }
    }
    val filteredGroups = allGroups.map {
        it.entries.filter { (groupId, groupMods) ->
            val query = query()
            if (query.isEmpty()) return@filter true
            val header = replaceRanges(
                groupMods.minByOrNull { it.name }?.text ?: "", 'X'
            ).lowercase()
            header.contains(query) || groupMods.any {
                it.name.lowercase().contains(query) ||
                        it.text.lowercase().contains(query)
            }
        }.sortedBy { it.key }
    }
    If(condition = { filteredGroups().isEmpty() }) {
        div(`class` = { "text-gray-400 text-sm text-center mt-8" }) {
            +{ "No groups match \"${query()}\"" }
        }
    }.Else {
        For({ filteredGroups() }) { (groupId, groupMods) ->
            val allGroupModNames = groupMods.map { it.id }
            val headerText = {
                replaceRanges(
                    groupMods.minByOrNull { it.name }?.text ?: "", 'X'
                )
            }

            ModGroup(cx, viewedItemType, headerText, selection, allGroupModNames, groupMods, toggleMod)
        }
    }
}

private fun DivContent.ModGroup(
    cx: Context,
    viewedItemType: () -> String,
    headerText: () -> String,
    selection: MapSignal<String, List<String>>,
    allGroupModNames: List<String>,
    groupMods: List<ModEntry>,
    toggleMod: (String, String) -> Unit
) = component {
    val expanded = cx.createSignal(false)
    div(`class` = { "mb-1 rounded border border-gray-200" }) {

        // collapsible header
        div(
            `class` = { "flex items-center justify-between px-3 py-1.5 cursor-pointer select-none hover:bg-gray-50" },
            onClick = { expanded.update { !it } }
        ) {
            span(`class` = { "text-sm font-medium truncate mr-2" }) { +headerText }
            div(`class` = { "flex items-center gap-2 flex-shrink-0" }) {
                span(`class` = { "text-xs text-blue-500" }) {
                    +{
                        val n = selection.getOrDefault(viewedItemType(), emptyList())
                            .count { it in allGroupModNames }
                        if (n > 0) "$n selected" else null
                    }
                }
                span(`class` = { "text-gray-400 text-xs" }) {
                    +{ if (expanded()) "▼" else "▶" }
                }
            }
        }

        // expandable body
        If(condition = { expanded() }) {
            div(`class` = { "border-t border-gray-100" }) {

                // select / deselect all row
                div(`class` = { "px-3 py-1 flex items-center justify-between bg-gray-50" }) {
                    SelectAllModsHeader(selection, viewedItemType, allGroupModNames)
                }
                For({ groupMods.sortedBy { it.name } }) { mod ->
                    ModRow(
                        mod = mod,
                        selected = { mod.id in selection.getOrDefault(viewedItemType(), emptyList()) },
                        onSelect = { toggleMod(viewedItemType(), mod.id) }
                    )
                }
            }

        }
    }
}

private fun DivContent.SelectAllModsHeader(
    selection: MapSignal<String, List<String>>,
    viewedItemType: () -> String,
    allGroupModNames: List<String>
) = component {
    span(`class` = { "text-xs text-gray-400 italic" }) { +"All tiers" }
    button(
        `class` = {
            val current = selection.getOrDefault(viewedItemType(), emptyList())
            val allSel = allGroupModNames.all { it in current }
            "text-xs px-2 py-0.5 rounded border " +
                    if (allSel) "bg-blue-100 border-blue-300 text-blue-700"
                    else "border-gray-300 text-gray-500 hover:bg-gray-50"
        },
        onClick = {
            val current = selection.getOrDefault(viewedItemType(), emptyList())
            val allSel = allGroupModNames.all { it in current }
            val updated =
                if (allSel) current.filter { it !in allGroupModNames }
                else (current + allGroupModNames).distinct()
            selection[viewedItemType()] = updated
        },
    ) {
        +{
            val allSel = allGroupModNames.all {
                it in selection.getOrDefault(viewedItemType(), emptyList())
            }
            if (allSel) "deselect all" else "select all"
        }
    }
}

private fun DivContent.ModRow(
    mod: ModEntry,
    selected: () -> Boolean,
    onSelect: () -> Unit
) = component {
    div(
        `class` = {
            "cursor-pointer px-3 py-1 text-sm border-t border-gray-100 " +
                    if (selected()) "bg-blue-50" else "hover:bg-gray-50"
        },
        onClick = { onSelect() },
    ) {
        +{ "${if (selected()) "✓ " else ""} ${mod.name}  — ${mod.text}" }
    }
}


// ── right panel: regex output ─────────────────────────────────────────────────

private fun DivContent.RegexPanel(
    cx: Context,
    modsData: () -> ModsData,
    selection: MapSignal<String, List<String>>,
    regexType: Supplier<RegexType>,
) {
    div(`class` = { "w-96 border-l border-gray-200 flex-shrink-0 flex flex-col" }) {
        div(`class` = { "px-4 py-2 border-b border-gray-200 flex items-center justify-between" }) {
            span(`class` = { "font-semibold text-sm" }) { +"Vendor Regex" }
            button(
                `class` = { "text-xs px-2 py-1 rounded border border-gray-300 hover:bg-gray-100 text-gray-600" },
                onClick = {
                    selection.keys.toList().forEach { selection[it] = emptyList() }
                },
            ) { +"Clear" }
        }
        div(`class` = { "p-4 flex-1 overflow-y-auto" }) {
            ResultDisplay(cx, selection, modsData, regexType)
        }
    }
}

private sealed interface RegexResult {
    data class Regex(var regex: String) : RegexResult
    data class Conflict(val element: Searchable, val conflicts: List<Searchable>) : RegexResult
}

private fun DivContent.ResultDisplay(
    cx: Context,
    selection: MapSignal<String, List<String>>,
    modsData: ()->ModsData,
    regexType: Supplier<RegexType>
) = component {
    val activeSel = selection.keySetSignal().map { selection[it] ?: listOf() }
    val regex = cx.createNullSignal<RegexResult>()
    val lastRegexTime = cx.createSignal(Duration.ZERO)
    val candidatesProgress = cx.createSignal(0f)
    val reductionProgress = cx.createSignal(0f)
    cx.createEffect {
        val mods = selection.filter { (k, v) -> v.isNotEmpty() }
        if (mods.isEmpty()) return@createEffect
        val modData = modsData().copy()
        val filterByClass = regexType.get().modBasis == RegexType.SelectionBasis.ITEM_CLASS
        val thread = Thread.startVirtualThread {
            regex.set(null)
            candidatesProgress.set(0f)
            reductionProgress.set(0f)
            lastRegexTime.set(measureTime {
                try {
                    regex.set(
                        RegexResult.Regex(
                            buildVendorRegex(
                                modData, mods,
                                filterByClass = filterByClass,
                                candidatesProgressCallback = { progress, total ->
                                    candidatesProgress.set(progress.toFloat() / total.coerceAtLeast(1))
                                },
                                reductionProgressCallback = { progress, total ->
                                    reductionProgress.set(progress.toFloat() / total.coerceAtLeast(1))
                                },
                            ).also {
                                println("Regex calculated:$it")
                            })
                    )
                } catch (e: UnresolvablePatternException) {
                    regex.set(RegexResult.Conflict(e.element, e.collisions))
                }
            })
            null
        }
        cx.cleanup {
            thread.interrupt()
        }
    }

    If({ activeSel.any { it.isNotEmpty() } }) {
        If({ regex() == null }) {
            CalculatingDisplay({ candidatesProgress() }, { reductionProgress() })
        }.Else {
            If({ regex() is RegexResult.Regex }) {
                RegexDisplay({ regex()!! as RegexResult.Regex }, { lastRegexTime() })
            }.Else {
                ConflictDisplay({ regex()!! as RegexResult.Conflict }, { lastRegexTime() }, selection)
            }
        }
    }.Else {
        span(`class` = { "text-gray-400 text-sm" }) { +"Select mods to generate a regex." }
    }
}

private fun DivContent.CalculatingDisplay(
    candidatesProgress: () -> Float,
    reductionProgress: () -> Float,
) = component {
    div(`class` = { "space-y-3" }) {
        span(`class` = { "text-sm text-gray-500" }) { +"Calculating regex ..." }
        ProgressBar("Candidates", candidatesProgress)
        ProgressBar("Reduction", reductionProgress)
    }
}

private fun DivContent.ProgressBar(label: String, progress: () -> Float) {
    val percentage = { "%,.1f".format(Locale.ENGLISH, progress() * 100) }
    div(`class` = { "space-y-1" }) {
        div(`class` = { "flex justify-between text-xs text-gray-400" }) {
            span { +label }
            span { +{ "${percentage()}%" } }
        }
        div(`class` = { "w-full bg-gray-100 rounded-full h-2" }) {
            div(
                `class` = { "bg-blue-400 h-2 rounded-full transition-all" },
                style = { "width: ${percentage()}%" }) {}
        }
    }
}

private fun DivContent.RegexDisplay(
    regex: () -> RegexResult.Regex,
    lastRegexTime: () -> Duration
) {
    pre(`class` = { "text-xs bg-gray-50 p-3 rounded border border-gray-200 whitespace-pre-wrap break-all font-mono leading-relaxed" }) {
        +{ regex().regex }
    }
    span(`class` = { "text-xs text-gray-300" }) {
        +"Regex Calculation time: "
        span(`class` = { "text-gray-400" }) { +{ lastRegexTime().toString() } }
    }
    br()
    span(`class` = { "text-xs text-gray-300" }) {
        +"Size: "
        span(`class` = { if(regex().regex.length<=250) "text-green-300" else "text-red-300" }) { +{ regex().regex.length.toString() } }
        span(`class` = { "text-gray-400" }) {+"/250"}
    }
}

private fun DivContent.ConflictDisplay(
    conflict: () -> RegexResult.Conflict,
    lastRegexTime: () -> Duration,
    selection: MapSignal<String, List<String>>,
) = component {

    // Add to all currently-selected item types where this mod can roll;
    // fall back to all of the mod's item types if none overlap.
    fun addConflictingMod(mod: ModEntry) {
        val relevantTypes = selection.keys
            .filter { itemType -> mod.itemTypes.any { it.id == itemType } }
            .ifEmpty { mod.itemTypes.map { it.id } }
        relevantTypes.forEach { itemType ->
            val current = selection.getOrDefault(itemType, emptyList())
            if (mod.id !in current) selection[itemType] = current + mod.id
        }
    }

    div(`class` = { "space-y-3" }) {
        // Failure message
        p(`class` = { "text-sm font-medium text-red-700" }) {
            +{
                val el = conflict().element
                val label = if (el is ModPattern) "\"${el.mod.name}\"" else "\"${el.searchString}\""
                "$label cannot be matched without also matching:"
            }
        }
        span(`class` = { "text-xs text-gray-400" }) {
            +"(You can click conflicting mods to also select them)"
        }

        div(`class` = { "space-y-1" }) {
            For({ conflict().conflicts }) { conflict ->
                when (conflict) {
                    is ModPattern -> {
                        For({ conflict.mod.itemTypes.filter { selection[it.id]?.isNotEmpty() ?: false } }) { item ->
                            val mod = conflict.mod
                            div(
                                `class` = { "flex flex-row cursor-pointer text-sm rounded-md border bg-red-50 border-red-200 text-red-700 hover:bg-red-100" },
                                onClick = { addConflictingMod(mod) },
                            ) {
                                div(`class` = { "border-r py-1 px-2 bg-orange-50 hover:bg-red-50 rounded-l-md" }) {
                                    +item.name
                                }
                                div(`class` = { "py-1 px-2" }) {
                                    +{ "${mod.name}  — ${mod.text}" }
                                }
                            }
                        }
                    }

                    is ItemPattern -> {
                        div(`class` = { "px-2 py-1 text-xs rounded border bg-gray-50 border-gray-200 text-gray-500" }) {
                            +{ conflict.searchString }
                        }
                    }

                    else -> {
                        div(`class` = { "px-2 py-1 text-xs text-gray-400" }) {
                            +{ conflict.searchString }
                        }
                    }
                }
            }
        }

        span(`class` = { "text-xs text-gray-400" }) {
            +"Calculation time: "
            span(`class` = { "text-gray-300" }) { +{ lastRegexTime().toString() } }
        }
    }
}

