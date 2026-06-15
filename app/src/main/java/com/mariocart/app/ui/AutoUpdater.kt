package com.mariocart.app.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.mariocart.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

object AutoUpdater {

    private const val RELEASES_API =
        "https://api.github.com/repos/ashtonhardy555-stack/I-m-done/releases/latest"

    suspend fun checkAndPrompt(context: Context) = withContext(Dispatchers.IO) {
        try {
            val json = URL(RELEASES_API).readText()
            val obj = JSONObject(json)
            val latestTag   = obj.optString("tag_name", "").trimStart('v')
            val downloadUrl = obj.optString("html_url", "")

            if (latestTag.isNotEmpty() && isNewer(latestTag, BuildConfig.VERSION_NAME)) {
                withContext(Dispatchers.Main) {
                    AlertDialog.Builder(context)
                        .setTitle("Update Available")
                        .setMessage("Version $latestTag is ready. Update now?")
                        .setPositiveButton("Download") { _, _ ->
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
                            )
                        }
                        .setNegativeButton("Later", null)
                        .show()
                }
            }
        } catch (_: Exception) { /* silent — no network or no release yet */ }
    }

    private fun isNewer(latest: String, current: String): Boolean {
        val l = latest.split(".").mapNotNull { it.toIntOrNull() }
        val c = current.split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(l.size, c.size)) {
            val lv = l.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (lv != cv) return lv > cv
        }
        return false
    }
}
