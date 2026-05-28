package poeregex.ui

import eu.niton.lifewire.runLifewire
import poeregex.loadModsData
import kotlin.io.path.Path

fun main(args: Array<String>) {
    val modsData = loadModsData(Path("data"))
    runLifewire(args) { cx ->
        App(cx, modsData)
    }
}
