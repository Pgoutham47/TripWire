package com.tripwire.app.data

import androidx.room.withTransaction
import com.tripwire.core.checks.CheckResult
import com.tripwire.core.engine.CaseState
import com.tripwire.core.engine.EvidenceRecord
import com.tripwire.core.ledger.InterventionLevel
import com.tripwire.core.ledger.InterventionRecord
import com.tripwire.core.ledger.LedgerStore
import com.tripwire.core.model.Counterparty
import com.tripwire.core.model.CounterpartyLink
import com.tripwire.core.model.CounterpartyType
import com.tripwire.core.model.Entities
import com.tripwire.core.model.Event
import com.tripwire.core.model.EventType
import com.tripwire.core.model.LinkReason
import com.tripwire.core.model.Tactic
import com.tripwire.core.model.TacticTag
import com.tripwire.core.model.TagSource
import com.tripwire.core.model.UpiPayment
import com.tripwire.core.script.ScriptPack
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer

/** The core [LedgerStore] on the encrypted Room database. Call from background threads only. */
class RoomLedgerStore(private val db: TripwireDatabase) : LedgerStore {
    val dao: LedgerDao = db.dao()
    private val codec = ScriptPack.json
    private val stringSet = SetSerializer(String.serializer())

    override fun counterparty(id: String) = dao.counterparty(id)?.toModel()
    override fun upsertCounterparty(c: Counterparty) = dao.upsertCounterparty(c.toRow())
    override fun counterpartiesWithIdentifier(identifier: String) =
        dao.counterpartiesLike(codec.encodeToString(String.serializer(), identifier))
            .map { it.toModel() }.filter { identifier in it.identifiers }
    override fun allCounterparties() = dao.allCounterparties().map { it.toModel() }
    override fun deleteCounterparty(id: String) = tx {
        dao.deleteTagsOf(id)
        dao.deleteEventsOf(id)
        dao.deleteLinks(id)
        dao.deleteCounterpartyRow(id)
    }

    override fun addLink(link: CounterpartyLink) = dao.addLink(LinkRow(link.fromId, link.toId, link.reason.name, link.confidence))
    override fun linksFrom(id: String) = dao.linksFrom(id).map { it.toModel() }
    override fun linksTo(id: String) = dao.linksTo(id).map { it.toModel() }

    override fun insertEvent(e: Event): Long = dao.insertEvent(e.toRow())
    override fun event(id: Long) = dao.event(id)?.toModel()
    override fun eventsFor(counterpartyIds: Collection<String>) =
        if (counterpartyIds.isEmpty()) emptyList() else dao.eventsFor(counterpartyIds.toList()).map { it.toModel() }
    override fun updateEvent(e: Event) = dao.updateEvent(e.toRow())

    override fun saveTags(eventId: Long, tags: List<TacticTag>) = tx {
        dao.clearTags(eventId)
        dao.insertTags(tags.map { TagRow(eventId, it.tactic.wire, it.confidence, it.source.name) })
    }
    override fun tags(eventId: Long) = dao.tags(eventId).mapNotNull { it.toModel() }

    override fun caseState(caseId: String) = dao.caseState(caseId)?.toModel()
    override fun saveCaseState(state: CaseState) = dao.saveCase(state.toRow())
    override fun allCaseStates() = dao.allCases().map { it.toModel() }
    override fun deleteCaseState(caseId: String) = tx {
        dao.deleteCaseRow(caseId)
        dao.deleteEvidenceOf(caseId)
        dao.deleteChecksOf(caseId)
    }

    override fun addEvidence(records: List<EvidenceRecord>) {
        if (records.isEmpty()) return
        dao.insertEvidence(records.map { EvidenceRow(caseId = it.caseId, eventId = it.eventId, json = codec.encodeToString(EvidenceRecord.serializer(), it), time = it.time) })
    }
    override fun evidence(caseId: String) = dao.evidence(caseId).map { codec.decodeFromString(EvidenceRecord.serializer(), it.json) }

    override fun saveCheck(caseId: String, check: CheckResult, time: Long) =
        dao.insertCheck(CheckRow(caseId = caseId, json = codec.encodeToString(CheckResult.serializer(), check), time = time))
    override fun checks(caseId: String) = dao.checks(caseId).map { codec.decodeFromString(CheckResult.serializer(), it.json) to it.time }

    override fun saveIntervention(r: InterventionRecord): Long {
        val id = dao.insertIntervention(r.toRow(0))
        dao.updateIntervention(r.copy(id = id).toRow(id))
        return id
    }
    override fun updateIntervention(r: InterventionRecord) = dao.updateIntervention(r.toRow(r.id))
    override fun interventions(caseId: String) = dao.interventions(caseId).map { it.toModel() }
    override fun intervention(id: Long) = dao.intervention(id)?.toModel()

    override fun heldCases() = dao.holds().toSet()
    override fun setHold(caseId: String, hold: Boolean) = if (hold) dao.hold(HoldRow(caseId)) else dao.release(caseId)

    override fun deleteEverything() = db.clearAllTables()

    private fun tx(block: () -> Unit) = runBlocking { db.withTransaction { block() } }

    // ------------------------------------------------------------------ mapping

    fun CounterpartyRow.toModel() = Counterparty(
        id = id, displayName = displayName, type = CounterpartyType.valueOf(type),
        identifiers = codec.decodeFromString(stringSet, identifiersJson),
        apps = codec.decodeFromString(stringSet, appsJson),
        firstSeen = firstSeen, lastSeen = lastSeen, trusted = trusted,
    )

    private fun Counterparty.toRow() = CounterpartyRow(
        id = id, displayName = displayName, type = type.name,
        identifiersJson = codec.encodeToString(stringSet, identifiers),
        appsJson = codec.encodeToString(stringSet, apps),
        firstSeen = firstSeen, lastSeen = lastSeen, trusted = trusted,
    )

    private fun LinkRow.toModel() = CounterpartyLink(fromId, toId, LinkReason.valueOf(reason), confidence)

    fun EventRow.toModel() = Event(
        id = id, counterpartyId = counterpartyId, app = app,
        type = EventType.fromWire(type) ?: EventType.MESSAGE,
        text = text, entities = codec.decodeFromString(Entities.serializer(), entitiesJson),
        timestamp = timestamp, source = source, senderName = senderName, groupName = groupName,
        installedPackage = installedPackage, installedLabel = installedLabel, installerPackage = installerPackage,
        upi = upiJson?.let { codec.decodeFromString(UpiPayment.serializer(), it) },
        isVideoCall = isVideoCall, textDeleted = textDeleted,
    )

    private fun Event.toRow() = EventRow(
        id = id, counterpartyId = counterpartyId, app = app, type = type.wire, text = text,
        entitiesJson = codec.encodeToString(Entities.serializer(), entities),
        timestamp = timestamp, source = source, senderName = senderName, groupName = groupName,
        installedPackage = installedPackage, installedLabel = installedLabel, installerPackage = installerPackage,
        upiJson = upi?.let { codec.encodeToString(UpiPayment.serializer(), it) },
        isVideoCall = isVideoCall, textDeleted = textDeleted,
    )

    fun TagRow.toModel(): TacticTag? = Tactic.fromWire(tag)?.let { TacticTag(it, confidence, TagSource.valueOf(source)) }

    fun CaseRow.toModel(): CaseState = codec.decodeFromString(CaseState.serializer(), stateJson)

    private fun CaseState.toRow() = CaseRow(
        caseId = caseId, stateJson = codec.encodeToString(CaseState.serializer(), this),
        risk = risk, stage = stage.name, topFamily = topFamily, status = status.name,
        openedAt = openedAt, lastEventAt = lastEventAt,
    )

    fun InterventionRow.toModel(): InterventionRecord = codec.decodeFromString(InterventionRecord.serializer(), this.json)

    private fun InterventionRecord.toRow(rowId: Long) = InterventionRow(
        id = rowId, caseId = caseId, level = level.name, moment = moment?.wire,
        json = codec.encodeToString(InterventionRecord.serializer(), this), time = time,
    )

    companion object {
        val tagListSerializer = ListSerializer(TacticTag.serializer())
        fun isFullScreen(row: InterventionRow) = row.level == InterventionLevel.FULL_SCREEN.name
    }
}
