package com.evyr.rads.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shared building blocks for every screen. Replaces TerminalParts.kt --
 * sentence-case copy instead of shouting labels, MaterialTheme.colorScheme
 * (adapts to light/dark) instead of hardcoded terminal colors, default
 * typography instead of monospace everywhere.
 */

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline)
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
        modifier = modifier.padding(top = 14.dp, bottom = 6.dp)
    )
}

@Composable
fun StatRow(
    label: String,
    value: String,
    warn: Boolean = false,
    emphasize: Boolean = false
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            modifier = Modifier.width(110.dp)
        )
        Text(
            text = value,
            fontSize = if (emphasize) 16.sp else 13.sp,
            fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Normal,
            color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Horizontal bar meter, e.g. calories consumed vs target. */
@Composable
fun BarMeter(
    label: String,
    current: Double,
    target: Double,
    unit: String,
    warn: Boolean = false
) {
    val pct = if (target > 0) (current / target).coerceIn(0.0, 1.0) else 0.0
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            modifier = Modifier.width(76.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(pct.toFloat())
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "${current.toInt()}/${target.toInt()}$unit",
            fontSize = 12.sp,
            color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * Pill-style tab row. Single navigation surface -- the old app had this AND
 * a separate bottom hardware-button row (TerminalChrome) both acting as
 * navigation for overlapping destinations; this is now the only one.
 */
@Composable
fun AppTabBar(
    tabs: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        tabs.forEach { tab ->
            val isSel = tab == selected
            Text(
                text = tab,
                fontSize = 13.sp,
                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                modifier = Modifier
                    .clickable { onSelect(tab) }
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
            Spacer(Modifier.width(4.dp))
        }
    }
}

/**
 * A tap target that meets the 48dp minimum, announces itself as a button, and
 * says what it does ("Previous day") to a screen reader. Use it for small text
 * and icon actions; content is centred inside the larger hit area.
 */
fun Modifier.accessibleClick(label: String, enabled: Boolean = true, onClick: () -> Unit): Modifier =
    this.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
        .clickable(
            enabled = enabled,
            onClickLabel = label,
            role = androidx.compose.ui.semantics.Role.Button,
            onClick = onClick
        )
        .wrapContentSize(androidx.compose.ui.Alignment.Center)

/**
 * Dialog footer: a quiet text action on the left, one filled primary action
 * on the right. Min 48dp tall so both are easy thumb targets.
 */
@Composable
fun DialogActions(
    secondaryText: String?,
    onSecondary: () -> Unit,
    primaryText: String,
    onPrimary: () -> Unit,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    primaryEnabled: Boolean = true
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        if (secondaryText != null) {
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onSecondary)
                    .padding(horizontal = 16.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text(
                    secondaryText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            Spacer(Modifier.width(8.dp))
        }
        Box(
            Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (primaryEnabled) primaryColor else primaryColor.copy(alpha = 0.35f))
                .clickable(enabled = primaryEnabled, onClick = onPrimary),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Text(
                primaryText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
fun AppAction(text: String, onClick: () -> Unit, warn: Boolean = false) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp)
    )
}
