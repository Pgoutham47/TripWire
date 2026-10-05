package com.tripwire.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tripwire.core.evidence.Complainant
import com.tripwire.core.evidence.EvidencePack
import com.tripwire.core.evidence.TransactionDetails
import com.tripwire.core.model.Amount
import com.tripwire.core.script.BankDef
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
    var bank by remember { mutableStateOf<BankDef?>(null) }
    var bankFromSms by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(caseId) {
        val found = caseId?.let { vm.bankFor(it) }
        bankFromSms = found != null
        if (found != null) bank = found
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
        ToneCard(Status.colors.danger) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Timer, contentDescription = null, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    if (left > 0) Ui.t("paid.left", lang, "m" to left.toString()) else Ui.t("paid.expired", lang),
                    style = if (left > 0) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
                )
            }
            if (left > 0) Text(Ui.t("paid.left_body", lang), style = MaterialTheme.typography.bodyLarge)
            BigButton(Ui.t("btn.call_1930", lang), color = MaterialTheme.colorScheme.error, icon = Icons.Filled.Call) {
                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:1930")))
            }
        }

        // EVD-03: the bank's own fraud line, read from its website, next to 1930.
        SectionHeader(Ui.t("paid.bank", lang))
        InfoCard {
            val b = bank
            if (b != null && bankFromSms) {
                Text(Ui.t("paid.bank_found", lang, "bank" to b.name), style = MaterialTheme.typography.bodyLarge)
            } else {
                Text(Ui.t("paid.which_bank", lang), style = MaterialTheme.typography.bodyLarge)
                BankChips(vm.banks, b) { bank = it; bankFromSms = false }
            }
            if (b != null) {
                BigButton(Ui.t("btn.call_bank", lang, "bank" to b.name, "n" to b.fraudLine), icon = Icons.Filled.Call) {
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + b.fraudLine.filter(Char::isDigit))))
                }
                if (bankFromSms) TextButton(onClick = { bankFromSms = false }) { Text(Ui.t("paid.other_bank", lang)) }
            }
        }

        if (cases.isNotEmpty()) {
            SectionHeader(Ui.t("paid.which", lang))
            cases.forEach { c ->
                SelectCard(c.title, "${c.family.replaceFirstChar { it.uppercase() }} · ${c.apps.joinToString(", ")}", c.caseId == caseId) { caseId = c.caseId }
            }
            SelectCard(Ui.t("paid.none", lang), null, caseId == null) { caseId = null }
        }

        SectionHeader(Ui.t("paid.details", lang))
        if (prefilled) Text(Ui.t("paid.prefilled", lang), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Field(Ui.t("paid.amount", lang), amount, KeyboardType.Number) { amount = it.filter(Char::isDigit) }
        Field(Ui.t("paid.minutes_ago", lang), minutesAgo, KeyboardType.Number) { minutesAgo = it.filter(Char::isDigit) }
        Field(Ui.t("paid.handle", lang), handle, KeyboardType.Email) { handle = it.trim() }
        Field(Ui.t("paid.utr", lang), utr, KeyboardType.Number) { utr = it.filter(Char::isDigit) }

        BigButton(Ui.t("btn.build_pack", lang), icon = Icons.Filled.Description) {
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
            ToneCard(Status.colors.safe) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(Ui.t("paid.pack_ready", lang), style = MaterialTheme.typography.titleMedium)
                }
            }
            QuietButton(Ui.t("btn.share_pack", lang), icon = Icons.Filled.Share) { context.startActivity(vm.shareIntent(file)) }
            QuietButton(Ui.t("btn.portal", lang), icon = Icons.Filled.OpenInBrowser) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://cybercrime.gov.in"))) }
            InfoCard {
                Text(Ui.t("paid.script", lang), style = MaterialTheme.typography.titleMedium)
                Text(vm.callScript(pack), style = MaterialTheme.typography.bodyLarge)
            }
        }

        // EVD-07: next steps.
        InfoCard {
            Text(Ui.t("paid.next", lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            listOf("paid.next1", "paid.next2", "paid.next3", "paid.next4").forEachIndexed { i, key ->
                Row {
                    Text(
                        "${i + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 2.dp).size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).padding(top = 2.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(Ui.t(key, lang), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BankChips(banks: List<BankDef>, selected: BankDef?, onSelect: (BankDef) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        banks.forEach { b ->
            FilterChip(
                selected = b.id == selected?.id,
                onClick = { onSelect(b) },
                label = { Text(b.name, style = MaterialTheme.typography.labelMedium) },
            )
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
        shape = MaterialTheme.shapes.medium,
        textStyle = MaterialTheme.typography.bodyLarge,
    )
}
