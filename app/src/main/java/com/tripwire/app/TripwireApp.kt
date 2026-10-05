package com.tripwire.app

import android.app.Application
import android.content.Context
import com.tripwire.app.ai.ModelManager
import com.tripwire.app.data.RoomLedgerStore
import com.tripwire.app.data.SettingsStore
import com.tripwire.app.data.TripwireDatabase
import com.tripwire.app.engine.Guardian
import com.tripwire.app.intervene.AllyNotifier
import com.tripwire.app.intervene.Speaker
import com.tripwire.app.notify.Notifier
import com.tripwire.app.work.Workers
import com.tripwire.core.pipeline.PipelineConfig
import com.tripwire.core.pipeline.TripwirePipeline
import com.tripwire.core.script.ScriptPack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class TripwireApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        // The model process (":llm") only hosts LlmService; it must not open the ledger or start protection.
        if (getProcessName().endsWith(":llm")) return
        graph = AppGraph(this)
        graph.notifier.createChannels()
        Workers.schedule(this)
    }
}

val Context.graph: AppGraph get() = (applicationContext as TripwireApp).graph

/** The app's long-lived objects, created once per process. */
class AppGraph(val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsStore(context, scope)
    val db: TripwireDatabase = TripwireDatabase.open(context)
    val store = RoomLedgerStore(db)

    /** A signed update in app storage replaces the bundled pack (UPD-01); see [com.tripwire.app.work.PackUpdateWorker]. */
    val pack: ScriptPack = loadPack(context)

    @Volatile var allyName: String? = null
        private set

    val pipeline = TripwirePipeline(
        pack = pack,
        store = store,
        model = null, // the model is attached by ModelManager once it loads
        config = {
            val s = settings.current
            PipelineConfig(
                language = s.language,
                offsets = s.offsets,
                allyName = allyName,
                disabledTypes = s.disabledTypes,
                paused = s.isPaused(System.currentTimeMillis()),
            )
        },
    )

    val notifier = Notifier(context)
    val speaker = Speaker(context)
    val allies = AllyNotifier(context, this)
    val models = ModelManager(context, this)
    val guardian = Guardian(this)

    init {
        // Room refuses main-thread queries; load what the pipeline needs in the background.
        scope.launch(Dispatchers.IO) {
            refreshAllies()
            models.attachIfPresent()
        }
    }

    fun refreshAllies() {
        allyName = runCatching { store.dao.allies().firstOrNull()?.name }.getOrNull()
    }

    companion object {
        const val PACK_FILE = "script_pack.json"

        fun loadPack(context: Context): ScriptPack {
            val installed = File(context.filesDir, PACK_FILE)
            if (installed.exists()) {
                runCatching { return ScriptPack.parse(installed.readText()) }
            }
            return ScriptPack.bundled()
        }
    }
}
