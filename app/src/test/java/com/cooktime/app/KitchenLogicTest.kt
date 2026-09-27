package com.cooktime.app

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONArray
import java.io.File
import java.time.LocalDate

class KitchenLogicTest {
    private val recipe=Recipe("sample","Sample","Indian",20,2,"Vegan","Dinner","",listOf(Ingredient("rice",100.0,"g"),Ingredient("oil",1.0,"tbsp")),listOf("Cook it"))
    @Test fun expiredPantryIsNotCounted() {
        val pantry=listOf(PantryItem("rice",expiry=LocalDate.now().minusDays(1).toString()),PantryItem("oil"))
        assertEquals(listOf("rice"),KitchenLogic.missing(recipe,pantry).map { it.name })
    }
    @Test fun pantryExpiresAfterUseByDay() {
        assertTrue("rice" in KitchenLogic.available(listOf(PantryItem("rice",expiry="2026-09-27")),LocalDate.of(2026,9,27)))
    }
    @Test fun emptyExclusionDoesNotFilterEverything() { assertTrue(KitchenLogic.allowed(recipe,"All"," , ")) }
    @Test fun dietAndExclusionsAreApplied() {
        assertFalse(KitchenLogic.allowed(recipe.copy(diet="Vegetarian"),"Vegan",""))
        assertFalse(KitchenLogic.allowed(recipe.copy(diet="Non-vegetarian"),"Vegetarian",""))
        assertFalse(KitchenLogic.allowed(recipe,"All"," Rice "))
    }
    @Test fun groceriesScaleAndCombineOnlyCompatibleUnits() {
        val existing=listOf(ShoppingItem("rice",1.0,"cup"),ShoppingItem("rice",50.0,"g"),ShoppingItem("rice",10.0,"g",true))
        val list=KitchenLogic.groceries(existing,listOf(recipe to 4),listOf(PantryItem("oil")))
        assertEquals(3,list.size)
        assertEquals(250.0,list[1].amount,0.001)
        assertEquals(1.0,list[0].amount,0.001)
        assertTrue(list[2].checked)
    }
    @Test fun rankingRespectsTimeAndIngredientCoverage() {
        val slow=recipe.copy(id="slow",minutes=50)
        val matching=recipe.copy(id="matching",ingredients=listOf(Ingredient("oil",1.0,"tbsp")))
        assertEquals(listOf("matching","sample"),KitchenLogic.rank(listOf(recipe,slow,matching),KitchenState(pantry=listOf(PantryItem("oil"))),30).map { it.id })
    }
    @Test fun aliasesMatchPrecisely() {
        assertEquals(KitchenLogic.key("capsicum"),KitchenLogic.key("bell pepper"))
        assertNotEquals(KitchenLogic.key("rice"),KitchenLogic.key("rice flour"))
    }
    @Test fun backupRoundTripsWithoutEntitlement() {
        val state=KitchenState(pantry=listOf(PantryItem("rice","2 kg","2026-10-01")),favourites=setOf("sample"),shopping=listOf(ShoppingItem("oil",1.0,"tbsp")),
            meals=listOf(Meal("2026-09-28","Dinner","sample",2)),customRecipes=listOf(recipe.copy(id="custom-1",custom=true)),dark=true)
        val raw=KitchenJson.encode(state)
        assertEquals(state,KitchenJson.decode(raw))
        assertFalse(raw.contains("purchaseToken"))
        assertFalse(raw.contains("entitlement"))
    }
    @Test(expected=IllegalArgumentException::class) fun invalidBackupRejected() {
        KitchenJson.decode(KitchenJson.encode(KitchenState()).replace("\"version\": 1","\"version\": 9"))
    }
    @Test(expected=IllegalArgumentException::class) fun zeroServingsRejected() {
        KitchenJson.decode(KitchenJson.encode(KitchenState(customRecipes=listOf(recipe.copy(id="custom-1",custom=true,servings=0)))))
    }
    @Test fun bundledCollectionIsBalancedAndComplete() {
        val recipes=KitchenJson.recipes(JSONArray(File("src/main/assets/recipes.json").readText()))
        assertEquals(24,recipes.size)
        assertEquals(12,recipes.count { it.cuisine=="Indian" })
        assertEquals(12,recipes.count { it.cuisine=="International" })
        assertEquals(recipes.size,recipes.map { it.id }.toSet().size)
        recipes.forEach { r->
            assertTrue(r.servings>0 && r.minutes>0 && r.title.isNotBlank())
            assertTrue(r.ingredients.size>=3 && r.steps.size>=3)
            assertTrue(r.ingredients.all { it.amount>0 && it.name.isNotBlank() && it.unit.isNotBlank() })
        }
    }
}
