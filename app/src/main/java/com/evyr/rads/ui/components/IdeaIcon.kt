package com.evyr.rads.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A lightbulb inside a thought cloud: the "Lighter idea" symbol. Drawn in code
 * so it stays sharp at any size and needs no image file. Decorative; the
 * button around it carries the spoken label.
 */
@Composable
fun IdeaIcon(size: Dp = 30.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        // Everything is laid out on a 100 x 100 grid, then scaled.
        val u = this.size.minDimension / 100f
        fun o(x: Float, y: Float) = Offset(x * u, y * u)
        fun circle(x: Float, y: Float, r: Float) =
            Path().apply { addOval(Rect(o(x, y), r * u)) }

        val outline = Color(0xFF9AA5B1)
        val cloudFill = Color(0xFFFFFFFF)
        val line = 2.2f * u

        // Cloud: a union of circles so it has one clean outline.
        val cloud = listOf(
            circle(30f, 46f, 15f), circle(46f, 30f, 17f), circle(66f, 30f, 16f),
            circle(80f, 46f, 14f), circle(66f, 58f, 16f), circle(44f, 58f, 15f)
        ).reduce { a, b -> Path.combine(PathOperation.Union, a, b) }
        drawPath(cloud, cloudFill)
        drawPath(cloud, outline, style = Stroke(line))

        // Thought bubbles trailing off the lower left.
        drawCircle(cloudFill, 6f * u, o(24f, 82f))
        drawCircle(outline, 6f * u, o(24f, 82f), style = Stroke(line))
        drawCircle(cloudFill, 3.5f * u, o(11f, 92f))
        drawCircle(outline, 3.5f * u, o(11f, 92f), style = Stroke(line))

        // Bulb glass with a warm glow.
        val glow = Color(0xFFFFE6A3)
        drawCircle(glow, 15f * u, o(56f, 40f))
        drawCircle(Color(0xFFFFF6DC), 9f * u, o(56f, 40f))
        drawCircle(outline, 15f * u, o(56f, 40f), style = Stroke(line))

        // Neck and screw base.
        val metal = Color(0xFF7D8896)
        drawRect(metal, o(50f, 53f), Size(12f * u, 9f * u))
        drawRect(Color.White, o(50f, 56.2f), Size(12f * u, 1.2f * u))
        drawRect(Color.White, o(50f, 59.2f), Size(12f * u, 1.2f * u))

        // Filament.
        val amber = Color(0xFFD4932A)
        val fil = Stroke(width = 1.8f * u, cap = StrokeCap.Round)
        drawLine(amber, o(52f, 53f), o(52f, 42f), strokeWidth = 1.8f * u, cap = StrokeCap.Round)
        drawLine(amber, o(60f, 53f), o(60f, 42f), strokeWidth = 1.8f * u, cap = StrokeCap.Round)
        drawLine(amber, o(52f, 42f), o(56f, 36f), strokeWidth = 1.8f * u, cap = StrokeCap.Round)
        drawLine(amber, o(60f, 42f), o(56f, 36f), strokeWidth = 1.8f * u, cap = StrokeCap.Round)
        drawCircle(amber, 2f * u, o(56f, 35.5f), style = fil)
    }
}
