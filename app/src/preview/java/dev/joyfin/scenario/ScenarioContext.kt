package dev.joyfin.scenario

import android.content.Context
import android.content.ContextWrapper
import java.io.File

/** Separate process + directories + preferences: real accounts and subtitle indexes stay unread. */
internal class ScenarioContext(base: Context) : ContextWrapper(base) {
    override fun getApplicationContext(): Context = this
    override fun getSharedPreferences(name: String, mode: Int) =
        baseContext.getSharedPreferences("ui_scenario_$name", mode)
    override fun getFilesDir() = File(baseContext.filesDir, "ui-scenario").apply { mkdirs() }
    override fun getCacheDir() = File(baseContext.cacheDir, "ui-scenario").apply { mkdirs() }
    override fun getNoBackupFilesDir() = File(baseContext.noBackupFilesDir, "ui-scenario").apply { mkdirs() }
}
