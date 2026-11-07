package com.evyr.rads.ui.theme

import androidx.compose.ui.graphics.Color

// --- Warm/organic palette ---
// Replaces the amber-terminal/device-panel look. Values are the real hex
// codes from the original Gut Check PWA (gut-check-v3-1.html's :root CSS
// vars), not approximated from screenshots. Single theme only -- the PWA
// never actually defined dark-mode colors (its theme toggle was stubbed,
// never wired to real values), so RADS renders this one palette regardless
// of system theme rather than inventing a dark variant that would drift
// from the source.

val CreamBg = Color(0xFFF5EAD8)          // --bg
val CreamCard = Color(0xFFFDF3E7)        // --surface
val CreamCard2 = Color(0xFFF0E2CA)       // --surface2
val CreamCardBorder = Color(0xFFD9C4A8)  // --border
val ClayText = Color(0xFF2D1F10)         // --text
val ClayTextSoft = Color(0xFF6B4F35)     // --text-soft
val ClayTextDim = Color(0xFF9A7A5A)      // --muted
// Accent family moved off orange/red: those hues belong to the traffic light and
// warnings. The names stay (Terracotta*) so call sites don't churn; the values are indigo.
val Terracotta = Color(0xFF4F5FB3)       // accent (was terracotta D4704A)
val TerracottaSoft = Color(0x244F5FB3)   // accent-soft
val WarnAmber = Color(0xFFD4932A)        // --warn
val WarnAmberSoft = Color(0x21D4932A)    // --warn-soft
// Darker twins for TEXT on the cream/white surfaces (4.5:1 or better there).
// The brighter colours above stay for fills, lamps and large shapes.
val TerracottaText = Color(0xFF3A4890)
val RustText = Color(0xFFAD4848)
val SageText = Color(0xFF3F7249)
val CautionText = Color(0xFF8A5C0E)
val WarnRust = Color(0xFFD95F5F)         // --danger
val WarnRustSoft = Color(0x21D95F5F)     // --danger-soft
val SageAccent = Color(0xFF6AB87A)       // --green
val SageAccentSoft = Color(0x216AB87A)   // --green-soft
val InfoBlue = Color(0xFF7AB8C8)         // --blue

// From the approved mockup rather than the PWA: page background a touch
// lighter than --bg, and the muted tone for inactive bottom-nav items.
val PageBg = Color(0xFFFBF4EC)
val NavInactive = Color(0xFFB8A88E)

// --- Per-meal accent tints ---
// One color family per meal slot so the accordion cards on the log screen
// read as visually distinct at a glance, matching the approved mockup.

// Breakfast — warm amber
val MealBreakfastBg = Color(0xFFFDEEE0)
val MealBreakfastBorder = Color(0xFFF0D4B8)
val MealBreakfastChip = Color(0xFFFBD9AE)
val MealBreakfastText = Color(0xFFA5732E)

// Lunch — sage green
val MealLunchBg = Color(0xFFEAF3E0)
val MealLunchBorder = Color(0xFFD3E4C0)
val MealLunchChip = Color(0xFFD3E4C0)
val MealLunchText = Color(0xFF6B8A50)

// Dinner — dusty terracotta
val MealDinnerBg = Color(0xFFFBE5DD)
val MealDinnerBorder = Color(0xFFF0C9BA)
val MealDinnerChip = Color(0xFFF5D4C4)
val MealDinnerText = Color(0xFFB5674A)

// Snack — dusty rose
val MealSnackBg = Color(0xFFF5E3EA)
val MealSnackBorder = Color(0xFFE8C7D6)
val MealSnackChip = Color(0xFFEBCEDA)
val MealSnackText = Color(0xFFA5567D)
