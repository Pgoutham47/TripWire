package com.tripwire.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tripwire.core.model.Stage

/** A scrolling page with a title row and an optional back action. */
@Composable
fun Page(title: String?, onBack: (() -> Unit)? = null, backLabel: String = "", content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (onBack != null) {
                TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("← $backLabel") }
            }
            if (title != null) Text(title, style = MaterialTheme.typography.headlineMedium)
            content()
        }
    }
}

/** Buttons are at least 56 dp tall with a text label, never an icon alone (PRD 13.4). */
@Composable
fun BigButton(text: String, modifier: Modifier = Modifier, color: Color? = null, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        colors = if (color != null) androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = color) else androidx.compose.material3.ButtonDefaults.buttonColors(),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun QuietButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun InfoCard(modifier: Modifier = Modifier, container: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = container ?: MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

/** Risk in words as well as colour: colour is never the only signal (PRD 13.4). */
@Composable
fun RiskLabel(risk: Int, lang: String) {
    val (key, color) = when {
        risk >= 60 -> "risk.high" to MaterialTheme.colorScheme.error
        risk >= 40 -> "risk.medium" to TwColors.Caution
        else -> "risk.low" to MaterialTheme.colorScheme.secondary
    }
    Text(Ui.t(key, lang), color = color, style = MaterialTheme.typography.titleMedium)
}

/** The six script stages as a bar, with the current one named. */
@Composable
fun StageBar(stage: Stage, stageName: String, lang: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Stage.entries.forEach { s ->
                val reached = s.number <= stage.number
                Surface(
                    modifier = Modifier.weight(1f).heightIn(min = 10.dp),
                    shape = RoundedCornerShape(5.dp),
                    color = if (reached) (if (s.number >= Stage.COMMITMENT.number) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    else MaterialTheme.colorScheme.surfaceVariant,
                ) {}
            }
        }
        Text(Ui.t("timeline.stage", lang, "stage" to stageName), style = MaterialTheme.typography.bodyMedium)
    }
}
