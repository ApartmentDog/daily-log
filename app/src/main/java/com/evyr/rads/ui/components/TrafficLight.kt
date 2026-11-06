package com.evyr.rads.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evyr.rads.data.Verdict
import com.evyr.rads.ui.theme.SageAccent
import com.evyr.rads.ui.theme.WarnAmber
import com.evyr.rads.ui.theme.WarnRust

/**
 * The one verdict signal used across the app: a traffic light.
 *
 * Colour is never the only cue. Lights keep the real-world order (red, amber,
 * green), the lit one carries a symbol, and the dialog pairs it with a word,
 * so it still reads for people who can't tell red from green.
 */
enum class Light(val symbol: String, val color: Color, val symbolColor: Color) {
    STOP("✕", WarnRust, Color.White),
    CAUTION("!", WarnAmber, Color(0xFF2D1F10)),
    GO("✓", SageAccent, Color(0xFF16301C))
}

data class LightState(val light: Light, val word: String)

/**
 * Verdict to light. Green is only for a check that fully ran and passed: if
 * the source had no data for something a condition cares about, that's amber,
 * because "go" would claim more than the app actually knows.
 */
fun lightState(verdict: Verdict, incomplete: Boolean = false, allergy: Boolean = false): LightState =
    when {
        allergy || verdict == Verdict.OVER_LIMIT -> LightState(Light.STOP, "Better to skip")
        verdict == Verdict.CAUTION -> LightState(Light.CAUTION, "Be careful")
        incomplete -> LightState(Light.CAUTION, "Not fully checked")
        else -> LightState(Light.GO, "Okay")
    }

@Composable
fun TrafficLight(
    light: Light,
    modifier: Modifier = Modifier,
    dot: Dp = 22.dp,
    horizontal: Boolean = false,
    showSymbol: Boolean = true
) {
    val housing = Color(0xFF2B2623)
    val shape = RoundedCornerShape(dot)
    val pad = dot * 0.22f
    val gap = dot * 0.18f
    val lamps: @Composable () -> Unit = {
        // Red on top (or left), green at the bottom (or right), like the real thing.
        listOf(Light.STOP, Light.CAUTION, Light.GO).forEach { l ->
            Lamp(l, lit = l == light, size = dot, showSymbol = showSymbol)
        }
    }
    if (horizontal) {
        Row(
            modifier.clip(shape).background(housing).padding(pad),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically
        ) { lamps() }
    } else {
        Column(
            modifier.clip(shape).background(housing).padding(pad),
            verticalArrangement = Arrangement.spacedBy(gap),
            horizontalAlignment = Alignment.CenterHorizontally
        ) { lamps() }
    }
}

@Composable
private fun Lamp(light: Light, lit: Boolean, size: Dp, showSymbol: Boolean) {
    Box(
        Modifier
            .size(size)
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
