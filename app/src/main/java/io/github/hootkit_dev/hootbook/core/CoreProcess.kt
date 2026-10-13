package io.github.hootkit_dev.hootbook.core

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import java.io.File

object CoreProcess {
    // If process identity cannot be determined, fail closed instead of opening a second database handle.
    fun isCrashProcess(processName: String?): Boolean = processName.isNullOrBlank() || processName.endsWith(":crash")

    fun isCrashProcess(context: Context): Boolean {
        val processName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            manager?.runningAppProcesses?.firstOrNull { it.pid == Process.myPid() }?.processName
        }
        val resolvedName = processName?.takeIf { it.isNotBlank() } ?: runCatching {
            File("/proc/self/cmdline").inputStream().use { input ->
                input.readBytes().toString(Charsets.UTF_8).substringBefore('\u0000').trim()
            }
        }.getOrNull()
        return isCrashProcess(resolvedName)
    }
}
