package com.tripwire.app.ai

import android.app.ActivityManager
import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.tripwire.app.AppGraph
import com.tripwire.app.BuildConfig
import com.tripwire.app.graph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Picks, finds, downloads and attaches the tactic model (ONB-05, TAC-09, UPD-03).
 *
 * Device tiers (PRD 14.3): A = 12 GB or more, full model; B = 8 GB or more, small model;
 * C = under 8 GB, keyword rules only. During development a model can be copied with
 * `adb push <file> /sdcard/Android/data/com.tripwire.app/files/models/`.
 */
class ModelManager(private val context: Context, private val graph: AppGraph) {
    private val _state = MutableStateFlow<ModelState>(ModelState.None)
    val state: StateFlow<ModelState> = _state
    private var reader: LlmTacticReader? = null

    val tier: Char by lazy {
        val mem = ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
        val gb = mem.totalMem / (1024.0 * 1024 * 1024)
        when {
            gb >= 11.0 -> 'A' // reported totals sit a little under the marketed size
            gb >= 7.0 -> 'B'
            else -> 'C'
        }
    }

    /** Folder a developer can push to without root. */
    val pushDir: File get() = File(context.getExternalFilesDir(null), "models").apply { mkdirs() }
    private val downloadDir: File get() = File(context.filesDir, "models").apply { mkdirs() }

    fun findModel(): File? {
        val files = (downloadDir.listFiles()?.toList().orEmpty() + pushDir.listFiles()?.toList().orEmpty())
            .filter { it.isFile && it.name.endsWith(".litertlm") && it.length() > 1_000_000 }
        val preferred = if (tier == 'A') FULL else SMALL
        return files.firstOrNull { it.name == preferred } ?: files.maxByOrNull { it.length() }
    }

    fun attachIfPresent() {
        // Debug builds load a pushed model on any phone, so the model path can be tested on small
        // emulators. Release builds follow the tiers (TAC-09).
        if (tier == 'C' && !(BuildConfig.DEBUG && findModel() != null)) {
            _state.value = ModelState.RulesOnlyDevice
            graph.pipeline.setModel(null)
            return
        }
        val file = findModel()
        if (file == null) {
            _state.value = ModelState.None
            graph.pipeline.setModel(null)
            return
        }
        reader?.release()
        val r = LlmTacticReader(context, file) { _state.value = it }
        reader = r
        graph.pipeline.setModel(r)
        _state.value = ModelState.Idle(file.name, "")
    }

    val downloadConfigured: Boolean get() = BuildConfig.MODEL_URL.isNotBlank()

    /** Downloads over Wi-Fi by default, resumes interrupted downloads and verifies the hash (ONB-05). */
    fun startDownload(wifiOnly: Boolean = true) {
        if (!downloadConfigured) return
        val req = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build())
            .setInputData(workDataOf("url" to BuildConfig.MODEL_URL, "sha256" to BuildConfig.MODEL_SHA256, "name" to if (tier == 'A') FULL else SMALL))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("model-download", ExistingWorkPolicy.KEEP, req)
    }

    internal fun reportDownload(percent: Int) {
        _state.value = ModelState.Downloading(percent)
    }

    companion object {
        const val FULL = "tactic-full.litertlm"
        const val SMALL = "tactic-small.litertlm"
    }
}

class ModelDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val url = inputData.getString("url") ?: return Result.failure()
        val sha = inputData.getString("sha256").orEmpty()
        val name = inputData.getString("name") ?: return Result.failure()
        val dir = File(applicationContext.filesDir, "models").apply { mkdirs() }
        val part = File(dir, "$name.part")
        val manager = applicationContext.graph.models
        return try {
            val have = if (part.exists()) part.length() else 0L
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                if (have > 0) setRequestProperty("Range", "bytes=$have-")
            }
            val resumed = conn.responseCode == 206
            val total = (if (resumed) have else 0L) + conn.contentLengthLong.coerceAtLeast(0)
            RandomAccessFile(part, "rw").use { out ->
                if (resumed) out.seek(have) else out.setLength(0)
                conn.inputStream.use { input ->
                    val buf = ByteArray(1 shl 16)
                    var done = if (resumed) have else 0L
                    var lastPct = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val pct = if (total > 0) (done * 100 / total).toInt() else 0
                        if (pct != lastPct) {
                            lastPct = pct
                            manager.reportDownload(pct)
                        }
                    }
                }
            }
            if (sha.isNotBlank() && !sha.equals(sha256(part), ignoreCase = true)) {
                part.delete()
                return Result.failure()
            }
            part.renameTo(File(dir, name))
            manager.attachIfPresent()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
