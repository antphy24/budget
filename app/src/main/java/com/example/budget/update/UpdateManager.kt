package com.example.budget.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-app updates from GitHub Releases (same approach as Absensi Shalat).
 *
 * Releases live in [RELEASES_REPO], which must be PUBLIC: phones check without logging in.
 * Each release is tagged `v<versionName>+<versionCode>` (e.g. v1.2.0+5) and carries [APK_ASSET].
 * Notes containing [MANDATORY_MARKER] hide the "Later" button.
 */
object UpdateConfig {
    const val RELEASES_REPO = "antphy24/budget"
    const val APK_ASSET = "budget.apk"
    const val MANDATORY_MARKER = "[required]"
    const val LATEST_APK_URL = "https://github.com/$RELEASES_REPO/releases/latest/download/$APK_ASSET"
}

data class AppVersion(val name: String, val code: Long)

data class UpdateInfo(
    val tag: String,
    val versionName: String,
    val versionCode: Long?,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
    val mandatory: Boolean
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float?) : UpdateState
    /** Installer opened, or [needsPermission] when the phone must first allow installs from this app */
    data class Ready(val info: UpdateInfo, val file: File, val needsPermission: Boolean) : UpdateState
    data class Failed(val info: UpdateInfo?, val message: String) : UpdateState
}

@Singleton
class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Shown to the user when they dismissed with "Later"; cleared on next check */
    private val _dismissed = MutableStateFlow(false)
    val dismissed: StateFlow<Boolean> = _dismissed.asStateFlow()

    private var lastCheck = 0L
    private var job: Job? = null

    val currentVersion: AppVersion by lazy {
        val pm = context.packageManager
        val info = pm.getPackageInfo(context.packageName, 0)
        AppVersion(info.versionName ?: "0", PackageInfoCompat.getLongVersionCode(info))
    }

    /** Background check: quiet on errors, throttled to every [minIntervalMs]. */
    fun checkInBackground(minIntervalMs: Long = 6 * 60 * 60 * 1000L) {
        val pending = _state.value
        if (pending is UpdateState.Available && pending.info.mandatory) {
            _dismissed.value = false
            return
        }
        if (System.currentTimeMillis() - lastCheck < minIntervalMs) return
        check(manual = false)
    }

    /** "Check for updates" button: reports up-to-date and errors. */
    fun check(manual: Boolean = true) {
        if (job?.isActive == true) return
        job = scope.launch {
            if (manual) _state.value = UpdateState.Checking
            try {
                val info = withContext(Dispatchers.IO) { fetchLatest() }
                lastCheck = System.currentTimeMillis()
                _dismissed.value = false
                _state.value = when {
                    info != null -> UpdateState.Available(info)
                    manual -> UpdateState.UpToDate
                    else -> UpdateState.Idle
                }
            } catch (e: Exception) {
                _state.value = if (manual) UpdateState.Failed(null, "Couldn't reach GitHub. Check your connection and try again.")
                else UpdateState.Idle
            }
        }
    }

    fun dismiss() {
        val s = _state.value
        if (s is UpdateState.Available && s.info.mandatory) return
        _dismissed.value = true
        if (s is UpdateState.UpToDate || s is UpdateState.Failed && s.info == null) _state.value = UpdateState.Idle
    }

    fun download(info: UpdateInfo) {
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = UpdateState.Downloading(info, null)
            try {
                val file = withContext(Dispatchers.IO) {
                    downloadApk(info) { p -> _state.value = UpdateState.Downloading(info, p) }
                }
                install(info, file)
            } catch (e: Exception) {
                _state.value = UpdateState.Failed(info, "Download stopped. Check your connection and try again.")
            }
        }
    }

    /** Opens Android's installer, or the "allow installs" page the first time. */
    fun install(info: UpdateInfo, file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            _state.value = UpdateState.Ready(info, file, needsPermission = true)
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.update_provider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        _state.value = UpdateState.Ready(info, file, needsPermission = false)
    }

    // --- GitHub ---

    private fun fetchLatest(): UpdateInfo? {
        val conn = (URL("https://api.github.com/repos/${UpdateConfig.RELEASES_REPO}/releases/latest")
            .openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "budget-app")
        }
        try {
            val code = conn.responseCode
            if (code == 404) return null // no release yet
            if (code != 200) throw IOException("GitHub answered $code")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })

            val tag = json.optString("tag_name")
            val (name, build) = parseTag(tag) ?: return null
            if (!isNewer(name, build, currentVersion)) return null

            val assets = json.optJSONArray("assets") ?: return null
            var url: String? = null
            var size = 0L
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val n = a.optString("name")
                if (n == UpdateConfig.APK_ASSET || (url == null && n.endsWith(".apk"))) {
                    url = a.optString("browser_download_url")
                    size = a.optLong("size")
                    if (n == UpdateConfig.APK_ASSET) break
                }
            }
            url ?: return null

            val body = json.optString("body").trim()
            return UpdateInfo(
                tag = tag,
                versionName = name,
                versionCode = build,
                notes = body.replace(UpdateConfig.MANDATORY_MARKER, "", ignoreCase = true).trim(),
                apkUrl = url,
                apkSize = size,
                mandatory = body.contains(UpdateConfig.MANDATORY_MARKER, ignoreCase = true)
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun downloadApk(info: UpdateInfo, onProgress: (Float?) -> Unit): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val safeTag = info.tag.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val file = File(dir, "budget-$safeTag.apk")
        if (file.exists() && info.apkSize > 0 && file.length() == info.apkSize) {
            onProgress(1f)
            return file
        }
        dir.listFiles()?.forEach { it.delete() }

        val part = File(dir, "${file.name}.part")
        var url = URL(info.apkUrl)
        var conn: HttpURLConnection
        var redirects = 0
        // GitHub redirects to its file storage; follow manually so https→https hops always work
        while (true) {
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "budget-app")
            }
            val code = conn.responseCode
            if (code in 300..399 && redirects < 5) {
                val loc = conn.getHeaderField("Location") ?: throw IOException("Bad redirect")
                conn.disconnect()
                url = URL(url, loc)
                redirects++
                continue
            }
            if (code != 200) throw IOException("Download failed ($code)")
            break
        }
        try {
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.apkSize
            var received = 0L
            conn.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var lastReport = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        received += n
                        if (received - lastReport > 128 * 1024 || received == total) {
                            lastReport = received
                            onProgress(if (total > 0) received.toFloat() / total else null)
                        }
                    }
                }
            }
            if (total > 0 && received != total) throw IOException("Download interrupted")
            if (!part.renameTo(file)) throw IOException("Couldn't save the update")
            return file
        } catch (e: Exception) {
            part.delete()
            throw e
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private val TAG_RE = Regex("""^v?(\d+(?:\.\d+)*)(?:\+(\d+))?$""")

        /** "v1.2.3+4" → ("1.2.3", 4); "v1.2.3" → ("1.2.3", null) */
        fun parseTag(tag: String): Pair<String, Long?>? {
            val m = TAG_RE.find(tag.trim()) ?: return null
            return m.groupValues[1] to m.groupValues[2].takeIf { it.isNotEmpty() }?.toLong()
        }

        fun isNewer(name: String, build: Long?, current: AppVersion): Boolean =
            if (build != null) build > current.code else compareNames(name, current.name) > 0

        private fun compareNames(a: String, b: String): Int {
            val pa = a.split('.').map { it.toIntOrNull() ?: 0 }
            val pb = b.split('.').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(pa.size, pb.size)) {
                val x = pa.getOrElse(i) { 0 }
                val y = pb.getOrElse(i) { 0 }
                if (x != y) return x.compareTo(y)
            }
            return 0
        }
    }
}
