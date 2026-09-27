package com.cooktime.app

import android.media.ToneGenerator
import android.media.AudioManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.time.LocalDate

@Composable fun RecipeDetail(recipe:Recipe,state:KitchenState,vm:KitchenViewModel,back:()->Unit,plan:()->Unit) {
    var servings by rememberSaveable(recipe.id) { mutableIntStateOf(recipe.servings) }
    var cooking by rememberSaveable(recipe.id) { mutableStateOf(false) }
    var step by rememberSaveable(recipe.id) { mutableIntStateOf(0) }
    var timerMinutes by rememberSaveable(recipe.id) { mutableStateOf("5") }
    var deadline by rememberSaveable(recipe.id) { mutableLongStateOf(0L) }
    var remaining by remember { mutableLongStateOf(0L) }
    var edit by rememberSaveable(recipe.id) { mutableStateOf(false) }
    var delete by rememberSaveable(recipe.id) { mutableStateOf(false) }
    var finished by rememberSaveable(recipe.id) { mutableStateOf(false) }
    val view=LocalView.current
    DisposableEffect(cooking) { view.keepScreenOn=cooking; onDispose { view.keepScreenOn=false } }
    LaunchedEffect(deadline) {
        while(deadline>0) {
            remaining=((deadline-System.currentTimeMillis()+999)/1000).coerceAtLeast(0)
            if(remaining==0L) {
                finished=true
                runCatching { ToneGenerator(AudioManager.STREAM_ALARM,80).also { try { it.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD,1000); delay(1100) } finally { it.release() } } }
                deadline=0
                break
            }
            delay(250)
        }
    }
    LazyColumn(contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            IconButton(onClick=back) { Icon(Icons.Outlined.ArrowBack,"Back") }
            IconButton(onClick={vm.favourite(recipe.id)}) { Icon(if(recipe.id in state.favourites) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,"Toggle favourite",tint=MaterialTheme.colorScheme.primary) }
        } }
        if(!cooking) item { FoodArt(recipe.id,Modifier.fillMaxWidth().height(210.dp)) }
        item { Text("${recipe.cuisine.uppercase()} · ${recipe.minutes} MIN · ${recipe.diet.uppercase()}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary) }
        item { Heading(recipe.title,recipe.description) }
        if(cooking) {
            item { Text("STEP ${step+1} OF ${recipe.steps.size}",color=MaterialTheme.colorScheme.secondary,style=MaterialTheme.typography.labelLarge) }
            item { LinearProgressIndicator(progress={(step+1).toFloat()/recipe.steps.size},modifier=Modifier.fillMaxWidth()) }
            item { Card { Text(recipe.steps[step],modifier=Modifier.padding(24.dp),style=MaterialTheme.typography.headlineSmall) } }
            item { Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick={step--},enabled=step>0,modifier=Modifier.weight(1f)) { Text("Previous") }
                Button(onClick={if(step<recipe.steps.lastIndex) step++ else {
                    vm.update { it.copy(history=(it.history+recipe.id).takeLast(100)) };cooking=false;deadline=0;finished=false
                    vm.notice.value="Nicely done. Enjoy your meal!"
                }},modifier=Modifier.weight(1f)) { Text(if(step==recipe.steps.lastIndex) "Finish cooking" else "Next step") }
            } }
            item { Card {
                Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("Kitchen timer",style=MaterialTheme.typography.titleMedium)
                    if(deadline>0) {
                        Text("${remaining/60}:${(remaining%60).toString().padStart(2,'0')}",style=MaterialTheme.typography.headlineLarge)
                        TextButton(onClick={deadline=0}) { Text("Cancel timer") }
                    } else Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(timerMinutes,{timerMinutes=it.filter(Char::isDigit).take(3)},label={Text("Minutes")},modifier=Modifier.weight(1f),singleLine=true)
                        Button(enabled=(timerMinutes.toIntOrNull()?:0) in 1..180,onClick={deadline=System.currentTimeMillis()+timerMinutes.toLong()*60_000;finished=false}) { Text("Start") }
                    }
                    if(finished) Text("Timer finished",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
                    Text("Keep this cooking screen open for the timer alert. This is an in-app timer, not a background alarm.",style=MaterialTheme.typography.bodySmall)
                }
            } }
            item { TextButton(onClick={cooking=false;deadline=0;finished=false}) { Text("Exit cooking mode") } }
        } else {
            item { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                Text("Servings",style=MaterialTheme.typography.titleMedium)
                Row(verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick={servings--},enabled=servings>1){Icon(Icons.Outlined.Remove,"Fewer servings")}
                    Text("$servings",style=MaterialTheme.typography.titleMedium)
                    IconButton(onClick={servings++},enabled=servings<24){Icon(Icons.Outlined.Add,"More servings")}
                }
            } }
            item { Button(onClick={cooking=true;step=0},modifier=Modifier.fillMaxWidth().height(52.dp)) { Icon(Icons.Outlined.PlayArrow,null); Text("Start cooking") } }
            item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={vm.addGroceries(recipe,servings)},modifier=Modifier.weight(1f)) { Text("Shop missing") }
                OutlinedButton(onClick=plan,modifier=Modifier.weight(1f)) { Text("Plan meal") }
            } }
            if(recipe.custom) item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                TextButton(onClick={edit=true}) { Text("Edit your recipe") }
                TextButton(onClick={delete=true}) { Text("Delete recipe",color=MaterialTheme.colorScheme.error) }
            } }
            item { Text("Ingredients",style=MaterialTheme.typography.titleLarge) }
            itemsIndexed(recipe.ingredients) { _, ingredient ->
                val have=KitchenLogic.key(ingredient.name) in KitchenLogic.available(state.pantry)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Icon(if(have) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,if(have) "In pantry" else "Missing",tint=if(have) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline)
                    Text(ingredient.display(servings,recipe.servings),modifier=Modifier.weight(1f))
                }
            }
            item { Text("Method",style=MaterialTheme.typography.titleLarge) }
            itemsIndexed(recipe.steps) { index, text ->
                Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    Text("${index+1}".padStart(2,'0'),color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
                    Text(text,modifier=Modifier.weight(1f))
                }
            }
            item { Text("Ingredient amounts scale with servings; adjust pan size, water and cooking time as needed. Check labels for allergens.",style=MaterialTheme.typography.bodySmall) }
        }
    }
    if(edit) CustomRecipeDialog(vm,{edit=false},recipe)
    if(delete) AlertDialog(onDismissRequest={delete=false},title={Text("Delete this recipe?")},text={Text("This also removes the recipe from your favourites and meal plan.")},confirmButton={TextButton(onClick={
        vm.update { s->s.copy(customRecipes=s.customRecipes.filterNot { it.id==recipe.id },favourites=s.favourites-recipe.id,meals=s.meals.filterNot { it.recipeId==recipe.id }) };back()
    }){Text("Delete")}},dismissButton={TextButton(onClick={delete=false}){Text("Cancel")}})

}
@Composable fun PlanRecipeDialog(recipe:Recipe,vm:KitchenViewModel,dismiss:()->Unit) {
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var slot by remember { mutableStateOf("Dinner") }
    var servings by remember { mutableStateOf(recipe.servings.toString()) }
    val valid=runCatching { LocalDate.parse(date) }.isSuccess && (servings.toIntOrNull()?:0) in 1..24
    AlertDialog(onDismissRequest=dismiss,title={Text("Plan ${recipe.title}")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(date,{date=it.take(10)},label={Text("Date: YYYY-MM-DD")},singleLine=true)
        ChoiceRow(listOf("Breakfast","Lunch","Dinner"),slot,{slot=it})
        OutlinedTextField(servings,{servings=it.filter(Char::isDigit).take(2)},label={Text("Servings (1–24)")},singleLine=true)
        Text("Replaces the meal already in this slot, if any.",style=MaterialTheme.typography.bodySmall)
    }},confirmButton={TextButton(enabled=valid,onClick={vm.update { s->s.copy(meals=s.meals.filterNot { it.date==date && it.slot==slot }+Meal(date,slot,recipe.id,servings.toInt())) };dismiss()}){Text("Save meal")}},dismissButton={TextButton(onClick=dismiss){Text("Cancel")}})
}
