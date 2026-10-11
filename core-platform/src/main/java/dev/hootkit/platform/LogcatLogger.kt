package dev.hootkit.platform

import android.util.Log
import dev.hootkit.core.LogLevel
import dev.hootkit.core.Logger

class LogcatLogger : Logger {
    override fun log(level: LogLevel, target: String, message: String) {
        // Core's logging contract excludes credentials and full transaction contents.
        runCatching {
            val priority = when (level) {
                LogLevel.ERROR -> Log.ERROR
                LogLevel.WARN -> Log.WARN
                LogLevel.INFO -> Log.INFO
                LogLevel.DEBUG -> Log.DEBUG
                LogLevel.TRACE -> Log.VERBOSE
            }
            Log.println(priority, "HootKitCore", "[$target] $message")
        }
    }
}
