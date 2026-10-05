package com.tripwire.app.ai

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ResponseFormat
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Hosts the LiteRT-LM engine in the ":llm" process (PRD 14.5: a failure in the tactic reader must
 * never stop the engine, checks or warnings). A native crash here kills only this process; the
 * main process sees a dead binder and falls back to keyword rules.
 *
 * Backends are tried NPU, GPU, CPU. Before each attempt a marker file names the backend; it is
 * removed once the engine is up. A marker found at start-up means that backend crashed the
 * process last time, so it is recorded as bad for this model and never tried again.
 */
class LlmService : Service() {
    private val lock = Any()
    private var engine: Engine? = null
    private var enginePath: String? = null
    private var backendName = ""
    private val reaper = Executors.newSingleThreadScheduledExecutor()
    private var unload: ScheduledFuture<*>? = null

    private val markerFile get() = File(filesDir, "llm_backend_attempt")
    private val badFile get() = File(filesDir, "llm_bad_backends")

    override fun onCreate() {
        super.onCreate()
        // A marker left behind means the previous attempt crashed the process.
        if (markerFile.exists()) {
            val crashed = markerFile.readText().trim()
            badFile.appendText(crashed + "\n")
            markerFile.delete()
            Log.w(TAG, "backend crashed last time, disabled: $crashed")
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private val binder = object : ITacticModel.Stub() {
        override fun generate(modelPath: String, systemInstruction: String, userPrompt: String, jsonSchema: String): String? =
            synchronized(lock) {
                val e = ensureEngine(modelPath) ?: return null
                unload?.cancel(false)
                try {
                    e.createConversation(
                        ConversationConfig(
                            systemInstruction = Contents.of(systemInstruction),
                            samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0),
                            maxOutputToken = 200,
                            enableResponseFormat = true,
                        ),
                    ).use { conv ->
                        val reply = conv.sendMessage(userPrompt, responseFormat = ResponseFormat.json(jsonSchema))
                        reply.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "generation failed", t)
                    null
                } finally {
                    // Release the model after 60 s idle (PRD 14.2).
                    unload = reaper.schedule({ release() }, IDLE_SECONDS, TimeUnit.SECONDS)
                }
            }

        override fun status(): String = synchronized(lock) {
            when {
                engine != null -> "ready:$backendName"
                badBackends().size >= BACKENDS.size -> "unavailable"
                else -> "idle"
            }
        }

        override fun release() = this@LlmService.release()
    }

    private fun release() = synchronized(lock) {
        engine?.close()
        engine = null
        enginePath = null
    }

    private fun badBackends(): Set<String> =
        if (badFile.exists()) badFile.readLines().map { it.trim() }.filter { it.isNotEmpty() }.toSet() else emptySet()

    private fun ensureEngine(modelPath: String): Engine? {
        if (engine != null && enginePath == modelPath) return engine
        release()
        val model = File(modelPath)
        val key = "${model.name}:${model.length()}"
        val bad = badBackends()
        val cache = File(cacheDir, "litertlm").apply { mkdirs() }.absolutePath
        for (name in BACKENDS) {
            val id = "$key@$name"
            if (id in bad) continue
            markerFile.writeText(id)
            try {
                val backend = when (name) {
                    "NPU" -> Backend.NPU(applicationInfo.nativeLibraryDir)
                    "GPU" -> Backend.GPU()
                    else -> Backend.CPU()
                }
                val e = Engine(EngineConfig(modelPath = modelPath, backend = backend, cacheDir = cache))
                e.initialize()
                // A first tiny generation exercises the kernels, which is where unsupported
                // instructions crash; only after it succeeds is the backend trusted.
                e.createConversation(ConversationConfig(maxOutputToken = 2)).use { it.sendMessage("ok") }
                markerFile.delete()
                engine = e
                enginePath = modelPath
                backendName = name
                Log.i(TAG, "model ${model.name} running on $name")
                return e
            } catch (t: Throwable) {
                markerFile.delete()
                badFile.appendText(id + "\n")
                Log.w(TAG, "backend $name unavailable: ${t.message}")
            }
        }
        return null
    }

    companion object {
        const val TAG = "TripwireModel"
        const val IDLE_SECONDS = 60L
        val BACKENDS = listOf("NPU", "GPU", "CPU")
    }
}
