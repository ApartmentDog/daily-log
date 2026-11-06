package com.evyr.rads.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.evyr.rads.ui.TodayViewModel.MenuDish
import com.evyr.rads.ui.TodayViewModel.MenuState

/**
 * "Check a menu": every dish gets a traffic light, easiest-on-you first.
 * Numbers are AI estimates from the photo, so the list says so up front and a
 * green here is worded "Looks lighter", not "Okay".
 */
@Composable
fun MenuCheckDialog(
    state: MenuState,
    hasConditions: Boolean,
    onPickDish: (MenuDish) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Menu check", fontWeight = FontWeight.Bold) },
        text = {
            when (state) {
                is MenuState.Working -> Row(verticalAlignment = Alignment.CenterVertically) {
                    LightsLoading(state.message)
                    Spacer(Modifier.width(12.dp))
                    Text(state.message, fontSize = 14.sp)
                }
                is MenuState.Failed -> Text(state.message, fontSize = 14.sp)
                is MenuState.Done -> MenuList(state.dishes, hasConditions, onPickDish)
                MenuState.Idle -> Unit
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun MenuList(dishes: List<MenuDish>, hasConditions: Boolean, onPick: (MenuDish) -> Unit) {
    val ranked = dishes
        .map { it to dishLight(it) }
        .sortedByDescending { it.second.light.ordinal }
    Column {
        Text(
            if (hasConditions)
                "Estimated from the photo, so treat it as a guide. Tap a dish for the details."
            else
                "Estimated from the photo. Add your conditions in Setup to get a real check.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(ranked) { (dish, state) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .accessibleClick("${dish.food.name}: ${state.word}. Open details") { onPick(dish) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TrafficLight(state.light, dot = 14.dp, horizontal = true, description = state.word)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(dish.food.name, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "${state.word} - ${dish.food.calories} kcal, ${dish.food.fatGrams.toInt()} g fat",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                        )
                    }
                }
            }
        }
    }
}

private fun dishLight(d: MenuDish): LightState {
    val base = lightState(d.verdict.verdict, allergy = d.verdict.allergyHits.isNotEmpty())
    return if (base.light == Light.GO) LightState(Light.GO, "Looks lighter") else base
}
