package com.evyr.rads.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.width
import com.evyr.rads.ui.TodayViewModel.SwapState

/**
 * "Lighter idea for next time": a few AI-written suggestions for something that
 * came up amber or red. They are ideas, not checked menu data, and say so.
 */
@Composable
fun SwapDialog(state: SwapState, onDismiss: () -> Unit) {
    val name = when (state) {
        is SwapState.Working -> state.foodName
        is SwapState.Done -> state.foodName
        is SwapState.Failed -> state.foodName
        SwapState.Idle -> ""
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Instead of $name", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            when (state) {
                is SwapState.Working -> Row(verticalAlignment = Alignment.CenterVertically) {
                    LightsLoading("Finding lighter ideas")
                    Spacer(Modifier.width(12.dp))
                    Text("Finding lighter ideas...", fontSize = 14.sp)
                }
                is SwapState.Failed -> Text(state.message, fontSize = 14.sp)
                is SwapState.Done -> Column(Modifier.verticalScroll(rememberScrollState())) {
                    state.swaps.forEach { s ->
                        if (s.where.isNotBlank()) {
                            Text(s.where, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f))
                        }
                        Text(s.order, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        if (s.why.isNotBlank()) {
                            Text(
                                s.why,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    Text(
                        "These are AI ideas, not checked menu data. Menus change, so look at the restaurant's " +
                            "nutrition info or ask before ordering, and follow your doctor's advice first.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                SwapState.Idle -> Unit
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
