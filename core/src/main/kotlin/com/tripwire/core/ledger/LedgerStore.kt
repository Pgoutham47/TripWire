package com.tripwire.core.ledger

import com.tripwire.core.checks.CheckResult
import com.tripwire.core.engine.CaseState
import com.tripwire.core.engine.EvidenceRecord
import com.tripwire.core.model.Counterparty
import com.tripwire.core.model.CounterpartyLink
import com.tripwire.core.model.Event
import com.tripwire.core.model.TacticTag
import com.tripwire.core.model.TripwireMoment
import kotlinx.serialization.Serializable

@Serializable
enum class InterventionLevel { QUIET_NOTICE, FULL_SCREEN, FOLLOW_UP }

@Serializable
enum class UserChoice { STOPPED, CALLED_ALLY, VERIFIED, PROCEEDED, MARKED_TRUSTED, DISMISSED, NONE }

@Serializable
enum class Feedback { SCAM, GENUINE, NOT_SURE }

/** One thing shown to the user (table `intervention`). */
@Serializable
data class InterventionRecord(
    val id: Long = 0,
    val caseId: String,
    val level: InterventionLevel,
    val moment: TripwireMoment?,
    /** What the warning was about, e.g. the payee handle or app package. Used for de-duplication (INT-06). */
    val subject: String?,
    val reasons: List<String>,
    val choice: UserChoice = UserChoice.NONE,
    val feedback: Feedback? = null,
    val time: Long,
)

/**
 * The encrypted on-device ledger (LED, PRD 12). The Android app implements it with Room and
 * SQLCipher; [InMemoryLedgerStore] backs tests and the replay harness.
 * Every method is synchronous; callers run it off the main thread.
 */
interface LedgerStore {
    fun counterparty(id: String): Counterparty?
    fun upsertCounterparty(c: Counterparty)
    fun counterpartiesWithIdentifier(identifier: String): List<Counterparty>
    fun allCounterparties(): List<Counterparty>
    fun deleteCounterparty(id: String)

    fun addLink(link: CounterpartyLink)
    fun linksFrom(id: String): List<CounterpartyLink>
    fun linksTo(id: String): List<CounterpartyLink>

    /** Stores the event and returns its new id. The id in [e] is ignored. */
    fun insertEvent(e: Event): Long
    fun event(id: Long): Event?
    fun eventsFor(counterpartyIds: Collection<String>): List<Event>
    fun updateEvent(e: Event)

    fun saveTags(eventId: Long, tags: List<TacticTag>)
    fun tags(eventId: Long): List<TacticTag>

    fun caseState(caseId: String): CaseState?
    fun saveCaseState(state: CaseState)
    fun allCaseStates(): List<CaseState>
    fun deleteCaseState(caseId: String)

    fun addEvidence(records: List<EvidenceRecord>)
    fun evidence(caseId: String): List<EvidenceRecord>

    fun saveCheck(caseId: String, check: CheckResult, time: Long)
    fun checks(caseId: String): List<Pair<CheckResult, Long>>

    fun saveIntervention(r: InterventionRecord): Long
    fun updateIntervention(r: InterventionRecord)
    fun interventions(caseId: String): List<InterventionRecord>
    fun intervention(id: Long): InterventionRecord?

    /** Case ids exempt from automatic deletion because an evidence pack is open (LED-07). */
    fun heldCases(): Set<String>
    fun setHold(caseId: String, hold: Boolean)

    fun deleteEverything()
}

/** Thread-safe in-memory ledger, used by unit tests, the replay harness and previews. */
class InMemoryLedgerStore : LedgerStore {
    private val lock = Any()
    private val counterparties = LinkedHashMap<String, Counterparty>()
    private val links = ArrayList<CounterpartyLink>()
    private val events = LinkedHashMap<Long, Event>()
    private val tags = HashMap<Long, List<TacticTag>>()
    private val cases = LinkedHashMap<String, CaseState>()
    private val evidence = ArrayList<EvidenceRecord>()
    private val checks = ArrayList<Triple<String, CheckResult, Long>>()
    private val interventions = LinkedHashMap<Long, InterventionRecord>()
    private val holds = HashSet<String>()
    private var nextEventId = 1L
    private var nextInterventionId = 1L

    override fun counterparty(id: String) = synchronized(lock) { counterparties[id] }
    override fun upsertCounterparty(c: Counterparty) = synchronized(lock) { counterparties[c.id] = c }
    override fun counterpartiesWithIdentifier(identifier: String) = synchronized(lock) {
        counterparties.values.filter { identifier in it.identifiers }
    }
    override fun allCounterparties() = synchronized(lock) { counterparties.values.toList() }
    override fun deleteCounterparty(id: String) = synchronized(lock) {
        counterparties.remove(id)
        val ids = events.values.filter { it.counterpartyId == id }.map { it.id }
        ids.forEach { events.remove(it); tags.remove(it) }
        links.removeAll { it.fromId == id || it.toId == id }
        Unit
    }

    override fun addLink(link: CounterpartyLink) = synchronized(lock) {
        if (links.none { it.fromId == link.fromId && it.toId == link.toId }) links += link
    }
    override fun linksFrom(id: String) = synchronized(lock) { links.filter { it.fromId == id } }
    override fun linksTo(id: String) = synchronized(lock) { links.filter { it.toId == id } }

    override fun insertEvent(e: Event): Long = synchronized(lock) {
        val id = nextEventId++
        events[id] = e.copy(id = id)
        id
    }
    override fun event(id: Long) = synchronized(lock) { events[id] }
    override fun eventsFor(counterpartyIds: Collection<String>) = synchronized(lock) {
        val set = counterpartyIds.toSet()
        events.values.filter { it.counterpartyId in set }.sortedBy { it.timestamp }
    }
    override fun updateEvent(e: Event) = synchronized(lock) { events[e.id] = e }

    override fun saveTags(eventId: Long, tags: List<TacticTag>) = synchronized(lock) { this.tags[eventId] = tags }
    override fun tags(eventId: Long) = synchronized(lock) { tags[eventId].orEmpty() }

    override fun caseState(caseId: String) = synchronized(lock) { cases[caseId] }
    override fun saveCaseState(state: CaseState) = synchronized(lock) { cases[state.caseId] = state }
    override fun allCaseStates() = synchronized(lock) { cases.values.toList() }
    override fun deleteCaseState(caseId: String) = synchronized(lock) {
        cases.remove(caseId)
        evidence.removeAll { it.caseId == caseId }
        checks.removeAll { it.first == caseId }
        Unit
    }

    override fun addEvidence(records: List<EvidenceRecord>) = synchronized(lock) { evidence += records; Unit }
    override fun evidence(caseId: String) = synchronized(lock) { evidence.filter { it.caseId == caseId } }

    override fun saveCheck(caseId: String, check: CheckResult, time: Long) = synchronized(lock) { checks += Triple(caseId, check, time); Unit }
    override fun checks(caseId: String) = synchronized(lock) { checks.filter { it.first == caseId }.map { it.second to it.third } }

    override fun saveIntervention(r: InterventionRecord): Long = synchronized(lock) {
        val id = nextInterventionId++
        interventions[id] = r.copy(id = id)
        id
    }
    override fun updateIntervention(r: InterventionRecord) = synchronized(lock) { interventions[r.id] = r }
    override fun interventions(caseId: String) = synchronized(lock) { interventions.values.filter { it.caseId == caseId } }
    override fun intervention(id: Long) = synchronized(lock) { interventions[id] }

    override fun heldCases() = synchronized(lock) { holds.toSet() }
    override fun setHold(caseId: String, hold: Boolean) = synchronized(lock) {
        if (hold) holds += caseId else holds -= caseId
        Unit
    }

    override fun deleteEverything() = synchronized(lock) {
        counterparties.clear(); links.clear(); events.clear(); tags.clear(); cases.clear()
        evidence.clear(); checks.clear(); interventions.clear(); holds.clear()
    }
}
