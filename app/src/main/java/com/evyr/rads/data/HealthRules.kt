package com.evyr.rads.data

import com.evyr.rads.data.local.FoodLogEntry

/**
 * Things in food that commonly aggravate specific conditions. Detected from a
 * food's name and ingredient text, plus tags an AI estimate reports directly.
 * Keyword detection is a heuristic: it catches what the name or ingredient
 * list says, not what a restaurant quietly puts in the fryer.
 */
enum class Trigger(val key: String, val label: String) {
    CAFFEINE("caffeine", "caffeine"),
    ALCOHOL("alcohol", "alcohol"),
    SPICY("spicy", "spicy"),
    FRIED("fried", "fried / greasy"),
    ACIDIC("acidic", "acidic (tomato / citrus)"),
    CARBONATED("carbonated", "carbonated"),
    CHOCOLATE_MINT("chocolate_mint", "chocolate / mint"),
    DAIRY("dairy", "dairy"),
    GLUTEN("gluten", "gluten"),
    HIGH_PURINE("high_purine", "high purine"),
    HIGH_FODMAP("high_fodmap", "high FODMAP"),
    FRUCTOSE("fructose", "added fructose / sugary drink");

    companion object {
        fun fromKey(k: String): Trigger? =
            values().firstOrNull { it.key == k.trim().lowercase() }

        fun parse(csv: String?): Set<Trigger> =
            csv.orEmpty().split(',').mapNotNull { fromKey(it) }.toSet()
    }
}

/**
 * Conditions the user can switch on in Settings. Each lists the triggers it
 * cares about and how seriously to treat them. Numeric checks (sodium, sugar,
 * carbs, saturated fat, meal size) live in VerdictRules.
 */
enum class Condition(
    val key: String,
    val label: String,
    /** Compact tag shown on log rows, e.g. "GERD". */
    val short: String,
    val watches: String,
    val triggers: Map<Trigger, Verdict>
) {
    REFLUX(
        "reflux", "ACID REFLUX / GERD", "GERD",
        "caffeine, alcohol, spicy, fried, acidic, carbonated, chocolate/mint, large meals",
        mapOf(
            Trigger.CAFFEINE to Verdict.CAUTION,
            Trigger.ALCOHOL to Verdict.CAUTION,
            Trigger.SPICY to Verdict.CAUTION,
            Trigger.FRIED to Verdict.CAUTION,
            Trigger.ACIDIC to Verdict.CAUTION,
            Trigger.CARBONATED to Verdict.CAUTION,
            Trigger.CHOCOLATE_MINT to Verdict.CAUTION
        )
    ),
    HIATAL_HERNIA(
        "hiatal_hernia", "HIATAL HERNIA", "HERNIA",
        "large meals, carbonated, fried, spicy, caffeine, alcohol, acidic",
        mapOf(
            Trigger.CARBONATED to Verdict.CAUTION,
            Trigger.FRIED to Verdict.CAUTION,
            Trigger.SPICY to Verdict.CAUTION,
            Trigger.CAFFEINE to Verdict.CAUTION,
            Trigger.ALCOHOL to Verdict.CAUTION,
            Trigger.ACIDIC to Verdict.CAUTION
        )
    ),
    IBS(
        "ibs", "IBS", "IBS",
        "high FODMAP, fried, spicy, caffeine, alcohol, carbonated",
        mapOf(
            Trigger.HIGH_FODMAP to Verdict.CAUTION,
            Trigger.FRIED to Verdict.CAUTION,
            Trigger.SPICY to Verdict.CAUTION,
            Trigger.CAFFEINE to Verdict.CAUTION,
            Trigger.ALCOHOL to Verdict.CAUTION,
            Trigger.CARBONATED to Verdict.CAUTION
        )
    ),
    FATTY_LIVER(
        "fatty_liver", "FATTY LIVER", "LIVER",
        "alcohol, added sugar and fructose, saturated fat, fried",
        mapOf(
            Trigger.ALCOHOL to Verdict.OVER_LIMIT,
            Trigger.FRUCTOSE to Verdict.CAUTION,
            Trigger.FRIED to Verdict.CAUTION
        )
    ),
    DIABETES(
        "diabetes", "DIABETES / PREDIABETES", "DIABETES",
        "carbs and sugar per meal, sugary drinks",
        mapOf(Trigger.FRUCTOSE to Verdict.CAUTION)
    ),
    HYPERTENSION(
        "hypertension", "HIGH BLOOD PRESSURE", "BP",
        "sodium per meal, alcohol",
        mapOf(Trigger.ALCOHOL to Verdict.CAUTION)
    ),
    CHOLESTEROL(
        "cholesterol", "HIGH CHOLESTEROL", "CHOL",
        "saturated fat per meal, fried food",
        mapOf(Trigger.FRIED to Verdict.CAUTION)
    ),
    GOUT(
        "gout", "GOUT", "GOUT",
        "high-purine foods (organ meat, some seafood), alcohol especially beer, sugary drinks",
        mapOf(
            Trigger.HIGH_PURINE to Verdict.CAUTION,
            Trigger.ALCOHOL to Verdict.CAUTION,
            Trigger.FRUCTOSE to Verdict.CAUTION
        )
    ),
    LACTOSE(
        "lactose", "LACTOSE INTOLERANCE", "LACTOSE",
        "dairy",
        mapOf(Trigger.DAIRY to Verdict.CAUTION)
    ),
    CELIAC(
        "celiac", "CELIAC / GLUTEN-FREE", "CELIAC",
        "gluten — wheat, barley, rye, most breads, pasta and batters",
        mapOf(Trigger.GLUTEN to Verdict.OVER_LIMIT)
    ),
    GALLBLADDER(
        "gallbladder", "GALLBLADDER DISEASE", "GALLBLADDER",
        "fried food, saturated fat, and large meals — the standard triggers for gallbladder attacks",
        mapOf(Trigger.FRIED to Verdict.CAUTION)
    );

    companion object {
        fun fromKey(k: String): Condition? =
            values().firstOrNull { it.key == k.trim().lowercase() }

        fun parse(csv: String?): Set<Condition> =
            csv.orEmpty().split(',').mapNotNull { fromKey(it) }.toSet()

        /** Stable, ordinal-ordered CSV for storage. */
        fun toCsv(set: Set<Condition>): String =
            values().filter { it in set }.joinToString(",") { it.key }
    }
}

/**
 * Per-meal thresholds, including what's already logged in the meal.
 * These are general-guidance defaults, not anyone's prescription.
 */
object Thresholds {
    const val SODIUM_CAUTION_MG = 600.0
    const val SODIUM_OVER_MG = 900.0
    const val SUGAR_CAUTION_G = 15.0
    const val SUGAR_OVER_G = 25.0
    const val CARB_CAUTION_G = 45.0
    const val CARB_OVER_G = 60.0
    const val SATFAT_CAUTION_G = 5.0
    const val SATFAT_OVER_G = 10.0
    const val REFLUX_MEAL_KCAL_CAUTION = 700
    const val REFLUX_MEAL_KCAL_OVER = 950
    const val HERNIA_MEAL_KCAL_CAUTION = 600
    const val HERNIA_MEAL_KCAL_OVER = 850
    const val GALLBLADDER_MEAL_KCAL_CAUTION = 600
    const val GALLBLADDER_MEAL_KCAL_OVER = 850
}

object TriggerDetector {

    private fun rx(pattern: String) =
        Regex("""\b(?:$pattern)\b""", RegexOption.IGNORE_CASE)

    // Shared exclusions: soft drinks named "beer" and plant milks trip several checks.
    private const val SOFT_BEERS = """root beer|ginger beer|ginger ale"""
    private const val PLANT_MILK = """(?:oat|almond|soy|coconut|rice|cashew|hemp|pea) milk(?:\s+\w+)?"""
    private const val LOW_FODMAP_MILK = """(?:oat|almond|coconut|rice|hemp|pea) milk(?:\s+\w+)?"""

    /** Phrases removed before matching, so "peanut butter" isn't dairy. */
    private val strip: Map<Trigger, Regex> = mapOf(
        Trigger.CAFFEINE to rx("""decaf(?:feinated)?(?:\s+\w+)?|caffeine[- ]free(?:\s+\w+)?|herbal teas?|(?:chamomile|rooibos|peppermint|ginger|hibiscus) teas?"""),
        Trigger.ALCOHOL to rx("""non[- ]?alcoholic(?:\s+\w+)?|alcohol[- ]free(?:\s+\w+)?|(?:red |white |rice )?wine vinegar|$SOFT_BEERS|beer[- ]battered|scotch eggs?|butterscotch"""),
        Trigger.SPICY to rx("""buffalo mozzarella|mild(?:\s+\w+)?"""),
        Trigger.FRIED to rx("""air[- ]fried(?:\s+\w+)?|baked (?:chips|fries)"""),
        Trigger.DAIRY to rx("""(?:peanut|almond|cashew|apple|cocoa|shea|sunflower|seed|nut) butter|$PLANT_MILK|dairy[- ]free(?:\s+\w+)?|non[- ]?dairy(?:\s+\w+)?|lactose[- ]free(?:\s+\w+)?|vegan (?:cheese|butter|cream|milk)|coconut cream|cream of tartar|cream soda"""),
        Trigger.GLUTEN to rx("""$SOFT_BEERS|gluten[- ]free(?:\s+\w+)?|corn tortillas?|rice (?:noodles?|cakes?|crackers?|paper|bread)|glass noodles?|zucchini noodles?|lettuce wraps?"""),
        Trigger.HIGH_PURINE to rx("""$SOFT_BEERS|beer[- ]battered|kidney beans?"""),
        Trigger.HIGH_FODMAP to rx("""$LOW_FODMAP_MILK|green beans?|coffee beans?|jelly beans?|garlic[- ]infused oil"""),
        Trigger.FRUCTOSE to rx("""diet (?:soda|coke|pepsi|cola|drink)s?|(?:coke|pepsi) zero|zero sugar(?:\s+\w+)?|sugar[- ]free(?:\s+\w+)?|unsweetened(?:\s+\w+)?"""),
        Trigger.CARBONATED to rx("""beer[- ]battered""")
    )

    private val patterns: Map<Trigger, Regex> = mapOf(
        Trigger.CAFFEINE to rx("""coffee|espresso|lattes?|cappuccinos?|americanos?|mochas?|macchiatos?|frappuccinos?|cold brew|energy drinks?|red bull|monster energy|colas?|coke|pepsi|dr\.? ?pepper|mountain dew|teas?|matcha|chai|yerba mate|guarana|caffeine|pre[- ]?workout"""),
        Trigger.ALCOHOL to rx("""beers?|wines?|vodka|whiske?y|bourbon|scotch|rum|tequila|mezcal|gin|brandy|cognac|liqueurs?|sake|soju|hard (?:seltzer|cider|lemonade)s?|margaritas?|mimosas?|sangria|cocktails?|martinis?|mojitos?|daiquiris?|pina coladas?|bloody marys?|ipas?|lagers?|stouts?|pilsners?|champagne|prosecco|spiked"""),
        Trigger.SPICY to rx("""spicy|hot sauce|hot wings|hot chicken|nashville hot|flamin'?g? hot|sriracha|jalape(?:ñ|n)os?|habaneros?|serranos?|chipotles?|cayenne|chil(?:i|e|li)(?:e?s)?|buffalo|ghost peppers?|red pepper flakes|crushed red pepper|curry|curries|vindaloo|gochujang|harissa|wasabi|pepper jack|kimchi|szechuan|sichuan|jerk"""),
        Trigger.FRIED to rx("""fried|deep[- ]fried|crispy|tempura|fries|french fries|tater tots?|hash ?browns?|nuggets?|onion rings?|battered|breaded|doughnuts?|donuts?|fritters?|funnel cakes?|tenders|chicken (?:strips|fingers)|egg rolls?|spring rolls?|corn dogs?|churros?|chips|mozzarella sticks|schnitzel|katsu|fish and chips|hush ?puppies|chimichangas?|taquitos?|empanadas?|samosas?|pakoras?|wings?"""),
        Trigger.ACIDIC to rx("""tomato(?:es)?|marinara|pizza|salsa|ketchup|bbq|barbecue|oranges?|orange juice|oj|lemons?|lemonade|limes?|limeade|grapefruits?|citrus|pineapples?|vinegar|vinaigrette|cranberr(?:y|ies)|pickles?|sauerkraut|hot sauce"""),
        Trigger.CARBONATED to rx("""sodas?|colas?|coke|pepsi|sprite|fanta|7[- ]?up|dr\.? ?pepper|mountain dew|root beer|ginger ale|ginger beer|cream soda|sparkling|seltzers?|club soda|tonic|carbonated|energy drinks?|red bull|monster energy|kombucha|beers?|champagne|prosecco|la ?croix|bubly"""),
        Trigger.CHOCOLATE_MINT to rx("""chocolate|cocoa|cacao|fudge|brownies?|mochas?|nutella|peppermint|spearmint|mints?"""),
        Trigger.DAIRY to rx("""milk|milkshakes?|shakes?|cheeses?|cheesy|cheddar|mozzarella|parmesan|parmigiano|provolone|pepper jack|brie|gouda|feta|ricotta|mascarpone|queso|cream|creamer|sour cream|ice cream|whipped cream|cream cheese|half and half|butter|buttercream|buttermilk|buttery|yogh?urts?|kefir|whey|casein|lactose|alfredo|custard|pudding|froyo|lattes?|cappuccinos?|frappuccinos?|mac (?:and|&|n) cheese|gelato|cheesecake|frosty|blizzard|mcflurry"""),
        Trigger.GLUTEN to rx("""wheat|flour|breads?|breaded|breadsticks?|buns?|bagels?|biscuits?|croissants?|muffins?|pancakes?|waffles?|french toast|toast|tortillas?|wraps?|pitas?|naan|flatbreads?|crackers?|cookies?|cakes?|cupcakes?|pastr(?:y|ies)|pies?|pretzels?|pasta|spaghetti|linguine|fettuccine|penne|macaroni|lasagna|ravioli|noodles?|ramen|udon|dumplings?|couscous|bulgur|farro|spelt|semolina|barley|rye|malt|seitan|croutons?|sandwich(?:es)?|burgers?|subs?|hoagies?|pizza|calzones?|stromboli|beers?|soy sauce|teriyaki|gravy|roux|battered|tempura|doughnuts?|donuts?|churros?|cereal|granola|gluten|panko|brioche|sourdough|(?:dinner|egg|cinnamon|spring|crescent) rolls?"""),
        Trigger.HIGH_PURINE to rx("""anchov(?:y|ies)|sardines?|herring|mackerel|mussels?|scallops?|trout|organ meats?|liver|livers|liverwurst|pate|pâté|kidneys?|sweetbreads?|tripe|venison|goose|gravy|bouillon|beers?|shellfish|shrimp|lobster|crab|clams?|oysters?|roe|caviar"""),
        Trigger.HIGH_FODMAP to rx("""onions?|garlic|beans?|refried beans|baked beans|lentils?|chickpeas?|hummus|falafel|apples?|apple juice|pears?|mangos?|mangoes|watermelon|cherr(?:y|ies)|peach(?:es)?|plums?|prunes|dates|figs|honey|agave|high fructose corn syrup|hfcs|sorbitol|xylitol|mannitol|maltitol|isomalt|cauliflower|mushrooms?|asparagus|artichokes?|leeks?|shallots?|wheat|rye|barley|inulin|chicory|milk|ice cream|yogh?urts?|ricotta|cottage cheese|cashews?|pistachios?"""),
        Trigger.FRUCTOSE to rx("""high fructose corn syrup|hfcs|corn syrup|fructose|agave|sodas?|colas?|coke|pepsi|sprite|fanta|7[- ]?up|dr\.? ?pepper|mountain dew|root beer|ginger ale|lemonade|limeade|sweet tea|fruit punch|punch|juices?|energy drinks?|red bull|sports drinks?|gatorade|powerade|slush(?:y|ies)|slurpee|icee|frappuccinos?|milkshakes?|syrup|candy|candies""")
    )

    fun detect(name: String, ingredients: String?, tags: Set<String>): Set<Trigger> {
        val text = (name + " " + ingredients.orEmpty()).lowercase()
        val found = mutableSetOf<Trigger>()
        for ((trigger, regex) in patterns) {
            val cleaned = strip[trigger]?.replace(text, " ") ?: text
            if (regex.containsMatchIn(cleaned)) found += trigger
        }
        tags.mapNotNullTo(found) { Trigger.fromKey(it) }
        return found
    }
}

data class ConditionWarning(
    val condition: Condition,
    val severity: Verdict,
    val details: List<String>
)

data class VerdictResult(
    val verdict: Verdict,
    val headline: String,
    val reasons: List<String>,
    /** Only real flags, each tied to the condition it's for. */
    val warnings: List<ConditionWarning> = emptyList(),
    val triggers: Set<Trigger> = emptySet(),
    /** Checks that couldn't run because the source had no data. Not warnings. */
    val skipped: List<String> = emptyList(),
    /** Allergen matches -- always the worst possible verdict when present. */
    val allergyHits: List<AllergyFlag> = emptyList(),
    /**
     * Set when a food trips two or more separate things at once (an allergy,
     * the meal fat limit, conditions). One sentence naming all of them, so
     * the person doesn't have to add up separate sections to see the total.
     */
    val compoundSummary: String? = null
)

/** One condition's reaction to a meal total: how bad, and the limit it was held to. */
data class IssueHit(val condition: Condition, val severity: Verdict, val limit: Double)

/**
 * One cause behind a meal's warnings (too big, too much sat fat, ...), with
 * every condition it affects merged in, so "meal size" shows once instead of
 * once per condition.
 */
data class MealIssue(
    val label: String,
    val unit: String,
    val total: Double,
    val severity: Verdict,
    /** Worst first. */
    val hits: List<IssueHit>,
    /** Some items in the meal had no data for this nutrient. */
    val partial: Boolean
) {
    private fun plainTitle(): String = when (label) {
        "meal size" -> "Big meal"
        "sat fat" -> "A lot of saturated fat"
        "sugar" -> "A lot of sugar"
        "carbs" -> "A lot of carbs"
        "sodium" -> "A lot of sodium"
        else -> label.replaceFirstChar { it.uppercase() }
    }

    /** One line. Simple: the cause and who it affects. Detailed: the numbers too. */
    fun describe(simple: Boolean): String {
        if (simple) return "${plainTitle()}: " + hits.joinToString(", ") { it.condition.short }
        val u = if (unit == "kcal") " kcal" else unit
        val parts = hits.joinToString(", ") {
            val word = if (it.severity == Verdict.OVER_LIMIT) "over" else "near"
            "$word for ${it.condition.short} (${fmt(it.limit)})"
        }
        return "${label.replaceFirstChar { it.uppercase() }} ${fmt(total)}$u: $parts" +
            if (partial) " (some items lack data)" else ""
    }
}

/** This rule with the user's own limit applied, if they set one for what it measures. */
private fun AmountRule.under(limits: Limits): AmountRule =
    if (label !in Limits.ADJUSTABLE || label !in limits.overrides) this
    else AmountRule(
        condition, label, unit,
        limits.caution(label, caution, over), limits.over(label, over),
        fromEntry, fromFood
    )

/** A per-meal amount rule for one condition. */
private class AmountRule(
    val condition: Condition,
    val label: String,
    val unit: String,
    val caution: Double,
    val over: Double,
    val fromEntry: (FoodLogEntry) -> Double?,
    val fromFood: (ScannedFood) -> Double?
)

private val AMOUNT_RULES = listOf(
    AmountRule(Condition.REFLUX, "meal size", "kcal",
        Thresholds.REFLUX_MEAL_KCAL_CAUTION.toDouble(), Thresholds.REFLUX_MEAL_KCAL_OVER.toDouble(),
        { it.calories.toDouble() }, { it.calories.toDouble() }),
    AmountRule(Condition.HIATAL_HERNIA, "meal size", "kcal",
        Thresholds.HERNIA_MEAL_KCAL_CAUTION.toDouble(), Thresholds.HERNIA_MEAL_KCAL_OVER.toDouble(),
        { it.calories.toDouble() }, { it.calories.toDouble() }),
    AmountRule(Condition.FATTY_LIVER, "sugar", "g",
        Thresholds.SUGAR_CAUTION_G, Thresholds.SUGAR_OVER_G,
        { it.sugarGrams }, { it.sugarGrams }),
    AmountRule(Condition.FATTY_LIVER, "sat fat", "g",
        Thresholds.SATFAT_CAUTION_G, Thresholds.SATFAT_OVER_G,
        { it.saturatedFatGrams }, { it.saturatedFatGrams }),
    AmountRule(Condition.DIABETES, "carbs", "g",
        Thresholds.CARB_CAUTION_G, Thresholds.CARB_OVER_G,
        { it.carbGrams }, { it.carbGrams }),
    AmountRule(Condition.DIABETES, "sugar", "g",
        Thresholds.SUGAR_CAUTION_G, Thresholds.SUGAR_OVER_G,
        { it.sugarGrams }, { it.sugarGrams }),
    AmountRule(Condition.HYPERTENSION, "sodium", "mg",
        Thresholds.SODIUM_CAUTION_MG, Thresholds.SODIUM_OVER_MG,
        { it.sodiumMg }, { it.sodiumMg }),
    AmountRule(Condition.CHOLESTEROL, "sat fat", "g",
        Thresholds.SATFAT_CAUTION_G, Thresholds.SATFAT_OVER_G,
        { it.saturatedFatGrams }, { it.saturatedFatGrams }),
    AmountRule(Condition.GALLBLADDER, "meal size", "kcal",
        Thresholds.GALLBLADDER_MEAL_KCAL_CAUTION.toDouble(), Thresholds.GALLBLADDER_MEAL_KCAL_OVER.toDouble(),
        { it.calories.toDouble() }, { it.calories.toDouble() }),
    AmountRule(Condition.GALLBLADDER, "sat fat", "g",
        Thresholds.SATFAT_CAUTION_G, Thresholds.SATFAT_OVER_G,
        { it.saturatedFatGrams }, { it.saturatedFatGrams })
)

private fun worst(a: Verdict, b: Verdict) = if (b.ordinal > a.ordinal) b else a

private fun fmt(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else String.format("%.1f", v)

/** Collects flags per condition, keeping the worst severity. */
private class FlagCollector {
    private val sev = mutableMapOf<Condition, Verdict>()
    private val details = mutableMapOf<Condition, MutableList<String>>()

    fun add(c: Condition, s: Verdict, detail: String) {
        if (s == Verdict.PASS) return
        sev[c] = worst(sev[c] ?: Verdict.PASS, s)
        details.getOrPut(c) { mutableListOf() }.add(detail)
    }

    fun build(): List<ConditionWarning> =
        Condition.values().filter { it in details }
            .map { ConditionWarning(it, sev.getValue(it), details.getValue(it)) }
}

private fun FlagCollector.triggers(found: Set<Trigger>, conditions: Set<Condition>) {
    for (c in Condition.values()) {
        if (c !in conditions) continue
        c.triggers.forEach { (t, s) -> if (t in found) add(c, s, t.label) }
    }
}

/**
 * Amount checks over a list of values (one per item in the meal).
 * Unknown values are ignored; if every value is unknown the check is skipped.
 */
private fun FlagCollector.amounts(
    conditions: Set<Condition>,
    limits: Limits,
    valuesFor: (AmountRule) -> List<Double?>
) {
    for (rule in AMOUNT_RULES.map { it.under(limits) }) {
        if (rule.condition !in conditions) continue
        val values = valuesFor(rule)
        if (values.none { it != null }) continue
        val total = values.sumOf { it ?: 0.0 }
        val level = when {
            total >= rule.over -> Verdict.OVER_LIMIT
            total >= rule.caution -> Verdict.CAUTION
            else -> Verdict.PASS
        }
        val partial = values.any { it == null }
        add(
            rule.condition, level,
            "${rule.label} ${fmt(total)}${rule.unit} this meal (" +
                (if (level == Verdict.OVER_LIMIT) "over" else "nearing") +
                " the ~${fmt(rule.over)}${rule.unit} limit)" +
                if (partial) ", some items lack data" else ""
        )
    }
}

/**
 * Live flags for things already logged. Worked out against the conditions
 * ticked right now, so changing Settings updates old entries too.
 */
object ConditionFlags {

    /** Stored triggers when present; otherwise detected from the name (older entries). */
    fun triggersOf(entry: FoodLogEntry): Set<Trigger> =
        entry.triggers?.let { Trigger.parse(it) }
            ?: TriggerDetector.detect(entry.name, null, emptySet())

    /** What's in this one food that a ticked condition cares about. */
    fun forEntry(entry: FoodLogEntry, conditions: Set<Condition>): List<ConditionWarning> {
        if (conditions.isEmpty()) return emptyList()
        return FlagCollector().apply { triggers(triggersOf(entry), conditions) }.build()
    }

    /** Triggers present that no ticked condition cares about — shown quietly. */
    fun irrelevantTriggers(entry: FoodLogEntry, conditions: Set<Condition>): Set<Trigger> {
        val relevant = conditions.flatMap { it.triggers.keys }.toSet()
        return triggersOf(entry) - relevant
    }

    /**
     * The same amount checks as [forMeal], merged by cause. Rules that measure
     * the same thing (e.g. meal size for GERD, hernia and gallbladder) become
     * one issue naming every condition it affects. Worst first.
     */
    fun mealIssues(
        entries: List<FoodLogEntry>,
        conditions: Set<Condition>,
        limits: Limits = Limits()
    ): List<MealIssue> {
        if (conditions.isEmpty() || entries.isEmpty()) return emptyList()
        return AMOUNT_RULES
            .map { it.under(limits) }
            .filter { it.condition in conditions }
            .groupBy { it.label }
            .mapNotNull { (label, rules) ->
                val values = entries.map(rules.first().fromEntry)
                if (values.none { it != null }) return@mapNotNull null
                val total = values.sumOf { it ?: 0.0 }
                val hits = rules.mapNotNull { r ->
                    val level = when {
                        total >= r.over -> Verdict.OVER_LIMIT
                        total >= r.caution -> Verdict.CAUTION
                        else -> null
                    }
                    level?.let { IssueHit(r.condition, it, r.over) }
                }.sortedByDescending { it.severity.ordinal }
                if (hits.isEmpty()) null
                else MealIssue(
                    label = label,
                    unit = rules.first().unit,
                    total = total,
                    severity = hits.map { it.severity }.reduce { a, b -> worst(a, b) },
                    hits = hits,
                    partial = values.any { it == null }
                )
            }
            .sortedByDescending { it.severity.ordinal }
    }

    /** Amount checks across a whole meal (sodium, sugar, carbs, sat fat, size). */
    fun forMeal(
        entries: List<FoodLogEntry>,
        conditions: Set<Condition>,
        limits: Limits = Limits()
    ): List<ConditionWarning> {
        if (conditions.isEmpty() || entries.isEmpty()) return emptyList()
        return FlagCollector().apply {
            amounts(conditions, limits) { rule -> entries.map(rule.fromEntry) }
        }.build()
    }
}

object VerdictRules {

    /**
     * Fat is judged against the per-meal ceiling. Condition checks add their
     * own flags, each naming its condition; the stamp shows the worst of all.
     */
    fun evaluate(
        food: ScannedFood,
        mealEntries: List<FoodLogEntry>,
        fatLimitPerMeal: Double,
        conditions: Set<Condition>,
        allergens: Set<Allergen> = emptySet(),
        customAllergens: List<CustomAllergen> = emptyList(),
        limits: Limits = Limits()
    ): VerdictResult {
        val reasons = mutableListOf<String>()

        // ---- Fat, per meal ----
        val fatAlready = mealEntries.sumOf { it.fatGrams }
        val projectedFat = fatAlready + food.fatGrams
        val fatVerdict = when {
            projectedFat >= fatLimitPerMeal -> Verdict.OVER_LIMIT
            projectedFat >= fatLimitPerMeal * 0.7 -> Verdict.CAUTION
            else -> Verdict.PASS
        }
        reasons += "Meal fat would reach ${fmt(projectedFat)}g of ${fmt(fatLimitPerMeal)}g."
        if (fatAlready > 0) reasons += "${fmt(fatAlready)}g already logged this meal."
        if (food.calories >= 700) reasons += "Large single item at ${food.calories} kcal."
        food.servingNote?.let { reasons += it }
        if (food.confidence == "low") reasons += "AI estimate — numbers are approximate."

        // ---- Conditions ----
        val found = TriggerDetector.detect(food.name, food.ingredients, food.tags)
        val skipped = mutableListOf<String>()
        val collector = FlagCollector()
        collector.triggers(found, conditions)
        collector.amounts(conditions, limits) { rule ->
            val item = rule.fromFood(food)
            if (item == null) {
                skipped += "${rule.label} for ${rule.condition.short}"
                listOf<Double?>(null)
            } else {
                mealEntries.map(rule.fromEntry) + item
            }
        }
        val warnings = collector.build()

        // ---- Allergies: always OVER_LIMIT, checked separately from conditions ----
        val allergyHits = AllergyChecker.check(food, allergens, customAllergens)

        var overall = warnings.fold(fatVerdict) { acc, w -> worst(acc, w.severity) }
        if (allergyHits.isNotEmpty()) overall = Verdict.OVER_LIMIT

        // Every separate way this food hits the person, worst first.
        val hits = buildList {
            if (allergyHits.isNotEmpty()) add("your ${allergyHits.joinToString(", ") { it.label }} allergy")
            if (fatVerdict == Verdict.OVER_LIMIT) add("your meal fat limit")
            warnings.filter { it.severity == Verdict.OVER_LIMIT }.forEach { add(it.condition.short) }
            warnings.filter { it.severity == Verdict.CAUTION }.forEach { add("${it.condition.short} (caution)") }
            if (fatVerdict == Verdict.CAUTION) add("near your meal fat limit")
        }
        val compoundSummary = if (hits.size >= 2) {
            "This hits you ${hits.size} ways: " + hits.joinToString(", ") + "."
        } else null

        val parts = mutableListOf<String>()
        if (allergyHits.isNotEmpty()) {
            parts += "ALLERGY: " + allergyHits.joinToString(", ") { it.label }
        }
        when (fatVerdict) {
            Verdict.OVER_LIMIT -> parts += "OVER MEAL FAT LIMIT"
            Verdict.CAUTION -> parts += "NEAR FAT LIMIT"
            Verdict.PASS -> Unit
        }
        if (warnings.isNotEmpty()) {
            parts += "FLAGGED: " + warnings.joinToString(", ") { it.condition.short }
        }

        return VerdictResult(
            verdict = overall,
            headline = if (parts.isEmpty()) "WITHIN LIMITS" else parts.joinToString(" / "),
            reasons = reasons,
            warnings = warnings,
            triggers = found,
            skipped = skipped.distinct(),
            allergyHits = allergyHits,
            compoundSummary = compoundSummary
        )
    }
}
