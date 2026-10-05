package com.tripwire.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tripwire.core.evidence.Complainant
import com.tripwire.core.evidence.EvidencePack
import com.tripwire.core.evidence.TransactionDetails
import com.tripwire.core.model.Amount
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Journey 8: pick the case, confirm amount and time (pre-filled from payment SMS), see the
 * golden-hour countdown, call 1930, and build the complaint pack in under two minutes.
 */
@Composable
fun PaidScreen(vm: AppViewModel, initialCaseId: String?, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    val lang = settings.language
    val cases by vm.cases.collectAsState()
    val scope = rememberCoroutineScope()

    var caseId by remember { mutableStateOf(initialCaseId ?: cases.firstOrNull()?.caseId) }
    var amount by remember { mutableStateOf("") }
    var minutesAgo by remember { mutableStateOf("5") }
    var utr by remember { mutableStateOf("") }
    var handle by remember { mutableStateOf("") }
    var prefilled by remember { mutableStateOf(false) }
    var built by remember { mutableStateOf<Pair<EvidencePack, File>?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(caseId) {
        val id = caseId ?: return@LaunchedEffect
        vm.prefill(id)?.let { tx ->
            tx.amount?.let { amount = (it.paise / 100).toString() }
            minutesAgo = ((System.currentTimeMillis() - tx.time) / 60_000).coerceAtLeast(0).toString()
            utr = tx.utr.orEmpty()
            handle = tx.payeeHandle.orEmpty()
            prefilled = true
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            now = System.currentTimeMillis()
        }
    }
    val paidAt = now - (minutesAgo.toLongOrNull() ?: 0) * 60_000
    val left = ((paidAt + 3_600_000 - now) / 60_000).toInt()

    Page(Ui.t("paid.title", lang), onBack, Ui.t("btn.back", lang)) {
        // EVD-03: golden-hour countdown, and the one most important button.
        InfoCard(container = MaterialTheme.colorScheme.errorContainer) {
            Text(
                if (left > 0) Ui.t("paid.countdown", lang, "m" to left.toString()) else Ui.t("paid.expired", lang),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        BigButton(Ui.t("btn.call_1930", lang), color = MaterialTheme.colorScheme.error) {
            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:1930")))
        }

        if (cases.isNotEmpty()) {
            Text(Ui.t("paid.which", lang), style = MaterialTheme.typography.titleMedium)
            cases.forEach { c ->
                if (c.caseId == caseId) BigButton("${c.title} · ${c.family}") {}
                else QuietButton("${c.title} · ${c.family}") { caseId = c.caseId }
            }
            QuietButton(Ui.t("paid.none", lang)) { caseId = null }
        }

        if (prefilled) Text(Ui.t("paid.prefilled", lang), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
        Field(Ui.t("paid.amount", lang), amount, KeyboardType.Number) { amount = it.filter(Char::isDigit) }
        Field(Ui.t("paid.minutes_ago", lang), minutesAgo, KeyboardType.Number) { minutesAgo = it.filter(Char::isDigit) }
        Field(Ui.t("paid.handle", lang), handle, KeyboardType.Email) { handle = it.trim() }
        Field(Ui.t("paid.utr", lang), utr, KeyboardType.Number) { utr = it.filter(Char::isDigit) }

        BigButton(Ui.t("btn.build_pack", lang)) {
            scope.launch {
                built = vm.buildPack(
                    caseId,
                    Complainant(name = settings.ownerName),
                    TransactionDetails(
                        amount = amount.toLongOrNull()?.let { Amount(it * 100) },
                        time = paidAt,
                        utr = utr.ifBlank { null },
                        payeeHandle = handle.ifBlank { null },
                    ),
                )
            }
        }

        built?.let { (pack, file) ->
            Text(Ui.t("paid.pack_ready", lang), style = MaterialTheme.typography.titleMedium, color = TwColors.Ok)
            QuietButton(Ui.t("btn.share_pack", lang)) { context.startActivity(vm.shareIntent(file)) }
            QuietButton(Ui.t("btn.portal", lang)) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://cybercrime.gov.in"))) }
            InfoCard {
                Text(Ui.t("paid.script", lang), style = MaterialTheme.typography.titleMedium)
                Text(vm.callScript(pack), style = MaterialTheme.typography.bodyLarge)
            }
        }

        // EVD-07: next steps.
        InfoCard {
            Text(Ui.t("paid.next", lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            listOf("paid.next1", "paid.next2", "paid.next3", "paid.next4").forEach { Text("• " + Ui.t(it, lang), style = MaterialTheme.typography.bodyLarge) }
        }
    }
}

@Composable
private fun Field(label: String, value: String, type: KeyboardType, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = type),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        textStyle = MaterialTheme.typography.bodyLarge,
    )
}
