package com.tripwire.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/*
 * The ten tables of PRD 12.1, plus a hold table for LED-07. Structured values that the engine
 * owns (entities, case state, evidence) are stored as JSON next to the columns the UI queries.
 */

@Entity(tableName = "counterparty")
data class CounterpartyRow(
    @PrimaryKey val id: String,
    val displayName: String,
    val type: String,
    val identifiersJson: String,
    val appsJson: String,
    val firstSeen: Long,
    val lastSeen: Long,
    val trusted: Boolean,
)

@Entity(tableName = "counterparty_link", primaryKeys = ["fromId", "toId"], indices = [Index("toId")])
data class LinkRow(
    val fromId: String,
    val toId: String,
    val reason: String,
    val confidence: Double,
)

@Entity(tableName = "event", indices = [Index("counterpartyId"), Index("timestamp")])
data class EventRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val counterpartyId: String,
    val app: String,
    val type: String,
    val text: String?,
    val entitiesJson: String,
    val timestamp: Long,
    val source: String,
    val senderName: String?,
    val groupName: String?,
    val installedPackage: String?,
    val installedLabel: String?,
    val installerPackage: String?,
    val upiJson: String?,
    val isVideoCall: Boolean,
    val textDeleted: Boolean,
)

@Entity(tableName = "tactic_tag", primaryKeys = ["eventId", "tag"])
data class TagRow(
    val eventId: Long,
    val tag: String,
    val confidence: Double,
    val source: String,
)

@Entity(tableName = "case_state")
data class CaseRow(
    @PrimaryKey val caseId: String,
    val stateJson: String,
    val risk: Int,
    val stage: String,
    val topFamily: String?,
    val status: String,
    val openedAt: Long?,
    val lastEventAt: Long,
)

@Entity(tableName = "case_evidence", indices = [Index("caseId")])
data class EvidenceRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val caseId: String,
    val eventId: Long?,
    val json: String,
    val time: Long,
)

@Entity(tableName = "check_result", indices = [Index("caseId")])
data class CheckRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val caseId: String,
    val json: String,
    val time: Long,
)

@Entity(tableName = "intervention", indices = [Index("caseId")])
data class InterventionRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val caseId: String,
    val level: String,
    val moment: String?,
    val json: String,
    val time: Long,
)

@Entity(tableName = "evidence_pack", indices = [Index("caseId")])
data class EvidencePackRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val caseId: String,
    val json: String,
    val filePath: String?,
    val createdAt: Long,
)

@Entity(tableName = "ally")
data class AllyRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String,
    val hasApp: Boolean = false,
    val alertsSent: Int = 0,
)

/** Cases exempt from automatic deletion while an evidence pack is open (LED-07). */
@Entity(tableName = "case_hold")
data class HoldRow(@PrimaryKey val caseId: String)

@Dao
interface LedgerDao {
    // counterparty
    @Query("SELECT * FROM counterparty WHERE id = :id") fun counterparty(id: String): CounterpartyRow?
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertCounterparty(row: CounterpartyRow)
    @Query("SELECT * FROM counterparty WHERE identifiersJson LIKE '%' || :quoted || '%'") fun counterpartiesLike(quoted: String): List<CounterpartyRow>
    @Query("SELECT * FROM counterparty") fun allCounterparties(): List<CounterpartyRow>
    @Query("SELECT * FROM counterparty") fun counterpartiesFlow(): Flow<List<CounterpartyRow>>
    @Query("DELETE FROM counterparty WHERE id = :id") fun deleteCounterpartyRow(id: String)

    // links
    @Insert(onConflict = OnConflictStrategy.IGNORE) fun addLink(row: LinkRow)
    @Query("SELECT * FROM counterparty_link WHERE fromId = :id") fun linksFrom(id: String): List<LinkRow>
    @Query("SELECT * FROM counterparty_link WHERE toId = :id") fun linksTo(id: String): List<LinkRow>
    @Query("DELETE FROM counterparty_link WHERE fromId = :id OR toId = :id") fun deleteLinks(id: String)
    @Query("SELECT * FROM counterparty_link") fun linksFlow(): Flow<List<LinkRow>>

    // events
    @Insert fun insertEvent(row: EventRow): Long
    @Update fun updateEvent(row: EventRow)
    @Query("SELECT * FROM event WHERE id = :id") fun event(id: Long): EventRow?
    @Query("SELECT * FROM event WHERE counterpartyId IN (:ids) ORDER BY timestamp, id") fun eventsFor(ids: List<String>): List<EventRow>
    @Query("SELECT * FROM event WHERE counterpartyId IN (:ids) ORDER BY timestamp, id") fun eventsForFlow(ids: List<String>): Flow<List<EventRow>>
    @Query("DELETE FROM tactic_tag WHERE eventId IN (SELECT id FROM event WHERE counterpartyId = :id)") fun deleteTagsOf(id: String)
    @Query("DELETE FROM event WHERE counterpartyId = :id") fun deleteEventsOf(id: String)

    // tags
    @Query("DELETE FROM tactic_tag WHERE eventId = :eventId") fun clearTags(eventId: Long)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun insertTags(rows: List<TagRow>)
    @Query("SELECT * FROM tactic_tag WHERE eventId = :eventId") fun tags(eventId: Long): List<TagRow>
    @Query("SELECT * FROM tactic_tag WHERE eventId IN (:ids)") fun tagsFor(ids: List<Long>): List<TagRow>

    // cases
    @Query("SELECT * FROM case_state WHERE caseId = :id") fun caseState(id: String): CaseRow?
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun saveCase(row: CaseRow)
    @Query("SELECT * FROM case_state") fun allCases(): List<CaseRow>
    @Query("SELECT * FROM case_state WHERE openedAt IS NOT NULL ORDER BY lastEventAt DESC") fun openCasesFlow(): Flow<List<CaseRow>>
    @Query("SELECT * FROM case_state WHERE caseId = :id") fun caseFlow(id: String): Flow<CaseRow?>
    @Query("DELETE FROM case_state WHERE caseId = :id") fun deleteCaseRow(id: String)
    @Query("DELETE FROM case_evidence WHERE caseId = :id") fun deleteEvidenceOf(id: String)
    @Query("DELETE FROM check_result WHERE caseId = :id") fun deleteChecksOf(id: String)

    // evidence, checks, interventions
    @Insert fun insertEvidence(rows: List<EvidenceRow>)
    @Query("SELECT * FROM case_evidence WHERE caseId = :id ORDER BY time, id") fun evidence(id: String): List<EvidenceRow>
    @Insert fun insertCheck(row: CheckRow)
    @Query("SELECT * FROM check_result WHERE caseId = :id ORDER BY time, id") fun checks(id: String): List<CheckRow>
    @Insert fun insertIntervention(row: InterventionRow): Long
    @Update fun updateIntervention(row: InterventionRow)
    @Query("SELECT * FROM intervention WHERE caseId = :id ORDER BY time, id") fun interventions(id: String): List<InterventionRow>
    @Query("SELECT * FROM intervention WHERE id = :id") fun intervention(id: Long): InterventionRow?
    @Query("SELECT * FROM intervention ORDER BY time DESC LIMIT :limit") fun recentInterventionsFlow(limit: Int): Flow<List<InterventionRow>>
    @Query("DELETE FROM intervention WHERE time < :before") fun deleteInterventionsBefore(before: Long)

    // packs
    @Insert fun insertPack(row: EvidencePackRow): Long
    @Query("SELECT * FROM evidence_pack WHERE caseId = :id ORDER BY createdAt DESC") fun packs(id: String): List<EvidencePackRow>

    // holds
    @Query("SELECT caseId FROM case_hold") fun holds(): List<String>
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun hold(row: HoldRow)
    @Query("DELETE FROM case_hold WHERE caseId = :id") fun release(id: String)

    // allies
    @Query("SELECT * FROM ally ORDER BY id") fun allies(): List<AllyRow>
    @Query("SELECT * FROM ally ORDER BY id") fun alliesFlow(): Flow<List<AllyRow>>
    @Insert fun insertAlly(row: AllyRow): Long
    @Update fun updateAlly(row: AllyRow)
    @Query("DELETE FROM ally WHERE id = :id") fun deleteAlly(id: Long)
}

@Database(
    entities = [
        CounterpartyRow::class, LinkRow::class, EventRow::class, TagRow::class, CaseRow::class,
        EvidenceRow::class, CheckRow::class, InterventionRow::class, EvidencePackRow::class, AllyRow::class, HoldRow::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class TripwireDatabase : RoomDatabase() {
    abstract fun dao(): LedgerDao

    companion object {
        private const val NAME = "tripwire.db"

        /** Opens the SQLCipher-encrypted database (PRD 11.4, 15.4). */
        fun open(context: Context): TripwireDatabase {
            System.loadLibrary("sqlcipher")
            val passphrase = DatabaseKey.passphrase(context)
            return Room.databaseBuilder(context, TripwireDatabase::class.java, NAME)
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .build()
        }

        /** SET-02: delete everything, including the database file and its key. */
        fun destroy(context: Context) {
            context.deleteDatabase(NAME)
            DatabaseKey.forget(context)
        }
    }
}

/**
 * A random 32-byte database passphrase, stored on disk encrypted with an AES-GCM key that never
 * leaves the Android Keystore. Another app, or a copy of the files, cannot open the database.
 */
object DatabaseKey {
    private const val ALIAS = "tripwire.db.key"
    private const val FILE = "db.key"

    fun passphrase(context: Context): ByteArray {
        val file = File(context.noBackupFilesDir, FILE)
        if (file.exists()) {
            val bytes = file.readBytes()
            val iv = bytes.copyOfRange(0, 12)
            val sealed = bytes.copyOfRange(12, bytes.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(128, iv))
            return cipher.doFinal(sealed)
        }
        val secret = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey())
        file.writeBytes(cipher.iv + cipher.doFinal(secret))
        return secret
    }

    fun forget(context: Context) {
        File(context.noBackupFilesDir, FILE).delete()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS)
    }

    private fun keystoreKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }
}
