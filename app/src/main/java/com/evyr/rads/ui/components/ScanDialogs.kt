package com.evyr.rads.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.evyr.rads.data.ScannedFood

/** When vision returns several dishes, pick which one to assess. */
@Composable
fun ScanResultPicker(
    foods: List<ScannedFood>,
    onPick: (ScannedFood) -> Unit,
    onDismiss: () -> Unit
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Text(
                "${foods.size} items detected",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = onSurface
            )
            Spacer(Modifier.height(10.dp))

            foods.forEach { f ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(f) }
                        .padding(vertical = 9.dp)
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            f.name,
                            fontSize = 14.sp,
                            color = onSurface
                        )
                        Text(
                            "${f.calories} kcal / ${f.fatGrams}g fat" +
                                (f.confidence?.let { "  ($it)" } ?: ""),
                            fontSize = 12.sp,
                            color = onSurface.copy(alpha = 0.68f)
                        )
                    }
                }
                Hairline()
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "Cancel",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = onSurface.copy(alpha = 0.72f),
                modifier = Modifier.accessibleClick("Cancel") { onDismiss() }
            )
        }
    }
}

@Composable
fun ScanStatusDialog(message: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Text(
                message,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "Close",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                modifier = Modifier.accessibleClick("Close") { onDismiss() }
            )
        }
    }
}
