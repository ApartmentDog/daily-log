package com.evyr.rads.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evyr.rads.data.local.HealthSnapshot
import com.evyr.rads.health.HealthConnectManager
import com.evyr.rads.ui.TodayViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TabSyncView(
    scrollState: ScrollState,
    status: TodayViewModel.SyncStatus,
    availability: HealthConnectManager.Availability,
    health: HealthSnapshot?,
    imperial: Boolean,
    onSync: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onInstall: () -> Unit
) {
    Column(Modifier.fillMaxWidth().verticalScroll(scrollState)) {

        SectionLabel("Health Connect")

        val (statusText, warn) = when {
            availability == HealthConnectManager.Availability.NOT_INSTALLED ->
                "Not installed" to true
            availability == HealthConnectManager.Availability.UPDATE_REQUIRED ->
                "Update required" to true
            status == TodayViewModel.SyncStatus.IDLE -> "Standing by" to false
            status == TodayViewModel.SyncStatus.SYNCING -> "Reading..." to false
            status == TodayViewModel.SyncStatus.OK -> "Connected" to false
            status == TodayViewModel.SyncStatus.NO_PERMISSION -> "Access denied" to true
            status == TodayViewModel.SyncStatus.ERROR -> "Couldn't read" to true
            else -> "Standing by" to false
        }
        StatRow("Status", statusText, warn = warn, emphasize = true)
        StatRow(
            "Last sync",
            health?.lastSyncedAt?.takeIf { it > 0 }?.let {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it))
            } ?: "Never"
        )

        Spacer(Modifier.height(4.dp))
        Hairline()
        SectionLabel("Last read")
        StatRow("Steps", health?.steps?.toString() ?: "--")
        StatRow("Exercise", health?.exerciseMinutes?.let { "$it min" } ?: "-- min")
        StatRow(
            "Weight",
            health?.weightKg?.let {
                "${com.evyr.rads.data.Units.displayWeight(it, imperial)} ${if (imperial) "lb" else "kg"}"
            } ?: "--"
        )

        Spacer(Modifier.height(4.dp))
        Hairline()
        SectionLabel("Actions")

        when (availability) {
            HealthConnectManager.Availability.NOT_INSTALLED -> {
                AppAction("Install Health Connect", onInstall)
                Spacer(Modifier.height(4.dp))
                Note(
                    "Health Connect isn't on this device. Install it, open Samsung Health once so it writes data, then come back and grant access."
                )
            }
            HealthConnectManager.Availability.UPDATE_REQUIRED -> {
                AppAction("Update Health Connect", onInstall)
                Spacer(Modifier.height(4.dp))
                Note("Health Connect is installed but too old to talk to. Update it, then grant access.")
            }
            HealthConnectManager.Availability.READY -> {
                AppAction("Pull now", onSync)
                AppAction("Grant access", onRequestPermission)
                AppAction("Open Health Connect settings", onOpenSettings)
                Spacer(Modifier.height(6.dp))
                Note(
                    if (status == TodayViewModel.SyncStatus.NO_PERMISSION)
                        "Access was denied. Use Grant access and allow Steps, Weight and Exercise. If no prompt appears, open settings and enable them there."
                    else
                        "Reads from Health Connect only. Samsung Health and the watch write into it; R.A.D.S. never talks to Samsung directly."
                )
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
    )
}

