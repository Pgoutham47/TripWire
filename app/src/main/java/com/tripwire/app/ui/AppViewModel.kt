package com.tripwire.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tripwire.app.data.AllyRow
import com.tripwire.app.data.TripwireSettings
import com.tripwire.app.evidence.PackPdf
import com.tripwire.app.graph
import com.tripwire.app.intervene.WarningRequest
import com.tripwire.app.intervene.appLabel
import com.tripwire.core.engine.CaseState
import com.tripwire.core.evidence.AllyAlert
import com.tripwire.core.evidence.Complainant
import com.tripwire.core.evidence.EvidencePack
import com.tripwire.core.evidence.EvidencePackBuilder
import com.tripwire.core.evidence.TransactionDetails
import com.tripwire.core.ledger.InMemoryLedgerStore
import com.tripwire.core.model.EventType
import com.tripwire.core.model.Stage
import com.tripwire.core.pipeline.PipelineConfig
import com.tripwire.core.pipeline.TripwirePipeline
import com.tripwire.core.replay.ReplayHarness
import com.tripwire.core.replay.Scenario
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class CaseItem(
    val caseId: String,
    val title: String,
    val apps: List<String>,
    val family: String,
    val stage: Stage,
    val stageName: String,
    val risk: Int,
    val lastEventAt: Long,
)

data class TimelineRow(
    val time: Long,
    val app: String,
    val title: String,
    val text: String?,
    val tactics: List<String>,
)

data class TimelineUi(val item: CaseItem, val description: String, val rows: List<TimelineRow>)

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = app.graph
    private val dao = graph.store.dao

    val settings: StateFlow<TripwireSettings> = graph.settings.state
    val modelState = graph.models.state

    val cases: StateFlow<List<CaseItem>> = combine(dao.openCasesFlow(), dao.counterpartiesFlow(), dao.linksFlow(), settings) { rows, _, _, s ->
        rows.map { row -> with(graph.store) { row.toModel() } }
            .filter { it.isOpen }
            .map { toItem(it, s.language) }
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allies: StateFlow<List<AllyRow>> = dao.alliesFlow().flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun timeline(caseId: String): Flow<TimelineUi?> {
        // Resolving the case root reads the database, so it runs inside the IO-bound flow.
        return flow { emit(graph.pipeline.rootOf(caseId)) }.flatMapLatest { root ->
            combine(dao.caseFlow(root), settings) { row, s -> Triple(root, row, s.language) }
        }.flatMapLatest { (root, row, lang) ->
            if (row == null) return@flatMapLatest flowOf(null)
            val members = graph.pipeline.membersOf(root)
            dao.eventsForFlow(members).map { events ->
                val state = with(graph.store) { row.toModel() }
                val explain = graph.pipeline.explain
                val tags = dao.tagsFor(events.map { it.id }).groupBy { it.eventId }
                val rows = events.mapNotNull { e ->
                    val type = EventType.fromWire(e.type) ?: return@mapNotNull null
                    if (type == EventType.CALL_ENDED) return@mapNotNull null
                    val tactics = tags[e.id].orEmpty().filter { it.confidence >= 0.5 }
                        .mapNotNull { t -> with(graph.store) { t.toModel() } }.map { explain.tacticWords(it.tactic, lang) }
                    val title = when (type) {
                        EventType.MESSAGE -> e.senderName ?: Ui.t("timeline.contact", lang)
                        EventType.GROUP_ADDED -> explain.string("timeline.group_added", lang, mapOf("group" to (e.groupName ?: "")))
                        EventType.APP_INSTALLED, EventType.INSTALL_SCREEN_OPENED -> explain.string("timeline.app_installed", lang, mapOf("app" to (e.installedLabel ?: "")))
                        EventType.UPI_LINK_OPENED -> explain.string("timeline.payment", lang, mapOf("handle" to (e.upiJson?.let { Regex("\"payeeHandle\":\"([^\"]+)\"").find(it)?.groupValues?.get(1) } ?: "")))
                        EventType.PAYMENT_APP_OPENED -> explain.string("pack.payment_app_opened", lang, mapOf("app" to appLabel(e.app)))
                        EventType.CALL_STARTED -> explain.string(if (e.isVideoCall) "timeline.video_call" else "timeline.call", lang)
                        EventType.SCREEN_SHARE_STARTED, EventType.REMOTE_APP_OPENED -> explain.string("timeline.screen_share", lang)
                        EventType.PAYMENT_SMS -> explain.string("timeline.paid", lang)
                        else -> type.wire
                    }
                    TimelineRow(e.timestamp, e.app, title, if (e.textDeleted) Ui.t("timeline.deleted", lang) else e.text, tactics)
                }
                val family = state.topFamily?.let { graph.pack.family(it) }
                TimelineUi(
                    item = toItem(state, lang),
                    description = family?.descriptions?.let { it[lang] ?: it["en"] }.orEmpty(),
                    rows = rows,
                )
            }
        }.flowOn(Dispatchers.IO)
    }

    private fun toItem(state: CaseState, lang: String): CaseItem {
        val explain = graph.pipeline.explain
        val members = graph.pipeline.membersOf(state.caseId).mapNotNull { graph.store.counterparty(it) }
        val root = members.firstOrNull { it.id == state.caseId } ?: members.firstOrNull()
        return CaseItem(
            caseId = state.caseId,
            title = root?.displayName ?: state.caseId,
            apps = members.flatMap { it.apps }.distinct().map { appLabel(it) },
            family = explain.familyName(state.topFamily, lang),
            stage = state.stage,
            stageName = explain.string("stage.${state.stage.name.lowercase()}", lang),
            risk = graph.pipeline.engine.riskAt(state, System.currentTimeMillis()),
            lastEventAt = state.lastEventAt,
        )
    }

    // ------------------------------------------------------------------ actions

    fun updateSettings(transform: (TripwireSettings) -> TripwireSettings) = viewModelScope.launch {
        val before = settings.value
        graph.settings.update(transform)
        val after = settings.value
        // PRD 15.4: turning protection off during an active case alerts the ally.
        val turnedOff = (!before.isPaused(System.currentTimeMillis()) && after.isPaused(System.currentTimeMillis())) ||
            after.disabledCollectors.size > before.disabledCollectors.size
        if (turnedOff && cases.value.isNotEmpty()) graph.guardian.alertAlly(cases.value.first().caseId, AllyAlert.Kind.PROTECTION_OFF)
    }

    fun markTrusted(caseId: String) = io { graph.pipeline.markTrusted(caseId) }
    fun deleteCase(caseId: String) = io { graph.pipeline.deleteCase(caseId) }

    fun deleteAll(onDone: () -> Unit) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            graph.store.deleteEverything()
            File(getApplication<Application>().filesDir, "packs").deleteRecursively()
        }
        // Derived from the deleted records, so they go too; language and permissions stay.
        graph.settings.update { it.copy(paidShortcutUntil = 0, watchOffset = 0, warnOffset = 0) }
        onDone()
    }

    fun addAlly(name: String, phone: String) = io {
        dao.allies().forEach { dao.deleteAlly(it.id) } // P0 keeps one ally; ALY-01 raises this to three
        dao.insertAlly(AllyRow(name = name, phone = phone))
        graph.refreshAllies()
    }

    fun removeAlly(id: Long) = io {
        dao.deleteAlly(id)
        graph.refreshAllies()
    }

    fun refreshModel() = io { graph.models.attachIfPresent() }
    fun downloadModel() = graph.models.startDownload()

    /** Pre-fill for journey 8 from the latest payment SMS linked to the case (EVD-05). */
    suspend fun prefill(caseId: String): TransactionDetails? = withContext(Dispatchers.IO) {
        EvidencePackBuilder(graph.pipeline).latestPayment(caseId)
    }

    /** Builds the pack and its PDF, holds the case against deletion (LED-07), alerts the ally. */
    suspend fun buildPack(caseId: String?, complainant: Complainant, tx: TransactionDetails): Pair<EvidencePack, File> = withContext(Dispatchers.IO) {
        val lang = settings.value.language
        val builder = EvidencePackBuilder(graph.pipeline)
        val id = caseId ?: "manual"
        val pack = builder.build(id, complainant, listOf(tx), lang, System.currentTimeMillis()) { appLabel(it) }
        val file = PackPdf(getApplication(), graph.pipeline.explain).render(pack, lang) { appLabel(it) }
        if (caseId != null) graph.store.setHold(graph.pipeline.rootOf(caseId), true)
        dao.insertPack(
            com.tripwire.app.data.EvidencePackRow(
                caseId = id,
                json = com.tripwire.core.script.ScriptPack.json.encodeToString(EvidencePack.serializer(), pack),
                filePath = file.absolutePath,
                createdAt = pack.createdAt,
            ),
        )
        graph.guardian.alertAlly(caseId, AllyAlert.Kind.ALREADY_PAID)
        pack to file
    }

    fun callScript(pack: EvidencePack): String = EvidencePackBuilder(graph.pipeline).callScript(pack, settings.value.language)

    fun shareIntent(file: File) = PackPdf(getApplication(), graph.pipeline.explain).shareIntent(file)

    val scenarios: List<Scenario> by lazy { ReplayHarness.bundledScenarios() }

    /**
     * Replays a recorded scam in a private in-memory pipeline and shows the first warning it raises
     * (ONB-06 sample, PRD 22 demo fallback). Real records are never touched.
     */
    fun playScenario(id: String = "fi_ramesh_hinglish", show: (WarningRequest) -> Unit) = viewModelScope.launch {
        val req = withContext(Dispatchers.Default) {
            val scenario = scenarios.firstOrNull { it.id == id } ?: return@withContext null
            val start = System.currentTimeMillis() - (scenario.steps.maxOf { it.t } + 1) * 60_000
            var now = start
            val lang = settings.value.language
            val p = TripwirePipeline(graph.pack, InMemoryLedgerStore(), null, { PipelineConfig(language = lang, allyName = graph.allyName) }, { now })
            var last: WarningRequest? = null
            for (step in scenario.steps) {
                now = start + step.t * 60_000
                val out = p.onObservation(step.obs.copy(timestamp = now))
                out.moment?.takeIf { it.show }?.warning?.let { last = WarningRequest(0, "", it, sample = true) }
                if (last != null && id == "fi_ramesh_hinglish" && step.obs.type == EventType.UPI_LINK_OPENED) break
            }
            last
        }
        req?.let(show)
    }

    private fun io(block: () -> Unit) = viewModelScope.launch(Dispatchers.IO) { block() }
}
