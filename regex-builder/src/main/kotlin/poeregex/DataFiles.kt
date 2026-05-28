package poeregex

import java.net.URI
import java.nio.file.Path
import kotlin.io.path.*

internal const val MODS_FILENAME       = "mods.json"
internal const val BASE_ITEMS_FILENAME = "base_items.json"

private const val MODS_URL       = "https://repoe-fork.github.io/poe2/mods.json"
private const val BASE_ITEMS_URL = "https://repoe-fork.github.io/poe2/base_items.json"

/**
 * Ensures [filename] exists under [projectDir], downloading it from [url] if absent.
 *
 * @return The path to the (now-existing) file.
 */
internal fun ensureFile(projectDir: Path, filename: String, url: String): Path {
    val target = projectDir.resolve(filename)
    if (!target.exists()) {
	target.parent.createDirectories()
	target.createFile()
	val content = URI(url).toURL().readText()
        target.writeText(content)
    }
    return target
}

/**
 * Ensures both data files exist under [projectDir], downloading them if absent.
 *
 * @return Pair of (modsFilePath, baseItemsFilePath).
 */
internal fun ensureDataFiles(projectDir: Path): Pair<Path, Path> = Pair(
    ensureFile(projectDir, MODS_FILENAME,       MODS_URL),
    ensureFile(projectDir, BASE_ITEMS_FILENAME, BASE_ITEMS_URL),
)
