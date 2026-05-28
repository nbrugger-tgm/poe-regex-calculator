package poeregex.ui

import eu.niton.lifewire.ktx.Event
import eu.niton.lifewire.ktx.For
import eu.niton.lifewire.ktx.If
import eu.niton.lifewire.ktx.component
import eu.niton.lifewire.ktx.tags.*
import eu.niton.lifewire.signaling.invoke
import eu.nitonfx.signaling.api.Context
import eu.nitonfx.signaling.api.MapSignal
import eu.nitonfx.signaling.api.Signal
import poeregex.ModEntry
import poeregex.ModsData
import poeregex.buildVendorRegex
import poeregex.replaceRanges
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.time.Duration
import kotlin.time.measureTime

fun BodyContent.App(cx: Context, modsData: ModsData) = component {
    val viewedItemType = cx.createNullSignal<String?>()
    val selection = cx.createMapSignal<String, List<String>>()

    fun toggleMod(itemType: String, modName: String) {
        val current = selection.getOrDefault(itemType, emptyList())
        val updated = if (modName in current) current - modName else current + modName
        selection[itemType] = updated
    }

    script(src = { "https://cdn.jsdelivr.net/npm/@tailwindcss/browser@4" }) {}

    div(`class` = { "flex h-screen font-sans text-gray-800" }) {
        ItemTypePanel(modsData, viewedItemType, selection)
        ModPanel(cx, modsData, viewedItemType, selection, ::toggleMod)
        RegexPanel(cx, modsData, selection)
    }
}

// ── left panel: item type list ────────────────────────────────────────────────

private fun DivContent.ItemTypePanel(
    modsData: ModsData,
    viewedItemType: Signal<String?>,
    selection: MapSignal<String, List<String>>,
) = component {
    div(`class` = { "w-56 border-r border-gray-200 overflow-y-auto flex-shrink-0 flex flex-col" }) {
        div(`class` = { "px-3 py-2 border-b border-gray-200" }) {
            span(`class` = { "font-semibold text-sm" }) { +"Item Types" }
        }
        For(elements = { modsData.itemClasses.sorted() }) { itemType ->
            ItemTypeCard(itemType, selection, selected = { viewedItemType() == itemType }, select = {
                viewedItemType(itemType)
            })
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
    modsData: ModsData,
    viewedItemType: Signal<String?>,
    selection: MapSignal<String, List<String>>,
    toggleMod: (String, String) -> Unit,
) = component {
    val expandedGroups = cx.createSetSignal<String>()
    div(`class` = { "flex-1 overflow-y-auto" }) {
        // Outer component: re-renders when viewedItemType changes.
        // Contains the search bar (re-inserted → autoFocus) and a nested component for the list.
        If(condition = { viewedItemType() == null }) {
            div(`class` = { "text-gray-400 text-sm mt-16 text-center" }) {
                +"Select an item type on the left"
            }
        }.Else {
            ModGroupView(cx, { viewedItemType()!! }, modsData, selection, toggleMod)
        }
    }
}

private fun DivContent.ModGroupView(
    cx: Context,
    viewedItemType: ()->String,
    modsData: ModsData,
    selection: MapSignal<String, List<String>>,
    toggleMod: (String, String) -> Unit
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
        ModGroupList(cx, { viewedItemType() }, modsData, query, selection, toggleMod)
    }
}

private fun DivContent.ModGroupList(
    cx: Context,
    viewedItemType: () -> String,
    modsData: ModsData,
    query: () -> String,
    selection: MapSignal<String, List<String>>,
    toggleMod: (String, String) -> Unit
) = component {
    val allGroups = cx.createMemo {
        modsData.mods.values
            .filter { mod -> mod.itemTypes.any { it.id == viewedItemType() } }
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
        }
            .sortedBy { it.key }
    }
    If (condition = { filteredGroups().isEmpty() }) {
        div(`class` = { "text-gray-400 text-sm text-center mt-8" }) {
            +{ "No groups match \"${query()}\"" }
        }
    }.Else {
        For({ filteredGroups() }) { (groupId, groupMods) ->
            val allGroupModNames = groupMods.map { it.name }
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
                For({groupMods.sortedBy { it.name }}) { mod ->
                    ModRow(
                        mod = mod,
                        selected ={mod.name in selection.getOrDefault(viewedItemType(), emptyList())},
                        onSelect ={toggleMod(viewedItemType(), mod.name)}
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
    selected: ()->Boolean,
    onSelect: ()-> Unit
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
    modsData: ModsData,
    selection: MapSignal<String, List<String>>,
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
            RegexDisplay(cx,selection, modsData)
        }
    }
}

private fun DivContent.RegexDisplay(
    cx: Context,
    selection: MapSignal<String, List<String>>,
    modsData: ModsData
) = component {
    val activeSel = selection.keySetSignal().map { selection[it]?:listOf() }
    cx.createEffect {
        println("Active selection changed : $activeSel")
    }
    val regex = cx.createNullSignal<String>()
    val lastRegexTime = cx.createSignal(Duration.ZERO)
    cx.createEffect {
        val mods = selection.filter { (k, v) -> v.isNotEmpty() }
        regex.set(null)
        CompletableFuture.runAsync {
            val regexAndTime =  run {
                lateinit var regex: String
                val time = measureTime {
                    regex = try {
                        buildVendorRegex(modsData, mods)
                    } catch (e: Exception) {
                        "Error: ${e.message}"
                    }
                }
                regex to time
            }
            lastRegexTime.set(regexAndTime.second)
            regex.set(regexAndTime.first)
        }
    }
    If ({ activeSel.any { it.isNotEmpty() } }) {
        If({regex() == null}) {
            +"Calculating regex ..."
        }.Else {
            pre(`class` = { "text-xs bg-gray-50 p-3 rounded border border-gray-200 whitespace-pre-wrap break-all font-mono leading-relaxed" }) {
                +{ regex() }
            }
            span(`class`={"text-xs text-gray-400"}) {
                +"Regex Calculation time: "
                span(`class`= {"text-gray-300"}) { +{lastRegexTime().toString()} }
            }
        }
    }.Else {
        span(`class` = { "text-gray-400 text-sm" }) { +"Select mods to generate a regex." }
    }
}

