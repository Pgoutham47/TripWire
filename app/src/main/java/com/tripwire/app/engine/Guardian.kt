package com.tripwire.app.engine

import android.util.Log
import com.tripwire.app.AppGraph
import com.tripwire.app.intervene.WarningRequest
import com.tripwire.app.widget.TripwireWidget
import com.tripwire.core.evidence.AllyAlert
import com.tripwire.core.model.EventType
import com.tripwire.core.model.Observation
import com.tripwire.core.model.TripwireMoment
import com.tripwire.core.pipeline.IngestResult
import com.tripwire.core.pipeline.MomentDecision
import com.tripwire.core.pipeline.isMoment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Takes observations from every collector, runs them through the pipeline and acts on the result
 * (PRD 11.1, 11.2). Two single-thread lanes keep order within each kind of work:
 *  - the moment lane decides installs, payments and screen shares from stored state, fast;
 *  - the message lane writes each event to the ledger first, then tags it, which can be slow.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Guardian(private val graph: AppGraph) {
    private val momentLane = Dispatchers.IO.limitedParallelism(1)
    private val messageLane = Dispatchers.IO.limitedParallelism(1)
    private val pipeline get() = graph.pipeline

    /** Fire-and-forget entry point for collectors. */
    fun submit(obs: Observation) {
        if (obs.type.isMoment()) {
            graph.scope.launch(momentLane) { handleMoment(obs) }
        } else {
            graph.scope.launch(messageLane) { handleMessage(obs) }
        }
    }

    /** For the UPI link handler, which must wait for the verdict before passing the link on (SIG-08). */
    suspend fun decide(obs: Observation): MomentDecision = withContext(momentLane) {
        pipeline.onMoment(obs).also { if (it.show) Log.i(TAG, "moment ${it.moment} ${it.hardRule ?: ""} in ${it.elapsedMs} ms") }
    }

    private fun handleMoment(obs: Observation) {
        val d = runCatching { pipeline.onMoment(obs) }.onFailure { Log.e(TAG, "moment failed", it) }.getOrNull() ?: return
        debug { "moment ${d.moment} case=${d.caseId != null} show=${d.show} rule=${d.hardRule} risk=${d.risk} in ${d.elapsedMs} ms" }
        if (d.show) {
            showWarning(d, forwardUri = null)
        } else if (obs.type == EventType.COLLECT_REQUEST) {
            // Not part of a tracked scam, but approving any request still sends money.
            runCatching { pipeline.collectRequestAlert(obs) }.getOrNull()?.let { graph.notifier.guardAlert(it) }
        }
        TripwireWidget.refresh(graph.context)
    }

    fun fakeCreditFromContact(sender: String) {
        graph.scope.launch(messageLane) {
            runCatching { pipeline.fakeCreditFromContact(sender) }.getOrNull()?.let {
                debug { "guard FAKE_CREDIT (saved contact)" }
                graph.notifier.guardAlert(it)
            }
        }
    }

    /** OTP guard: a one-time code arrived. Only the fact is passed; the code is never read here. */
    fun otpArrived() {
        graph.scope.launch(momentLane) {
            val alert = runCatching { pipeline.otpArrived() }.onFailure { Log.e(TAG, "otp guard failed", it) }.getOrNull()
            debug { "otp guard: alert=${alert != null}" }
            alert?.let { graph.notifier.guardAlert(it) }
        }
    }

    private fun handleMessage(obs: Observation) {
        try {
            val result = pipeline.ingest(obs)
            debug { "ingest ${obs.type.wire} from ${obs.app} (text ${obs.text?.length ?: 0} chars, group=${obs.groupName != null}, phone=${obs.senderPhone != null}): ${if (result is IngestResult.Ignored) "ignored (${result.reason})" else "stored"}" }
            val stored = result as? IngestResult.Stored ?: return
            val processed = pipeline.process(stored.eventId)
            debug { "processed: tags=${processed?.tags?.map { it.tactic.wire }} risk=${processed?.state?.risk} stage=${processed?.state?.stage} notice=${processed?.notice != null} in ${processed?.elapsedMs} ms" }
            processed?.notice?.let { graph.notifier.quietNotice(it) }
            processed?.alert?.let {
                debug { "guard ${it.kind}" }
                graph.notifier.guardAlert(it)
            }
            TripwireWidget.refresh(graph.context)
            if (obs.type == com.tripwire.core.model.EventType.CALL_STARTED) {
                pipeline.inCallNoticeFor(stored.caseId, obs)?.takeIf { it.show }?.let {
                    debug { "in-call notice shown, risk=${it.risk}" }
                    showWarning(it, null)
                }
            }
        } catch (e: Exception) {
            // PRD 14.5: a failure here must never stop the engine, checks or warnings.
            Log.e(TAG, "message failed", e)
        }
    }

    /** Shows the full-screen warning, speaks it, and alerts the ally (ALY-02). */
    fun showWarning(d: MomentDecision, forwardUri: String?) {
        val warning = d.warning ?: return
        val req = WarningRequest(
            interventionId = d.interventionId ?: 0,
            caseId = d.caseId ?: "",
            warning = warning,
            forwardUri = forwardUri,
        )
        graph.notifier.launchWarning(req)
        if (d.moment != TripwireMoment.CALL) {
            alertAlly(d.caseId, AllyAlert.Kind.WARNING_SHOWN)
        }
    }

    /** Safe to call from any thread: the database work runs in the background. */
    fun alertAlly(caseId: String?, kind: AllyAlert.Kind) {
        graph.scope.launch(Dispatchers.IO) { runCatching { alertAllyNow(caseId, kind) }.onFailure { Log.e(TAG, "ally alert failed", it) } }
    }

    private fun alertAllyNow(caseId: String?, kind: AllyAlert.Kind) {
        val state = caseId?.let { graph.store.caseState(pipeline.rootOf(it)) }
        val lang = graph.settings.current.language
        val alert = AllyAlert(
            kind = kind,
            familyName = pipeline.explain.familyName(state?.topFamily, lang),
            stageName = pipeline.explain.string("stage.${(state?.stage ?: com.tripwire.core.model.Stage.CONTACT).name.lowercase()}", lang),
            time = System.currentTimeMillis(),
        )
        graph.allies.send(alert)
    }

    companion object {
        const val TAG = "Tripwire"

        /** Debug builds only, and never message text, names or numbers (PRD 17). */
        inline fun debug(msg: () -> String) {
            if (com.tripwire.app.BuildConfig.DEBUG) Log.d(TAG, msg())
        }
    }
}
