package io.github.cuongdev.debuggerkit.devtools

import androidx.annotation.StringRes
import io.github.cuongdev.debuggerkit.R

/** Shortcuts the dev panel can show. [needsShell] ones require Shizuku or root. */
enum class DevAction(val id: String, @StringRes val title: Int, @StringRes val sub: Int, val needsShell: Boolean) {
    ANIMATIONS("anim", R.string.dev_anim, R.string.dev_anim_sub, false),
    SHOW_TAPS("taps", R.string.dev_taps, R.string.dev_taps_sub, true),
    POINTER("pointer", R.string.dev_pointer, R.string.dev_pointer_sub, true),
    KEEP_AWAKE("awake", R.string.dev_stay_awake, R.string.dev_stay_awake_sub, false),
    DONT_KEEP("dka", R.string.dev_dont_keep, R.string.dev_dont_keep_sub, false),
    FONT_SCALE("font", R.string.dev_font, R.string.dev_font_sub, false),
    ADB_CONNECT("adbc", R.string.dev_adb_connect, R.string.dev_adb_connect_sub, false),
    DEV_OPTIONS("devopts", R.string.developer_options, R.string.dev_devopts_sub, false),
    LAYOUT_BOUNDS("bounds", R.string.dev_bounds, R.string.dev_bounds_sub, true),
    OVERDRAW("overdraw", R.string.dev_overdraw, R.string.dev_overdraw_sub, true),
    GPU_BARS("gpubars", R.string.dev_gpubars, R.string.dev_gpubars_sub, true),
    DARK_MODE("dark", R.string.dev_dark, R.string.dev_dark_sub, true),
    DEMO_MODE("demo", R.string.dev_demo, R.string.dev_demo_sub, true);

    companion object {
        fun byId(id: String): DevAction? = entries.firstOrNull { it.id == id }
    }
}

enum class Preset(@StringRes val title: Int, @StringRes val summary: Int) {
    UI_TEST(R.string.preset_ui, R.string.preset_ui_sub),
    SCREENSHOT(R.string.preset_shot, R.string.preset_shot_sub),
    PERF(R.string.preset_perf, R.string.preset_perf_sub),
}

data class PanelItem(
    val action: DevAction,
    val value: String,
    val active: Boolean,
    val available: Boolean,
)

data class Outcome(val message: String?, val closePanel: Boolean = false)
