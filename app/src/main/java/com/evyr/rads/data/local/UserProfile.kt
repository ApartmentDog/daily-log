package com.evyr.rads.data.local

import com.evyr.rads.data.Allergen
import com.evyr.rads.data.Condition
import com.evyr.rads.data.Limits
import com.evyr.rads.data.Units
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.math.abs
import kotlin.math.ceil

/** Single source for fallbacks used when no profile exists yet. */
const val DEFAULT_FAT_WARN_GRAMS = 15.0
const val DEFAULT_CALORIE_TARGET = 2000

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1,
    val name: String = "",
    val heightCm: Double = 0.0,
    val weightKg: Double = 0.0,
    val age: Int = 0,
    val sex: String = "unspecified",
    val goal: String = "maintain",
    val activityLevel: String = "moderate",
    val useImperial: Boolean = true,
    /** Target body weight, stored in kg. 0 = not set. */
    val goalWeightKg: Double = 0.0,
    /** Desired rate of change in pounds per week (always lb, it's the common unit). */
    val rateLbsPerWeek: Double = 1.0,
    /**
     * Per-meal fat ceiling in grams. Deliberately per-meal, never a daily
     * budget — firm dietary requirement, do not aggregate.
     */
    val fatWarnGramsPerMeal: Double = DEFAULT_FAT_WARN_GRAMS,
    /** Comma-separated Condition keys the user switched on in Settings. */
    val conditions: String = "",
    /** Comma-separated Allergen keys the user switched on in Settings. */
    val allergens: String = "",
    /** Free-text allergens the user typed in that aren't in the common list. */
    val customAllergens: String = "",
    val onboarded: Boolean = false,
    /** Comma-separated Focus keys picked in onboarding. Empty = predates onboarding choices. */
    val focus: String = "",
    /** "simple" or "detailed" (see DetailLevel). */
    val detailLevel: String = "detailed",
    /**
     * True when the user entered the fat limit themselves (e.g. from their
     * doctor). False means [fatWarnGramsPerMeal] is the general starting point.
     */
    val fatLimitFromDoctor: Boolean = false,
    /** "label=value;label=value" for per-meal limits the user changed (see Limits). */
    val limitOverrides: String = ""
) {
    fun focusSet(): Set<Focus> = Focus.parse(focus)

    fun limits(): Limits = Limits.parse(limitOverrides)

    fun homeLayout(): HomeLayout {
        val f = focusSet()
        val legacy = f.isEmpty()
        val usesSafety = Focus.CONDITION in f || Focus.ALLERGY in f ||
            conditions.isNotBlank() || allergens.isNotBlank()
        val wantsNumbers = legacy || Focus.WEIGHT in f || Focus.MUSCLE in f
        val muscle = Focus.MUSCLE in f
        return HomeLayout(
            showCheckHero = usesSafety,
            showCalorieRing = wantsNumbers,
            proteinShare = if (muscle) 0.30 else 0.24,
            carbShare = if (muscle) 0.40 else 0.44,
            simple = DetailLevel.fromKey(detailLevel) == DetailLevel.SIMPLE
        )
    }

    fun bmr(): Double {
        if (weightKg <= 0 || heightCm <= 0 || age <= 0) return 0.0
        val base = (10 * weightKg) + (6.25 * heightCm) - (5 * age)
        return when (sex) {
            "male" -> base + 5
            "female" -> base - 161
            else -> base - 78
        }
    }

    fun tdee(): Double {
        val multiplier = when (activityLevel) {
            "sedentary" -> 1.2
            "light" -> 1.375
            "moderate" -> 1.55
            "active" -> 1.725
            "very_active" -> 1.9
            else -> 1.55
        }
        return bmr() * multiplier
    }

    /** 3500 kcal ≈ 1 lb, so 1 lb/week ≈ 500 kcal/day. */
    fun dailyAdjustment(): Int = when (goal) {
        "lose" -> -((rateLbsPerWeek * 3500) / 7).toInt()
        "gain" -> ((rateLbsPerWeek * 3500) / 7).toInt()
        else -> 0
    }

    fun calorieTarget(): Int {
        val t = tdee()
        if (t <= 0) return 2000
        // Never recommend below a conservative floor.
        val floor = if (sex == "male") 1500 else 1200
        return maxOf((t + dailyAdjustment()).toInt(), floor)
    }

    fun conditionSet(): Set<Condition> = Condition.parse(conditions)
    fun allergenSet(): Set<Allergen> = Allergen.parse(allergens)

    /** Pounds remaining to goal; null when no goal set. */
    fun poundsToGoal(): Double? {
        if (goalWeightKg <= 0 || weightKg <= 0) return null
        return Units.kgToLb(weightKg - goalWeightKg)
    }

    /** Whole weeks to reach goal at the chosen rate; null if not computable. */
    fun weeksToGoal(): Int? {
        val lbs = poundsToGoal() ?: return null
        if (rateLbsPerWeek <= 0) return null
        if (abs(lbs) < 0.5) return 0
        return ceil(abs(lbs) / rateLbsPerWeek).toInt()
    }
}
