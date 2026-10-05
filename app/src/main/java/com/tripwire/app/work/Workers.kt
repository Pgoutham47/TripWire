package com.tripwire.app.work

import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.tripwire.app.AppGraph
import com.tripwire.app.BuildConfig
import com.tripwire.app.MainActivity
import com.tripwire.app.graph
import com.tripwire.app.ui.Ui
import com.tripwire.core.script.ScriptPack
import com.tripwire.core.share.PackVerifier
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

object Workers {
    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            "retention", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RetentionWorker>(1, TimeUnit.DAYS).build(),
        )
        if (BuildConfig.PACK_URL.isNotBlank()) {
            wm.enqueueUniquePeriodicWork(
                "pack-update", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<PackUpdateWorker>(7, TimeUnit.DAYS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build(),
            )
        }
    }

    /** INT-08: check in one hour after the user went ahead despite a warning. */
    fun scheduleCheckIn(context: Context, caseId: String, at: Long) {
        val delay = (at - System.currentTimeMillis()).coerceAtLeast(0)
        WorkManager.getInstance(context).enqueueUniqueWork(
            "checkin-$caseId", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<CheckInWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf("caseId" to caseId))
                .build(),
        )
    }
}

/** Daily retention pass (LED-03, PRD 12.3). */
class RetentionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = applicationContext.graph
        graph.pipeline.purge(graph.settings.current.retentionDays)
        // Intervention history is kept for 12 months, without message text (PRD 12.3).
        graph.store.dao.deleteInterventionsBefore(System.currentTimeMillis() - 365L * 24 * 3600_000)
        return Result.success()
    }
}

class CheckInWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = applicationContext.graph
        val lang = graph.settings.current.language
        val caseId = inputData.getString("caseId").orEmpty()
        graph.notifier.followUp(
            id = 30,
            title = Ui.t("checkin.title", lang),
            text = Ui.t("checkin.body", lang),
            intent = Intent(applicationContext, MainActivity::class.java).putExtra(MainActivity.EXTRA_PAID_CASE, caseId),
        )
        return Result.success()
    }
}

/**
 * Weekly signed script-pack update (UPD-01, UPD-02). A pack is accepted only if its signature
 * verifies against the key pinned in the app; otherwise the current pack stays. It applies on
 * the next app start.
 */
class PackUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val key = BuildConfig.PACK_PUBLIC_KEY.ifBlank { return Result.success() }
        return try {
            val bytes = fetch(BuildConfig.PACK_URL) ?: return Result.retry()
            val sig = fetch(BuildConfig.PACK_URL + ".sig")?.toString(Charsets.UTF_8) ?: return Result.retry()
            if (!PackVerifier(key).verify(bytes, sig)) return Result.success() // reject, keep the old pack
            val candidate = ScriptPack.parse(bytes.toString(Charsets.UTF_8))
            val current = applicationContext.graph.pack
            if (candidate.version <= current.version) return Result.success()
            val target = File(applicationContext.filesDir, AppGraph.PACK_FILE)
            // UPD-04: keep the previous pack so it can be restored.
            if (target.exists()) target.copyTo(File(applicationContext.filesDir, AppGraph.PACK_FILE + ".prev"), overwrite = true)
            target.writeBytes(bytes)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun fetch(url: String): ByteArray? {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        return if (conn.responseCode == 200) conn.inputStream.use { it.readBytes().takeIf { b -> b.size < 2_000_000 } } else null
    }
}
