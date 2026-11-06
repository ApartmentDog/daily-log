package com.evyr.rads.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.evyr.rads.ui.TodayViewModel.MenuDish
import com.evyr.rads.ui.TodayViewModel.MenuState

/**
 * Menu mode: at a restaurant, photograph the menu (a few pages is fine) and get
 * the dishes sorted into best picks, be careful, and skip.
 *
 * The numbers are AI estimates read from the photos, so "best picks" says
 * "looks lighter", never "safe", and the screen says so at the top.
 */
@Composable
fun MenuModeDialog(
    state: MenuState,
    hasConditions: Boolean,
    onTakePhoto: () -> Unit,
    onPickPhoto: () -> Unit,
    onAnalyze: (restaurant: String) -> Unit,
    onPickDish: (MenuDish) -> Unit,
    onBackToPhotos: () -> Unit,
    onRestart: () -> Unit,
    onDismiss: () -> Unit
) {
    var restaurant by remember { mutableStateOf("") }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Menu mode", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                Spacer(Modifier.height(8.dp))
                when (state) {
                    is MenuState.Capture -> CaptureStep(
                        pages = state.pages,
                        restaurant = restaurant,
                        onRestaurant = { restaurant = it },
                        onTakePhoto = onTakePhoto,
                        onPickPhoto = onPickPhoto,
                        onAnalyze = { onAnalyze(restaurant) }
                    )
                    is MenuState.Working -> Row(
                        Modifier.padding(top = 24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LightsLoading(state.message)
                        Spacer(Modifier.width(14.dp))
                        Text(state.message, fontSize = 16.sp)
                    }
                    is MenuState.Failed -> Column {
                        Text(state.message, fontSize = 16.sp, modifier = Modifier.padding(top = 16.dp))
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onBackToPhotos) { Text("Back to photos") }
                    }
                    is MenuState.Done -> Results(state.dishes, hasConditions, onPickDish, onRestart)
                    MenuState.Idle -> Unit
                }
            }
        }
    }
}

@Composable
private fun CaptureStep(
    pages: Int,
    restaurant: String,
    onRestaurant: (String) -> Unit,
    onTakePhoto: () -> Unit,
    onPickPhoto: () -> Unit,
    onAnalyze: () -> Unit
) {
    val dim = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Photograph each page of the menu. Lay it flat, get the whole page in, and keep the light on it.",
            fontSize = 15.sp,
            color = dim
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = restaurant,
            onValueChange = onRestaurant,
            label = { Text("Restaurant (optional)") },
            supportingText = { Text("Helps it use that place's real nutrition info") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Text(
            if (pages == 0) "No pages yet" else if (pages == 1) "1 page added" else "$pages pages added",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onTakePhoto, modifier = Modifier.weight(1f)) {
                Text(if (pages == 0) "Take photo" else "Add page")
            }
            OutlinedButton(onClick = onPickPhoto, modifier = Modifier.weight(1f)) { Text("From gallery") }
        }
        Spacer(Modifier.height(20.dp))
        Button(onClick = onAnalyze, enabled = pages > 0, modifier = Modifier.fillMaxWidth()) {
            Text("Show me what's okay", fontSize = 16.sp, modifier = Modifier.padding(vertical = 4.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Nothing is logged. Your conditions and limits are applied on your phone. " +
                "The photos go to Google Gemini with your own key.",
            fontSize = 12.sp,
            color = dim
        )
    }
}

@Composable
private fun Results(
    dishes: List<MenuDish>,
    hasConditions: Boolean,
    onPick: (MenuDish) -> Unit,
    onRestart: () -> Unit
) {
    val dim = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
    val rated = dishes.map { it to dishLight(it) }
    val best = rated.filter { it.second.light == Light.GO }
    val careful = rated.filter { it.second.light == Light.CAUTION }
    val skip = rated.filter { it.second.light == Light.STOP }
    var showRest by remember { mutableStateOf(best.isEmpty()) }

    Column(Modifier.fillMaxSize()) {
        Text(
            if (hasConditions)
                "Estimated from the photos, so treat it as a guide, not a guarantee. Tap a dish for details."
            else
                "Estimated from the photos. Add your conditions in Setup to get a real check.",
            fontSize = 13.sp,
            color = dim
        )
        Spacer(Modifier.height(10.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            item {
                Text(
                    "Best picks (${best.size})",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
                )
                if (best.isEmpty()) {
                    Text(
                        "Nothing on this menu looks clearly okay. The closest ones are below. " +
                            "Ask for changes like grilled instead of fried, sauce on the side, or a smaller size.",
                        fontSize = 14.sp,
                        color = dim
                    )
                }
            }
            items(best) { (dish, st) -> DishRow(dish, st, onPick) }
            if (careful.isNotEmpty() || skip.isNotEmpty()) {
                item {
                    Text(
                        if (showRest) "Hide the rest" else "Show the rest (${careful.size + skip.size})",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .accessibleClick(if (showRest) "Hide the rest" else "Show the rest") { showRest = !showRest }
                    )
                }
            }
            if (showRest) {
                if (careful.isNotEmpty()) {
                    item {
                        Text(
                            "Be careful (${careful.size})",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                        )
                    }
                    items(careful) { (dish, st) -> DishRow(dish, st, onPick) }
                }
                if (skip.isNotEmpty()) {
                    item {
                        Text(
                            "Better to skip (${skip.size})",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                        )
                    }
                    items(skip) { (dish, st) -> DishRow(dish, st, onPick) }
                }
            }
        }
        TextButton(onClick = onRestart) { Text("Start over with new photos") }
    }
}

@Composable
private fun DishRow(dish: MenuDish, st: LightState, onPick: (MenuDish) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .accessibleClick("${dish.food.name}: ${st.word}. Open details") { onPick(dish) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrafficLight(st.light, dot = 14.dp, horizontal = true, description = st.word)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(dish.food.name, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(
                if (st.light == Light.GO)
                    "${dish.food.calories} kcal, ${dish.food.fatGrams.toInt()} g fat"
                else plainSentence(dish.food, dish.verdict, st),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
            )
        }
    }
}

private fun dishLight(d: MenuDish): LightState {
    val base = lightState(d.verdict.verdict, allergy = d.verdict.allergyHits.isNotEmpty())
    return if (base.light == Light.GO) LightState(Light.GO, "Looks lighter") else base
}
