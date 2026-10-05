package com.tripwire.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tripwire.core.model.Stage

/** A scrolling page with a back action and a large title. */
@Composable
fun Page(title: String?, onBack: (() -> Unit)? = null, backLabel: String = "", content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.systemBarsPadding().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (onBack != null) {
                TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(start = 4.dp, end = 12.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(backLabel, style = MaterialTheme.typography.labelLarge)
                }
            } else {
                Spacer(Modifier.height(16.dp))
            }
            if (title != null) Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            content()
        }
    }
}

/** Buttons are at least 56 dp tall with a text label, never an icon alone (PRD 13.4). */
@Composable
fun BigButton(text: String, modifier: Modifier = Modifier, color: Color? = null, icon: ImageVector? = null, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = MaterialTheme.shapes.medium,
        colors = if (color != null) ButtonDefaults.buttonColors(containerColor = color) else ButtonDefaults.buttonColors(),
    ) { ButtonContent(text, icon) }
}

@Composable
fun QuietButton(text: String, modifier: Modifier = Modifier, color: Color? = null, icon: ImageVector? = null, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, color ?: MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color ?: MaterialTheme.colorScheme.primary),
    ) { ButtonContent(text, icon) }
}

@Composable
private fun ButtonContent(text: String, icon: ImageVector?) {
    if (icon != null) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
    }
    Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
}

/** A card on the page background. Text inside always uses the colour that belongs to the container. */
@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content_: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    val m = modifier.fillMaxWidth()
    val body: @Composable () -> Unit = {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
    if (onClick != null) Surface(onClick = onClick, modifier = m, shape = shape, color = container, contentColor = content_, content = body)
    else Surface(modifier = m, shape = shape, color = container, contentColor = content_, content = body)
}

/** A card tinted with a status tone. */
@Composable
fun ToneCard(tone: Tone, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) =
    InfoCard(modifier, tone.container, tone.onContainer, onClick, content)

/** An icon in a tinted circle, used to anchor cards and rows. */
@Composable
fun IconBubble(icon: ImageVector, tint: Color, background: Color, size: Dp = 48.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/** A small heading above a group of cards. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 8.dp, start = 4.dp),
    )
}

/** A tappable row with an icon, a title, an optional detail line and a chevron. */
@Composable
fun NavRow(icon: ImageVector, title: String, detail: String? = null, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBubble(icon, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f), 44.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Pick one of a few options. Each option is a 56 dp pill on one line (PRD 13.4). */
@Composable
fun ChoiceRow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (key, label) ->
            val on = key == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .selectable(selected = on, role = Role.RadioButton) { onSelect(key) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}

/** The tone for a risk level. */
@Composable
fun riskTone(risk: Int): Tone = when {
    risk >= 60 -> Status.colors.danger
    risk >= 40 -> Status.colors.caution
    else -> Status.colors.safe
}

/** Risk in words and an icon as well as colour: colour is never the only signal (PRD 13.4). */
@Composable
fun RiskLabel(risk: Int, lang: String) {
    val tone = riskTone(risk)
    val (key, icon) = when {
        risk >= 60 -> "risk.high" to Icons.Filled.ReportProblem
        risk >= 40 -> "risk.medium" to Icons.Filled.ErrorOutline
        else -> "risk.low" to Icons.Filled.VerifiedUser
    }
    Row(
        Modifier.clip(CircleShape).background(tone.container).padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tone.onContainer, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(Ui.t(key, lang), style = MaterialTheme.typography.labelMedium, color = tone.onContainer, maxLines = 1)
    }
}

/** The six script stages as a bar, with the current one named and counted. */
@Composable
fun StageBar(stage: Stage, stageName: String, lang: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Stage.entries.forEach { s ->
                val reached = s.number <= stage.number
                val color = when {
                    !reached -> MaterialTheme.colorScheme.outlineVariant
                    s.number >= Stage.COMMITMENT.number -> Status.colors.danger.main
                    s.number >= Stage.GROOMING.number -> Status.colors.caution.main
                    else -> MaterialTheme.colorScheme.primary
                }
                Box(Modifier.weight(1f).height(8.dp).clip(CircleShape).background(color))
            }
        }
        Text(
            Ui.t("timeline.stage", lang, "n" to stage.number.toString(), "total" to Stage.entries.size.toString(), "stage" to stageName.replaceFirstChar { it.uppercase() }),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Tactics found in a message, as small tinted chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TacticChips(tactics: List<String>) {
    val tone = Status.colors.danger
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        tactics.forEach {
            Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                color = tone.onContainer,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(tone.container).padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

/** A clickable card-like row for choosing one item from a list, with a radio mark. */
@Composable
fun SelectCard(title: String, detail: String?, selected: Boolean, onClick: () -> Unit) {
    val border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        border = border,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}
