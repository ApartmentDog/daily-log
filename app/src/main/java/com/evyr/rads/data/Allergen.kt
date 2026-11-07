package com.evyr.rads.data

import com.evyr.rads.data.local.FoodLogEntry

/**
 * Common allergens the user can switch on in Settings, plus any custom ones
 * they type in free-text. Kept separate from Condition: an allergy is a flat
 * "this ingredient is present" flag, always OVER_LIMIT, not a caution tier
 * that scales with amount -- the two don't share a shape.
 *
 * matchWords are what's searched for in a food's name and ingredient text,
 * word-boundary, case-insensitive. Deliberately broad (e.g. PEANUT also
 * matches "peanut butter", "peanuts") since a missed match is the worse
 * failure for an allergy.
 */
enum class Allergen(val key: String, val label: String, val matchWords: List<String>) {
    PEANUTS("peanuts", "Peanuts", listOf("peanut")),
    TREE_NUTS(
        "tree_nuts", "Tree Nuts",
        listOf(
            "almond", "cashew", "walnut", "pecan", "pistachio", "hazelnut",
            "macadamia", "brazil nut", "pine nut"
        )
    ),
    SHELLFISH(
        "shellfish", "Shellfish",
        listOf(
            "shrimp", "prawn", "crab", "lobster", "scallop", "clam", "mussel", "oyster", "crawfish",
            "crayfish", "shellfish"
        )
    ),
    FISH(
        "fish", "Fish",
        listOf(
            "salmon", "tuna", "cod", "tilapia", "halibut", "anchovy", "sardine", "fish sauce",
            "fish", "anchovies", "pollock", "haddock", "catfish", "trout", "mackerel", "herring",
            "snapper", "swordfish", "mahi mahi", "sea bass", "worcestershire"
        )
    ),
    EGGS("eggs", "Eggs", listOf("egg", "eggnog")),
    DAIRY(
        "dairy", "Milk / Dairy",
        listOf(
            "milk", "cheese", "butter", "cream", "yogurt", "whey", "casein",
            "milkshake", "buttermilk", "cheeseburger", "cheesecake", "caseinate", "ghee", "lactose"
        )
    ),
    WHEAT_GLUTEN(
        "wheat_gluten", "Wheat / Gluten",
        listOf("wheat", "gluten", "barley", "rye", "malt", "bread", "pasta", "flour tortilla")
    ),
    SOY("soy", "Soy", listOf("soy", "tofu", "edamame", "tempeh")),
    SESAME("sesame", "Sesame", listOf("sesame", "tahini"));

    companion object {
        fun fromKey(k: String): Allergen? =
            values().firstOrNull { it.key == k.trim().lowercase() }

        fun parse(csv: String?): Set<Allergen> =
            csv.orEmpty().split(',').mapNotNull { fromKey(it) }.toSet()

        fun toCsv(set: Set<Allergen>): String =
            values().filter { it in set }.joinToString(",") { it.key }
    }
}

/**
 * A user-typed allergen not in the common list ("mangoes", "MSG"). Matched
 * the same way as a common allergen: the word itself, as typed, against a
 * food's name and ingredient text.
 */
data class CustomAllergen(val label: String) {
    companion object {
        fun parseCsv(csv: String?): List<CustomAllergen> =
            csv.orEmpty().split(',').map { it.trim() }.filter { it.isNotBlank() }.map { CustomAllergen(it) }

        fun toCsv(list: List<CustomAllergen>): String =
            list.joinToString(",") { it.label.trim() }
    }
}

data class AllergyFlag(val label: String)

object AllergyChecker {

    /** The word, plus a plural ending, so "peanuts" and "eggs" match "peanut" and "egg". */
    private fun rx(word: String) = Regex("""\b${Regex.escape(word)}(?:s|es)?\b""", RegexOption.IGNORE_CASE)

    private fun matches(text: String, words: List<String>) =
        words.any { rx(it).containsMatchIn(text) }

    /**
     * Phrases that contain an allergen word without the allergen, removed before
     * matching that one allergen. Only unambiguous cases. "Non-dairy" and "gluten
     * free" are deliberately NOT here: those labels don't rule out milk derivatives
     * or wheat starch, and a missed allergy is worse than a false flag.
     */
    private val neutral: Map<Allergen, Regex> = mapOf(
        Allergen.DAIRY to Regex(
            """\b(?:(?:peanut|almond|cashew|hazelnut|cocoa|cacao|shea|sunflower|seed|nut|apple|coconut|cookie|sun) butter|butter (?:beans?|lettuce)|cream of tartar|cream soda|(?:coconut|almond|cashew|soy|oat|rice|hemp|pea|flax|macadamia|hazelnut) (?:milk|cream))\b""",
            RegexOption.IGNORE_CASE
        ),
        Allergen.FISH to Regex("""\b(?:swedish fish|goldfish|fish[- ]free)\b""", RegexOption.IGNORE_CASE),
        Allergen.EGGS to Regex("""\b(?:egg[- ]free|eggless)\b""", RegexOption.IGNORE_CASE)
    )

    /** Every allergen (common + custom) present in the given text. */
    fun check(
        text: String,
        allergens: Set<Allergen>,
        customAllergens: List<CustomAllergen>
    ): List<AllergyFlag> {
        if (text.isBlank()) return emptyList()
        val hits = mutableListOf<AllergyFlag>()
        allergens.forEach { a ->
            val cleaned = neutral[a]?.replace(text, " ") ?: text
            if (matches(cleaned, a.matchWords)) hits += AllergyFlag(a.label)
        }
        customAllergens.forEach { c -> if (matches(text, listOf(c.label))) hits += AllergyFlag(c.label) }
        return hits
    }

    /** Name + ingredients when present (a candidate before logging). */
    fun check(food: ScannedFood, allergens: Set<Allergen>, customAllergens: List<CustomAllergen>): List<AllergyFlag> =
        check(listOfNotNull(food.name, food.ingredients).joinToString(" "), allergens, customAllergens)

    /** Name only -- a saved entry doesn't keep ingredient text. */
    fun check(entry: FoodLogEntry, allergens: Set<Allergen>, customAllergens: List<CustomAllergen>): List<AllergyFlag> =
        check(entry.name, allergens, customAllergens)
}
