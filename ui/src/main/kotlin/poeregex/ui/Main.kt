package poeregex.ui

import eu.niton.lifewire.runLifewire
import poeregex.loadGameData
import kotlin.io.path.Path

fun main(args: Array<String>) {
    val modsData = loadGameData(Path("data"))
    runLifewire(args) { cx ->
        App(cx, modsData)
    }
}
