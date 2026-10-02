package com.cooktime.app

import java.time.LocalDate
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

data class Ingredient(val name: String, val amount: Double, val unit: String) {
    fun display(servings: Int, base: Int): String {
        val value = amount * servings / base
        val text = if (value == value.toInt().toDouble()) value.toInt().toString() else String.format(Locale.US, "%.1f", value)
        return "$text $unit $name".trim()
    }
}
data class Recipe(val id: String, val title: String, val cuisine: String, val minutes: Int,
    val servings: Int, val diet: String, val category: String, val description: String,
    val ingredients: List<Ingredient>, val steps: List<String>, val custom: Boolean = false,
    val moods: List<String> = emptyList(), val occasions: List<String> = emptyList(), val specialDays: List<String> = emptyList())
data class PantryItem(val name: String, val quantity: String = "", val expiry: String = "")
data class ShoppingItem(val name: String, val amount: Double = 0.0, val unit: String = "", val checked: Boolean = false) {
    val label: String get() = if (amount > 0) Ingredient(name, amount, unit).display(1, 1) else name
}
data class Meal(val date: String, val slot: String, val recipeId: String, val servings: Int)
data class KitchenState(val favourites: Set<String> = emptySet(), val pantry: List<PantryItem> = emptyList(),
    val shopping: List<ShoppingItem> = emptyList(), val meals: List<Meal> = emptyList(),
    val customRecipes: List<Recipe> = emptyList(), val history: List<String> = emptyList(),
    val diet: String = "All", val excluded: String = "", val dark: Boolean = false, val onboarded: Boolean = false)

/** Editorial inspiration, not automatically scheduled holidays or allergen certification. */
object RecipeDiscovery {
    val moods = listOf("Cozy comfort", "Quick & easy", "Fresh & light", "Spicy cravings", "Sweet tooth", "Rainy day", "Summer cooler")
    val occasions = listOf("Solo night", "Family table", "Date night", "Party bites", "Weekend brunch", "Movie night")
    val specialDays = listOf("Diwali", "Holi", "Eid", "Christmas", "Birthdays", "Valentine's Day", "New Year", "Pongal", "Anniversary")
    val categories = listOf("Breakfast", "Lunch", "Dinner", "Sides", "Snacks", "Desserts", "Drinks")
    fun matches(recipe: Recipe, mood: String = "", occasion: String = "", specialDay: String = "", query: String = ""): Boolean {
        val terms = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val searchable = listOf(recipe.title, recipe.description, recipe.cuisine, recipe.category) +
            recipe.ingredients.map { it.name } + recipe.moods + recipe.occasions + recipe.specialDays
        return (mood.isBlank() || mood in recipe.moods) && (occasion.isBlank() || occasion in recipe.occasions) &&
            (specialDay.isBlank() || specialDay in recipe.specialDays) && terms.all { term -> searchable.any { it.contains(term, ignoreCase = true) } }
    }
}

object KitchenLogic {
    fun normalize(value: String) = value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
    private val aliases = mapOf("garbanzo beans" to "chickpeas", "cilantro" to "coriander", "curd" to "yogurt", "capsicum" to "bell pepper", "scallions" to "spring onion")
    fun key(value: String): String = normalize(value).let { aliases[it] ?: it }
    fun available(pantry: List<PantryItem>, today: LocalDate = LocalDate.now()): Set<String> = pantry.filter {
        it.expiry.isBlank() || runCatching { !LocalDate.parse(it.expiry).isBefore(today) }.getOrDefault(false)
    }.map { key(it.name) }.toSet()
    fun missing(recipe: Recipe, pantry: List<PantryItem>) = recipe.ingredients.filter { key(it.name) !in available(pantry) }
    fun allowed(recipe: Recipe, diet: String, excluded: String): Boolean {
        val dietMatch = when (diet) { "Vegan" -> recipe.diet == "Vegan"; "Vegetarian" -> recipe.diet != "Non-vegetarian"; else -> true }
        val terms = excluded.split(',').map { normalize(it) }.filter { it.isNotEmpty() }
        return dietMatch && recipe.ingredients.none { ingredient -> terms.any { key(ingredient.name).contains(key(it)) } }
    }
    fun rank(recipes: List<Recipe>, state: KitchenState, maxMinutes: Int): List<Recipe> = recipes
        .filter { it.minutes <= maxMinutes && allowed(it, state.diet, state.excluded) }
        .sortedWith(compareBy<Recipe> { missing(it, state.pantry).size.toDouble() / it.ingredients.size }
            .thenBy { it.id in state.history.takeLast(5) }.thenBy { it.minutes })
    fun groceries(existing: List<ShoppingItem>, recipes: List<Pair<Recipe, Int>>, pantry: List<PantryItem>): List<ShoppingItem> {
        val result = existing.toMutableList()
        recipes.forEach { (recipe, servings) -> missing(recipe, pantry).forEach { ingredient ->
            val amount = ingredient.amount * servings / recipe.servings
            val index = result.indexOfFirst { key(it.name) == key(ingredient.name) && it.unit == ingredient.unit && !it.checked }
            if (index < 0) result.add(ShoppingItem(ingredient.name, amount, ingredient.unit))
            else result[index] = result[index].copy(amount = result[index].amount + amount)
        } }
        return result
    }
}

object KitchenJson {
    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    private fun JSONArray.strings() = (0 until length()).map { getString(it) }
    fun recipes(array: JSONArray): List<Recipe> = array.objects().map { o ->
        Recipe(o.getString("id"), o.getString("title"), o.getString("cuisine"), o.getInt("minutes"), o.getInt("servings"),
            o.getString("diet"), o.getString("category"), o.getString("description"),
            o.getJSONArray("ingredients").objects().map { Ingredient(it.getString("name"), it.getDouble("amount"), it.getString("unit")) },
            o.getJSONArray("steps").strings(), o.optBoolean("custom"),
            o.optJSONArray("moods")?.strings().orEmpty(), o.optJSONArray("occasions")?.strings().orEmpty(), o.optJSONArray("specialDays")?.strings().orEmpty())
    }
    fun recipe(r: Recipe) = JSONObject().put("id", r.id).put("title", r.title).put("cuisine", r.cuisine).put("minutes", r.minutes)
        .put("servings", r.servings).put("diet", r.diet).put("category", r.category).put("description", r.description).put("custom", r.custom)
        .put("ingredients", JSONArray(r.ingredients.map { JSONObject().put("name", it.name).put("amount", it.amount).put("unit", it.unit) }))
        .put("steps", JSONArray(r.steps)).put("moods", JSONArray(r.moods)).put("occasions", JSONArray(r.occasions)).put("specialDays", JSONArray(r.specialDays))
    fun encode(s: KitchenState): String = JSONObject().put("version", 1).put("favourites", JSONArray(s.favourites.toList()))
        .put("pantry", JSONArray(s.pantry.map { JSONObject().put("name", it.name).put("quantity", it.quantity).put("expiry", it.expiry) }))
        .put("shopping", JSONArray(s.shopping.map { JSONObject().put("name", it.name).put("amount", it.amount).put("unit", it.unit).put("checked", it.checked) }))
        .put("meals", JSONArray(s.meals.map { JSONObject().put("date", it.date).put("slot", it.slot).put("recipeId", it.recipeId).put("servings", it.servings) }))
        .put("customRecipes", JSONArray(s.customRecipes.map(::recipe))).put("history", JSONArray(s.history))
        .put("diet", s.diet).put("excluded", s.excluded).put("dark", s.dark).put("onboarded", s.onboarded).toString(2)
    fun decode(raw: String): KitchenState {
        require(raw.length <= 2_000_000) { "Backup is too large" }
        val o = JSONObject(raw)
        require(o.getInt("version") == 1) { "Unsupported backup version" }
        val state = KitchenState(
            o.getJSONArray("favourites").strings().toSet(),
            o.getJSONArray("pantry").objects().map { PantryItem(it.getString("name"), it.getString("quantity"), it.getString("expiry")) },
            o.getJSONArray("shopping").objects().map { ShoppingItem(it.getString("name"), it.getDouble("amount"), it.getString("unit"), it.getBoolean("checked")) },
            o.getJSONArray("meals").objects().map { Meal(it.getString("date"), it.getString("slot"), it.getString("recipeId"), it.getInt("servings")) },
            recipes(o.getJSONArray("customRecipes")), o.getJSONArray("history").strings(), o.getString("diet"), o.getString("excluded"), o.getBoolean("dark"), o.getBoolean("onboarded"))
        require(state.diet in listOf("All", "Vegetarian", "Vegan"))
        require(state.pantry.size <= 5000 && state.shopping.size <= 5000 && state.meals.size <= 5000 && state.customRecipes.size <= 1000)
        state.pantry.forEach { require(it.name.isNotBlank()); if(it.expiry.isNotBlank()) LocalDate.parse(it.expiry) }
        state.shopping.forEach { require(it.name.isNotBlank() && it.amount.isFinite() && it.amount >= 0) }
        state.meals.forEach { LocalDate.parse(it.date); require(it.servings in 1..24 && it.slot in listOf("Breakfast", "Lunch", "Dinner")) }
        state.customRecipes.forEach { r ->
            require(r.custom && r.id.startsWith("custom-") && r.title.isNotBlank() && r.servings in 1..24 && r.minutes in 1..600)
            require(r.ingredients.isNotEmpty() && r.steps.isNotEmpty())
            require(r.ingredients.all { it.name.isNotBlank() && it.amount.isFinite() && it.amount > 0 })
        }
        require(state.customRecipes.map { it.id }.distinct().size == state.customRecipes.size)
        return state
    }
}
