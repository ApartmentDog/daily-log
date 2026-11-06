package com.evyr.rads.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.evyr.rads.ui.TodayViewModel.SwapState

/**
 * "Lighter idea for next time": up to three short, separate cards. Each is
 * a place (if there is one), what to order, and a few words on why. They are
 * AI ideas, not checked menu data, and a single quiet line says so.
 */
@Composable
fun SwapDialog(state: SwapState, onDismiss: () -> Unit) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val dim = onSurface.copy(alpha = 0.72f)
    val name = when (state) {
        is SwapState.Working -> state.foodName
        is SwapState.Done -> state.foodName
        is SwapState.Failed -> state.foodName
        SwapState.Idle -> ""
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .padding(20.dp)
                .heightIn(max = 600.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IdeaIcon(40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Lighter ideas", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = onSurface)
                    Text(
                        "instead of ${tidyName(name)}",
                        fontSize = 13.sp,
                        color = dim,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
            ) {
                when (state) {
                    is SwapState.Working -> Row(verticalAlignment = Alignment.CenterVertically) {
                        LightsLoading("Finding lighter ideas")
                        Spacer(Modifier.width(12.dp))
                        Text("Finding ideas...", fontSize = 15.sp, color = onSurface)
                    }
                    is SwapState.Failed -> Text(state.message, fontSize = 15.sp, color = onSurface)
                    is SwapState.Done -> state.swaps.forEach { s ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(Color.White, RoundedCornerShape(12.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                .padding(14.dp)
                        ) {
                            if (s.where.isNotBlank()) {
                                Text(s.where.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = dim, letterSpacing = 0.8.sp)
                                Spacer(Modifier.height(2.dp))
                            }
                            Text(s.order, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = onSurface)
                            if (s.why.isNotBlank()) {
                                Spacer(Modifier.height(3.dp))
                                Text(s.why, fontSize = 13.sp, color = dim)
                            }
                        }
                    }
                    SwapState.Idle -> Unit
                }
            }
            Spacer(Modifier.height(12.dp))
            if (state is SwapState.Done) {
                Text("AI ideas, not checked. Confirm before you order.", fontSize = 12.sp, color = dim)
                Spacer(Modifier.height(8.dp))
            }
            Text(
                "Close",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.End)
                    .accessibleClick("Close", onClick = onDismiss)
            )
        }
    }
}

/** ALL-CAPS product names from labels are hard to read; make them normal case. */
private fun tidyName(name: String): String =
    if (name.length > 3 && name == name.uppercase())
        name.lowercase().split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
    else name
