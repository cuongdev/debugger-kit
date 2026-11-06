package io.github.cuongdev.debuggerkit.core

import android.provider.Settings

/** Setting keys the app reads or writes. Nothing outside this list is touched. */
object Keys {
    const val ADB_ENABLED = Settings.Global.ADB_ENABLED
    const val ADB_WIFI_ENABLED = "adb_wifi_enabled"
    const val DEV_SETTINGS = Settings.Global.DEVELOPMENT_SETTINGS_ENABLED
    const val STAY_ON = Settings.Global.STAY_ON_WHILE_PLUGGED_IN
    const val WINDOW_ANIM = Settings.Global.WINDOW_ANIMATION_SCALE
    const val TRANSITION_ANIM = Settings.Global.TRANSITION_ANIMATION_SCALE
    const val ANIMATOR_DURATION = Settings.Global.ANIMATOR_DURATION_SCALE
    const val ALWAYS_FINISH = Settings.Global.ALWAYS_FINISH_ACTIVITIES
    const val FONT_SCALE = Settings.System.FONT_SCALE
    const val SHOW_TOUCHES = "show_touches"
    const val POINTER_LOCATION = "pointer_location"
}
