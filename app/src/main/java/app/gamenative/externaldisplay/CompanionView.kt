package app.gamenative.externaldisplay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import androidx.core.content.ContextCompat
import app.gamenative.R
import app.gamenative.ui.screen.wow.GamePath
import com.winlator.xserver.XKeycode
import com.winlator.xserver.XServer
import org.json.JSONObject
import timber.log.Timber
import java.io.File

/**
 * Bottom-screen companion for the Thor. Hosts a WebView that renders the bundled
 * companion UI (character panel fed by the in-game addon, plus static reference
 * tabs), with a floating keyboard toggle so the on-screen keyboard stays reachable
 * exactly like [HybridInputLayout] offers it.
 */
@SuppressLint("SetJavaScriptEnabled")
class CompanionView(
    context: Context,
    private val xServer: XServer,
) : FrameLayout(context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val dataSource = CompanionDataSource(GamePath.load(context))

    private var pageReady = false
    private var pendingJson: String? = null

    private val webView = WebView(context).apply {
        layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        setBackgroundColor(ContextCompat.getColor(context, R.color.external_display_surface_background))
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
        addJavascriptInterface(Bridge(), "WFHost")
    }

    private val keyboardView = ExternalOnScreenKeyboardView(context, xServer).apply {
        layoutParams = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { gravity = Gravity.BOTTOM }
        visibility = View.GONE
    }

    private val keyboardToggleButton = ImageButton(context).apply {
        val density = resources.displayMetrics.density
        val sizePx = (56 * density).toInt()
        val marginPx = (16 * density).toInt()
        layoutParams = LayoutParams(sizePx, sizePx).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            setMargins(marginPx, marginPx, marginPx, marginPx)
        }
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ContextCompat.getColor(context, R.color.external_display_key_background))
        }
        setImageResource(R.drawable.icon_keyboard)
        setColorFilter(ContextCompat.getColor(context, R.color.external_display_key_color))
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setPadding(marginPx / 2, marginPx / 2, marginPx / 2, marginPx / 2)
        setOnClickListener { toggleKeyboard() }
    }

    private val dataListener: (String) -> Unit = { json -> pushToPage(json) }

    init {
        addView(webView)
        addView(keyboardView)
        addView(keyboardToggleButton)
        keyboardView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateToggleButtonPosition()
        }
        webView.loadUrl("file:///android_asset/companion/index.html")
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        dataSource.addListener(dataListener)
        dataSource.start()
    }

    override fun onDetachedFromWindow() {
        dataSource.stop()
        dataSource.removeListener(dataListener)
        super.onDetachedFromWindow()
    }

    private fun pushToPage(json: String) {
        mainHandler.post {
            if (!pageReady) {
                pendingJson = json
                return@post
            }
            val escaped = JSONObject.quote(json)
            webView.evaluateJavascript("window.__wfUpdate && window.__wfUpdate($escaped);", null)
        }
    }

    /**
     * Fires the keybind for a WoW action slot so a tap on the companion hotbar
     * activates the spell/ability/item bound there. [binding] is a WoW binding
     * string such as "1", "SHIFT-1", "CTRL-F2" — the hotkey the addon reports for
     * the slot, or the default bar key. The press is live regardless of how fresh
     * the slot labels are.
     */
    private fun pressBinding(binding: String) {
        val parts = binding.uppercase().split("-")
        val key = keycodeFor(parts.lastOrNull()) ?: return
        val mods = parts.dropLast(1).mapNotNull { modifierFor(it) }
        mainHandler.post {
            mods.forEach { xServer.injectKeyPress(it) }
            xServer.injectKeyPress(key)
            // WoW needs a perceptible hold to register the action; release shortly after.
            mainHandler.postDelayed({
                xServer.injectKeyRelease(key)
                mods.asReversed().forEach { xServer.injectKeyRelease(it) }
            }, KEY_HOLD_MS)
        }
    }

    private fun modifierFor(token: String): XKeycode? = when (token) {
        "SHIFT" -> XKeycode.KEY_SHIFT_L
        "CTRL" -> XKeycode.KEY_CTRL_L
        "ALT" -> XKeycode.KEY_ALT_L
        else -> null
    }

    private fun keycodeFor(token: String?): XKeycode? = when (token) {
        "1" -> XKeycode.KEY_1; "2" -> XKeycode.KEY_2; "3" -> XKeycode.KEY_3
        "4" -> XKeycode.KEY_4; "5" -> XKeycode.KEY_5; "6" -> XKeycode.KEY_6
        "7" -> XKeycode.KEY_7; "8" -> XKeycode.KEY_8; "9" -> XKeycode.KEY_9
        "0" -> XKeycode.KEY_0; "-" -> XKeycode.KEY_MINUS; "=" -> XKeycode.KEY_EQUAL
        "F1" -> XKeycode.KEY_F1; "F2" -> XKeycode.KEY_F2; "F3" -> XKeycode.KEY_F3
        "F4" -> XKeycode.KEY_F4; "F5" -> XKeycode.KEY_F5; "F6" -> XKeycode.KEY_F6
        "F7" -> XKeycode.KEY_F7; "F8" -> XKeycode.KEY_F8; "F9" -> XKeycode.KEY_F9
        "F10" -> XKeycode.KEY_F10; "F11" -> XKeycode.KEY_F11; "F12" -> XKeycode.KEY_F12
        else -> if (token != null && token.length == 1 && token[0] in 'A'..'Z') {
            runCatching { XKeycode.valueOf("KEY_${token}") }.getOrNull()
        } else {
            null
        }
    }

    private fun toggleKeyboard() {
        val show = keyboardView.visibility != View.VISIBLE
        keyboardView.visibility = if (show) View.VISIBLE else View.GONE
        post { updateToggleButtonPosition() }
    }

    private fun updateToggleButtonPosition() {
        keyboardToggleButton.translationY = if (keyboardView.visibility == View.VISIBLE) {
            -keyboardView.height.toFloat()
        } else {
            0f
        }
    }

    /** Exposed to the WebView as `WFHost`. */
    private inner class Bridge {
        /** The page calls this once its DOM is ready so we can flush the first payload. */
        @JavascriptInterface
        fun ready() {
            mainHandler.post {
                pageReady = true
                val last = dataSource.latestJson() ?: pendingJson
                if (last != null) pushToPage(last)
            }
        }

        /** Force an immediate re-read of the SavedVariables file. */
        @JavascriptInterface
        fun refresh() {
            dataSource.pollNow()
        }

        /** Tap on a hotbar slot: fire its keybind in-game. */
        @JavascriptInterface
        fun pressSlot(binding: String?) {
            if (!binding.isNullOrBlank()) pressBinding(binding)
        }
    }

    companion object {
        private const val KEY_HOLD_MS = 60L
    }
}

/**
 * Watches the WoW addon's SavedVariables file and emits the JSON blob it writes.
 *
 * The companion addon encodes its state as a single quoted Lua string assigned to
 * `WFCompanionJSON`, so no Lua-table parser is needed here: we extract the string
 * literal and unescape it back into the JSON the WebView consumes. SavedVariables
 * only flush on `/reload` or logout on Classic clients, so this polls for mtime
 * changes rather than assuming a push.
 */
private class CompanionDataSource(gameRoot: String) {

    private val savedVariablesRoot = File(gameRoot, "_forever_/WTF/Account")
    private val handler = Handler(Looper.getMainLooper())
    private val listeners = mutableSetOf<(String) -> Unit>()

    @Volatile private var latest: String? = null
    private var lastModified = 0L
    private var running = false

    private val poll = object : Runnable {
        override fun run() {
            readIfChanged()
            if (running) handler.postDelayed(this, POLL_MS)
        }
    }

    fun addListener(l: (String) -> Unit) { listeners.add(l) }
    fun removeListener(l: (String) -> Unit) { listeners.remove(l) }
    fun latestJson(): String? = latest

    fun start() {
        if (running) return
        running = true
        handler.post(poll)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(poll)
    }

    fun pollNow() {
        lastModified = 0L
        handler.post { readIfChanged() }
    }

    private fun currentFile(): File? {
        val accounts = savedVariablesRoot.listFiles { f -> f.isDirectory } ?: return null
        // Account-wide SavedVariables: <Account>/<ACCOUNT>/SavedVariables/WFCompanion.lua
        return accounts
            .map { File(it, "SavedVariables/$SV_FILE") }
            .filter { it.isFile }
            .maxByOrNull { it.lastModified() }
    }

    private fun readIfChanged() {
        val file = currentFile() ?: return
        val mtime = file.lastModified()
        if (mtime == lastModified) return
        lastModified = mtime
        val json = runCatching { extractJson(file.readText()) }.getOrElse {
            Timber.w(it, "Companion: failed to read %s", file.path)
            null
        } ?: return
        latest = json
        listeners.toList().forEach { it(json) }
    }

    private fun extractJson(raw: String): String? {
        val marker = "WFCompanionJSON"
        val eq = raw.indexOf(marker).takeIf { it >= 0 }
            ?.let { raw.indexOf('=', it + marker.length) } ?: return null
        val open = raw.indexOf('"', eq).takeIf { it >= 0 } ?: return null
        val sb = StringBuilder()
        var i = open + 1
        while (i < raw.length) {
            val c = raw[i]
            if (c == '\\' && i + 1 < raw.length) {
                when (val n = raw[i + 1]) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'r' -> sb.append('\r')
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    else -> sb.append(n)
                }
                i += 2
            } else if (c == '"') {
                return sb.toString()
            } else {
                sb.append(c)
                i++
            }
        }
        return null
    }

    companion object {
        private const val POLL_MS = 3000L
        private const val SV_FILE = "WFCompanion.lua"
    }
}
