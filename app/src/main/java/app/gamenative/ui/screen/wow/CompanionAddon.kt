package app.gamenative.ui.screen.wow

import android.content.Context
import app.gamenative.ui.screen.wow.WowClientDownloader.FLAVOR_DIR
import timber.log.Timber
import java.io.File

/**
 * Installs the bundled WFCompanion in-game addon into the WoW install so the
 * companion screen has data to read. The addon ships in assets under
 * `companion-addon/WFCompanion/` and is copied to
 * `<gameRoot>/<flavor>/Interface/AddOns/WFCompanion/` on launch.
 */
object CompanionAddon {
    private const val ASSET_DIR = "companion-addon/WFCompanion"
    private const val ADDON_NAME = "WFCompanion"
    private val FILES = listOf("WFCompanion.toc", "WFCompanion.lua")

    fun install(context: Context, gameRoot: File) {
        val target = File(gameRoot, "$FLAVOR_DIR/Interface/AddOns/$ADDON_NAME")
        if (!target.exists() && !target.mkdirs()) {
            Timber.w("Companion: could not create %s", target.path)
            return
        }
        FILES.forEach { name ->
            val bytes = context.assets.open("$ASSET_DIR/$name").use { it.readBytes() }
            val out = File(target, name)
            // Rewrite only when content changed so player edits to data aren't clobbered needlessly.
            if (!out.exists() || !out.readBytes().contentEquals(bytes)) {
                out.writeBytes(bytes)
            }
        }
        Timber.i("Companion: addon installed at %s", target.path)
    }
}
