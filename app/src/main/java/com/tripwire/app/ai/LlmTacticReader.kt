package com.tripwire.app.ai

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.BatteryManager
import android.os.IBinder
import android.os.PowerManager
import android.os.RemoteException
import android.util.Log
import com.tripwire.core.tactic.TacticInput
import com.tripwire.core.tactic.TacticOutput
import com.tripwire.core.tactic.TacticOutputParser
import com.tripwire.core.tactic.TacticPrompt
import com.tripwire.core.tactic.TacticReader
import com.tripwire.core.tactic.TacticSchema
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The tactic reader (TAC-01, TAC-05, PRD 10.4), running the model in [LlmService]'s separate
 * process. Every failure (no backend, a crashed model process, a timeout, an invalid answer)
 * returns null, and the core falls back to keyword rules (TAC-04). Nothing here can stop
 * protection.
 */
class LlmTacticReader(
    private val context: Context,
    private val modelFile: File,
    private val onState: (ModelState) -> Unit,
) : TacticReader {
    @Volatile private var remote: ITacticModel? = null
    @Volatile private var connecting: CountDownLatch? = null
    @Volatile var lastLatencyMs: Long = 0
        private set

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            remote = ITacticModel.Stub.asInterface(service)
            connecting?.countDown()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            // The model process died, most likely a native crash. It restarts on the next bind,
            // and LlmService then skips the backend that crashed.
            remote = null
            onState(ModelState.Failed(modelFile.name))
        }
    }

    override fun read(input: TacticInput): TacticOutput? {
        // TAC-08: on a hot phone or a low battery, use the keyword rules instead of the model.
        if (tooHotOrLow()) return null
        val model = bind() ?: return null
        onState(ModelState.Loading(modelFile.name))
        val started = System.nanoTime()
        return try {
            val raw = model.generate(modelFile.absolutePath, TacticPrompt.systemInstruction, TacticPrompt.user(input), TacticSchema.schema)
            lastLatencyMs = (System.nanoTime() - started) / 1_000_000
            val status = runCatching { model.status() }.getOrDefault("unavailable")
            onState(
                when {
                    status.startsWith("ready:") -> ModelState.Ready(modelFile.name, status.substringAfter(':'))
                    status == "unavailable" -> ModelState.Failed(modelFile.name)
                    else -> ModelState.Idle(modelFile.name, "")
                },
            )
            raw?.let { TacticOutputParser.parse(it) }
        } catch (e: RemoteException) {
            Log.w(TAG, "model process failed", e)
            remote = null
            onState(ModelState.Failed(modelFile.name))
            null
        }
    }

    fun release() {
        runCatching { remote?.release() }
        runCatching { context.unbindService(connection) }
        remote = null
    }

    private fun bind(): ITacticModel? {
        remote?.let { return it }
        val latch = CountDownLatch(1)
        connecting = latch
        val ok = context.bindService(Intent(context, LlmService::class.java), connection, Context.BIND_AUTO_CREATE)
        if (!ok) return null
        latch.await(BIND_TIMEOUT_S, TimeUnit.SECONDS)
        return remote
    }

    private fun tooHotOrLow(): Boolean {
        val pm = context.getSystemService(PowerManager::class.java)
        if (pm != null && pm.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE) return true
        val bm = context.getSystemService(BatteryManager::class.java) ?: return false
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return level in 0..14 && !bm.isCharging
    }

    companion object {
        const val TAG = "TripwireModel"
        const val BIND_TIMEOUT_S = 10L
    }
}

sealed interface ModelState {
    data object None : ModelState
    data class Loading(val file: String) : ModelState
    data class Ready(val file: String, val backend: String) : ModelState
    data class Idle(val file: String, val backend: String) : ModelState
    data class Failed(val file: String) : ModelState
    data class Downloading(val percent: Int) : ModelState
    /** Tier C phones run rules only (TAC-09). */
    data object RulesOnlyDevice : ModelState
}
