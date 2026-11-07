package com.evyr.rads.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.scale
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evyr.rads.data.Verdict
import com.evyr.rads.ui.theme.CautionText
import com.evyr.rads.ui.theme.RustText
import com.evyr.rads.ui.theme.SageAccent
import com.evyr.rads.ui.theme.SageText
import com.evyr.rads.ui.theme.WarnAmber
import com.evyr.rads.ui.theme.WarnRust

/**
 * The one verdict signal used across the app: a traffic light.
 *
 * Colour is never the only cue. Lights keep the real-world order (red, amber,
 * green), the lit one carries a symbol, and the dialog pairs it with a word,
 * so it still reads for people who can't tell red from green.
 */
enum class Light(val symbol: String, val color: Color, val symbolColor: Color, val spoken: String) {
    STOP("✕", WarnRust, Color.White, "Better to skip"),
    CAUTION("!", WarnAmber, Color(0xFF2D1F10), "Be careful"),
    GO("✓", SageAccent, Color(0xFF16301C), "Okay")
}

data class LightState(val light: Light, val word: String)

/**
 * Verdict to light. Green is only for a check that fully ran and passed: if
 * the source had no data for something a condition cares about, that's amber,
 * because "go" would claim more than the app actually knows.
 */
fun lightState(
    verdict: Verdict,
    incomplete: Boolean = false,
    allergy: Boolean = false,
    estimate: Boolean = false
): LightState =
    when {
        allergy || verdict == Verdict.OVER_LIMIT -> LightState(Light.STOP, "Better to skip")
        verdict == Verdict.CAUTION -> LightState(Light.CAUTION, "Be careful")
        // AI-estimated numbers can be off, so a pass on them is never a green light.
        estimate -> LightState(Light.CAUTION, "Estimate: check it")
        incomplete -> LightState(Light.CAUTION, "Not fully checked")
        else -> LightState(Light.GO, "Okay")
    }

@Composable
fun TrafficLight(
    light: Light,
    modifier: Modifier = Modifier,
    dot: Dp = 22.dp,
    horizontal: Boolean = false,
    showSymbol: Boolean = true,
    description: String = light.spoken,
    animate: Boolean = false
) {
    val housing = Color(0xFF2B2623)
    val shape = RoundedCornerShape(dot)
    val pad = dot * 0.22f
    val gap = dot * 0.18f
    val lamps: @Composable () -> Unit = {
        // Red on top (or left), green at the bottom (or right), like the real thing.
        listOf(Light.STOP, Light.CAUTION, Light.GO).forEach { l ->
            Lamp(l, lit = l == light, size = dot, showSymbol = showSymbol, animate = animate)
        }
    }
    // Screen readers get the verdict as words; the lamps themselves are decoration.
    val spoken = modifier.semantics(mergeDescendants = true) {
        contentDescription = "Traffic light: $description"
    }
    if (horizontal) {
        Row(
            spoken.clip(shape).background(housing).padding(pad),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically
        ) { lamps() }
    } else {
        Column(
            spoken.clip(shape).background(housing).padding(pad),
            verticalArrangement = Arrangement.spacedBy(gap),
            horizontalAlignment = Alignment.CenterHorizontally
        ) { lamps() }
    }
}

/**
 * Compact verdict for tight spots like a meal header: one lamp with its symbol
 * plus a word, in a soft tint. The full three-lamp housing is too dark and too
 * small there, and an unlit lamp tells you nothing, so only the lit one shows.
 */
@Composable
fun LightChip(light: Light, modifier: Modifier = Modifier, description: String = light.spoken) {
    val word = when (light) {
        Light.STOP -> "Skip"
        Light.CAUTION -> "Careful"
        Light.GO -> "Okay"
    }
    val textColor = when (light) {
        Light.STOP -> RustText
        Light.CAUTION -> CautionText
        Light.GO -> SageText
    }
    Row(
        modifier
            .semantics(mergeDescendants = true) { contentDescription = "Traffic light: $description" }
            .clip(RoundedCornerShape(50))
            .background(light.color.copy(alpha = 0.16f))
            .padding(start = 4.dp, end = 9.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(18.dp).clip(CircleShape).background(light.color),
            contentAlignment = Alignment.Center
        ) {
            Text(light.symbol, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = light.symbolColor)
        }
        Spacer(Modifier.width(5.dp))
        Text(word, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = textColor)
    }
}

@Composable
private fun Lamp(light: Light, lit: Boolean, size: Dp, showSymbol: Boolean, animate: Boolean) {
    // The lit lamp pops in with a small spring when asked to; otherwise it just sits there.
    var arrived by remember { mutableStateOf(!animate) }
    LaunchedEffect(Unit) { arrived = true }
    val pop by animateFloatAsState(
        targetValue = if (lit && !arrived) 0.4f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "lamp"
    )
    Box(
        Modifier
            .size(size)
            .scale(pop)
            .clip(CircleShape)
            .background(if (lit) light.color else light.color.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        if (lit && showSymbol) {
            Text(
                light.symbol,
                fontSize = (size.value * 0.62f).sp,
                fontWeight = FontWeight.Bold,
                color = light.symbolColor
            )
        }
    }
}

/**
 * A waiting state in the app's own language: the three lamps light in turn.
 * Pass a [label] so screen readers get words, not a flicker.
 */
@Composable
fun LightsLoading(label: String, modifier: Modifier = Modifier, dot: Dp = 16.dp) {
    var step by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(350)
            step = (step + 1) % 3
        }
    }
    val lit = listOf(Light.STOP, Light.CAUTION, Light.GO)[step]
    TrafficLight(lit, modifier, dot = dot, horizontal = true, showSymbol = false, description = label)
}
