package com.mariocart.app.ui.updates

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mariocart.app.BuildConfig
import com.mariocart.app.ui.theme.Bg
import com.mariocart.app.ui.theme.Bg3
import com.mariocart.app.ui.theme.Red
import com.mariocart.app.ui.theme.TextMuted
import com.mariocart.app.ui.theme.TextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

private data class ReleaseInfo(
    val tagName: String,
    val name: String,
    val body: String,
    val downloadUrl: String,
    val apkUrl: String?,
    val publishedAt: String
)

@Composable
fun UpdatesScreen() {
    val context = LocalContext.current
    var release by remember { mutableStateOf<ReleaseInfo?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val json = URL(
                    "https://api.github.com/repos/ashtonhardy555-stack/I-m-done/releases/latest"
                ).readText()
                val obj = JSONObject(json)
                val tag = obj.optString("tag_name", "")
                val apkAsset: String? = try {
                    val assets = obj.optJSONArray("assets")
                    var url: String? = null
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val a = assets.getJSONObject(i)
                            if (a.optString("name", "").endsWith(".apk")) {
                                url = a.optString("browser_download_url")
                                break
                            }
                        }
                    }
                    url
                } catch (_: Exception) { null }
                release = ReleaseInfo(
                    tagName      = tag,
                    name         = obj.optString("name", tag),
                    body         = obj.optString("body", "No release notes."),
                    downloadUrl  = obj.optString("html_url", ""),
                    apkUrl       = apkAsset,
                    publishedAt  = obj.optString("published_at", "").take(10)
                )
            } catch (_: Exception) {
                error = "Could not check for updates. Check your connection."
            } finally {
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .padding(20.dp)
    ) {
        Text(
            "Updates",
            color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            "Current version: ${BuildConfig.VERSION_NAME}",
            color = TextMuted, fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        when {
            isLoading -> Box(
                Modifier.fillMaxWidth().padding(40.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = Red) }

            error != null -> Text(error ?: "", color = TextMuted, fontSize = 14.sp)

            release != null -> {
                val r = release!!
                val newer = isNewer(r.tagName.trimStart('v'), BuildConfig.VERSION_NAME)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Bg3)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                r.name.ifEmpty { r.tagName },
                                color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold
                            )
                            if (newer) {
                                Text(
                                    "NEW", color = Color.White, fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .background(Red, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            } else {
                                Text("Up to date", color = Color(0xFF4CAF50), fontSize = 12.sp)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(r.publishedAt, color = TextMuted, fontSize = 12.sp)
                        if (r.body.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                r.body.take(400) + if (r.body.length > 400) "…" else "",
                                color = TextMuted, fontSize = 13.sp, lineHeight = 20.sp
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        val targetUrl = r.apkUrl ?: r.downloadUrl
                        val label    = if (r.apkUrl != null) "Download APK" else "View Release"
                        Button(
                            onClick = {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Red),
                            shape = RoundedCornerShape(8.dp)
                        ) { Text(label, color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                }
                if (!newer) {
                    Spacer(Modifier.height(12.dp))
                    Text("You are running the latest version.", color = TextMuted, fontSize = 13.sp)
                }
            }
        }
    }
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
