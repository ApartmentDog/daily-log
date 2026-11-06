package com.evyr.rads.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog


@Composable
fun AddEntryDialog(
    mealSlot: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, calories: Int, fat: Double, protein: Double, carbs: Double) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var calories by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var servings by remember { mutableStateOf("1") }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Text(
                "New entry — ${mealSlot.replaceFirstChar { it.uppercase() }}",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(14.dp))

            EntryField("Name", name) { name = it }
            EntryField("Calories", calories, numeric = true) { calories = it }
            EntryField("Fat g", fat, numeric = true, decimal = true) { fat = it }
            EntryField("Protein g", protein, numeric = true, decimal = true) { protein = it }
            EntryField("Carbs g", carbs, numeric = true, decimal = true) { carbs = it }
            EntryField("Servings", servings, numeric = true, decimal = true) { servings = it }

            val mult = servings.toDoubleOrNull() ?: 1.0
            if (mult != 1.0) {
                Text(
                    "= ${((calories.toIntOrNull() ?: 0) * mult).toInt()} kcal, " +
                        "${entryFmt((fat.toDoubleOrNull() ?: 0.0) * mult)}g fat",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    modifier = Modifier.padding(start = 96.dp, top = 2.dp)
                )
            }

            Spacer(Modifier.height(18.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Cancel",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    modifier = Modifier.accessibleClick("Cancel") { onDismiss() }
                )
                Spacer(Modifier.width(28.dp))
                Text(
                    "Save",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.accessibleClick("Save entry") {
                        if (name.isNotBlank()) {
                            val m = servings.toDoubleOrNull() ?: 1.0
                            onConfirm(
                                name.trim(),
                                ((calories.toIntOrNull() ?: 0) * m).toInt(),
                                entryRound1((fat.toDoubleOrNull() ?: 0.0) * m),
                                entryRound1((protein.toDoubleOrNull() ?: 0.0) * m),
                                entryRound1((carbs.toDoubleOrNull() ?: 0.0) * m)
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun EntryField(
    label: String,
    value: String,
    numeric: Boolean = false,
    decimal: Boolean = false,
    onValueChange: (String) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(
            label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            modifier = Modifier.width(96.dp)
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = if (numeric) {
                KeyboardOptions(
                    keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number
                )
            } else {
                KeyboardOptions.Default
            },
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)
        )
    }
}


private fun entryRound1(v: Double): Double = kotlin.math.round(v * 10) / 10.0

private fun entryFmt(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else String.format("%.1f", v)

