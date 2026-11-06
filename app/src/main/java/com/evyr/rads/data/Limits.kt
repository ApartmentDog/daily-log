package com.evyr.rads.data

/**
 * Per-meal limits the user has changed from the defaults. Keyed by what is
 * measured ("sodium", "sugar", "carbs", "sat fat"), so one number applies to
 * every condition that checks it. Meal-size rules stay at their defaults.
 * The "nearing" (amber) point keeps the same proportion to the limit as the
 * default does.
 */
data class Limits(val overrides: Map<String, Double> = emptyMap()) {

    fun over(label: String, default: Double): Double =
        overrides[label]?.takeIf { it > 0 } ?: default

    fun caution(label: String, defaultCaution: Double, defaultOver: Double): Double {
        val o = overrides[label]?.takeIf { it > 0 } ?: return defaultCaution
        return o * (defaultCaution / defaultOver)
    }

    fun toCsv(): String = overrides.entries
        .filter { it.key in ADJUSTABLE && it.value > 0 }
        .joinToString(";") { "${it.key}=${it.value}" }

    companion object {
        /** Label -> (title, unit, default over-limit). Order is display order. */
        data class Item(val label: String, val title: String, val unit: String, val default: Double)

        val ITEMS = listOf(
            Item("sodium", "Sodium", "mg", Thresholds.SODIUM_OVER_MG),
            Item("sugar", "Sugar", "g", Thresholds.SUGAR_OVER_G),
            Item("carbs", "Carbs", "g", Thresholds.CARB_OVER_G),
            Item("sat fat", "Saturated fat", "g", Thresholds.SATFAT_OVER_G)
        )
        val ADJUSTABLE = ITEMS.map { it.label }.toSet()

        fun parse(csv: String): Limits = Limits(
            csv.split(";").mapNotNull { part ->
                val (k, v) = part.split("=").takeIf { it.size == 2 } ?: return@mapNotNull null
                val d = v.trim().toDoubleOrNull() ?: return@mapNotNull null
                if (k.trim() in ADJUSTABLE && d > 0) k.trim() to d else null
            }.toMap()
        )
    }
}
