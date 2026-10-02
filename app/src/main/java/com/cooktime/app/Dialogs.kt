package com.cooktime.app

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable fun ProDialog(billing:ProBilling,pro:Boolean,dismiss:()->Unit,preview:()->Unit) {
    val price by billing.price.collectAsState()
    val context=LocalContext.current
    AlertDialog(onDismissRequest=dismiss,title={Text(if(pro) "Your kitchen, upgraded" else "Make room for more")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("COOKTIME PRO",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelLarge)
            Text("One purchase. No subscription.",style=MaterialTheme.typography.titleLarge)
            Text("• Plan a full week of meals\n\n• Track pantry expiry dates\n\n• Keep your own recipes\n\n• Back up and restore your kitchen")
            Text("All 60 recipes, ingredient matching, favourites, guided cooking and shopping lists are free.")
            Text("Purchasing and restoring require Google Play and internet. Once verified, Pro works offline on this device.",style=MaterialTheme.typography.bodySmall)
            if(!pro) Button(onClick={billing.buy(context as Activity)},modifier=Modifier.fillMaxWidth()) { Text(price?.let { "Unlock Pro · $it" }?:"Check upgrade availability") }
            OutlinedButton(onClick={billing.refresh()},modifier=Modifier.fillMaxWidth()) { Text("Restore purchase") }
            if(BuildConfig.DEBUG) {
                HorizontalDivider()
                Text("DEVELOPER BUILD ONLY",style=MaterialTheme.typography.labelSmall)
                TextButton(onClick=preview) { Text("Toggle Pro preview (no purchase)") }
            }
        }
    },confirmButton={TextButton(onClick=dismiss){Text("Done")}})
}
@Composable fun SettingsDialog(state:KitchenState,vm:KitchenViewModel,pro:Boolean,dismiss:()->Unit,upgrade:()->Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var importText by remember { mutableStateOf<String?>(null) }
    var reset by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if(uri!=null) scope.launch {
            busy=true
            val snapshot=vm.state.value
            val result=withContext(Dispatchers.IO) { runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(KitchenJson.encode(snapshot)) } ?: error("Cannot open file")
            } }
            vm.notice.value=if(result.isSuccess) "Backup saved" else "Could not save backup"
            busy=false
        }
    }
    val restore=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) scope.launch {
            busy=true
            val result=withContext(Dispatchers.IO) { runCatching {
                val raw=context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                    val buffer=CharArray(2_000_001); var count=0
                    while(count<buffer.size) { val n=reader.read(buffer,count,buffer.size-count); if(n<0) break;count+=n }
                    require(count<=2_000_000) { "File too large" };String(buffer,0,count)
                } ?: error("Cannot open file")
                KitchenJson.decode(raw);raw
            } }
            result.onSuccess { importText=it }.onFailure { vm.notice.value="Invalid or unsupported CookTime backup. Your kitchen has not changed." }
            busy=false
        }
    }
    AlertDialog(onDismissRequest=dismiss,title={Text("Your kitchen settings")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) { Text("Dark mode",Modifier.weight(1f)); Switch(state.dark,{value->vm.update { it.copy(dark=value) }}) }
            Text("Dietary preference",style=MaterialTheme.typography.titleMedium)
            ChoiceRow(listOf("All","Vegetarian","Vegan"),state.diet,{diet->vm.update { it.copy(diet=diet) }})
            OutlinedTextField(state.excluded,{text->vm.update { it.copy(excluded=text.take(300)) }},label={Text("Exclude ingredients")},supportingText={Text("Comma-separated names, e.g. mushroom, peanut. This is a text filter, not a certified allergen checker.")})
            HorizontalDivider()
            Text("Your data",style=MaterialTheme.typography.titleMedium)
            Text("Recipes and kitchen data stay on this device. No account, advertising or analytics. Google Play handles purchases. Backups do not include Pro access.",style=MaterialTheme.typography.bodyMedium)
            OutlinedButton(enabled=!busy,onClick={if(pro) export.launch("CookTime-backup.json") else upgrade()},modifier=Modifier.fillMaxWidth()) { Text("Export backup · Pro") }
            OutlinedButton(enabled=!busy,onClick={if(pro) restore.launch(arrayOf("application/json","text/plain","application/octet-stream")) else upgrade()},modifier=Modifier.fillMaxWidth()) { Text("Restore backup · Pro") }
            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            TextButton(onClick={reset=true}) { Text("Reset kitchen data",color=MaterialTheme.colorScheme.error) }
            HorizontalDivider()
            Text("CookTime ${BuildConfig.VERSION_NAME}\n60 original recipes · 30 Indian + 30 international\nArtwork is decorative, not a photograph of each dish.",style=MaterialTheme.typography.bodySmall)
        }
    },confirmButton={TextButton(onClick=dismiss){Text("Done")}})
    if(importText!=null) AlertDialog(onDismissRequest={importText=null},title={Text("Replace your kitchen?")},text={Text("This replaces your pantry, favourites, meal plan, custom recipes and shopping list. Export a backup first if you want to keep them.")},confirmButton={TextButton(onClick={vm.restore(importText!!);importText=null}){Text("Replace and restore")}},dismissButton={TextButton(onClick={importText=null}){Text("Cancel")}})
    if(reset) AlertDialog(onDismissRequest={reset=false},title={Text("Reset your kitchen?")},text={Text("This deletes your local cooking data. Your Google Play purchase is unaffected.")},confirmButton={TextButton(onClick={vm.update { KitchenState(onboarded=true) };reset=false}){Text("Delete kitchen data")}},dismissButton={TextButton(onClick={reset=false}){Text("Cancel")}})
}
@Composable fun CustomRecipeDialog(vm:KitchenViewModel,dismiss:()->Unit, original:Recipe? = null) {
    var title by remember { mutableStateOf(original?.title?:"") }
    var minutes by remember { mutableStateOf(original?.minutes?.toString()?:"30") }
    var servings by remember { mutableStateOf(original?.servings?.toString()?:"2") }
    var cuisine by remember { mutableStateOf(original?.cuisine?:"Indian") }
    var diet by remember { mutableStateOf(original?.diet?:"Vegetarian") }
    var category by remember { mutableStateOf(original?.category?:"Dinner") }
    var mood by remember { mutableStateOf(original?.moods?.firstOrNull()?:"") }
    var occasion by remember { mutableStateOf(original?.occasions?.firstOrNull()?:"") }
    var specialDay by remember { mutableStateOf(original?.specialDays?.firstOrNull()?:"") }
    var ingredients by remember { mutableStateOf(original?.ingredients?.joinToString("\n") { "${it.amount} | ${it.unit} | ${it.name}" }?:"") }
    var steps by remember { mutableStateOf(original?.steps?.joinToString("\n")?:"") }
    var error by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest=dismiss,title={Text("A recipe of your own")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(title,{title=it.take(100)},label={Text("Recipe title")},singleLine=true)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(minutes,{minutes=it.filter(Char::isDigit).take(3)},label={Text("Minutes")},modifier=Modifier.weight(1f),singleLine=true)
                OutlinedTextField(servings,{servings=it.filter(Char::isDigit).take(2)},label={Text("Servings")},modifier=Modifier.weight(1f),singleLine=true)
            }
            ChoiceRow(listOf("Indian","International"),cuisine,{cuisine=it})
            ChoiceRow(listOf("Vegetarian","Vegan","Non-vegetarian"),diet,{diet=it})
            ChoiceRow(RecipeDiscovery.categories,category,{category=it})
            CollectionFilters(mood,occasion,specialDay,{mood=it},{occasion=it},{specialDay=it})
            OutlinedTextField(ingredients,{ingredients=it.take(10000)},label={Text("Ingredients: one per line")},placeholder={Text("200 | g | rice\n1 | tbsp | oil")},supportingText={Text("Format: amount | unit | ingredient. Use decimal quantities, e.g. 0.5.")},minLines=4)
            OutlinedTextField(steps,{steps=it.take(20000)},label={Text("Method: one step per line")},minLines=4)
            if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
        }
    },confirmButton={TextButton(onClick={
        runCatching {
            require(title.isNotBlank()) { "Add a recipe title." }
            val time=minutes.toIntOrNull()?:0;val count=servings.toIntOrNull()?:0
            require(time in 1..600 && count in 1..24) { "Use 1–600 minutes and 1–24 servings." }
            val parsed=ingredients.lines().filter { it.isNotBlank() }.map { line->
                val parts=line.split('|').map { it.trim() }
                require(parts.size==3) { "Each ingredient needs: amount | unit | name." }
                val amount=parts[0].toDoubleOrNull()?:0.0
                require(amount.isFinite() && amount>0 && parts[2].isNotBlank()) { "Check ingredient amounts and names." }
                Ingredient(KitchenLogic.key(parts[2]),amount,parts[1])
            }
            val method=steps.lines().map { it.trim() }.filter { it.isNotBlank() }
            require(parsed.isNotEmpty()&&method.isNotEmpty()) { "Add ingredients and at least one cooking step." }
            Recipe(original?.id?:"custom-${UUID.randomUUID()}",title.trim(),cuisine,time,count,diet,category,"From your own kitchen",parsed,method,true,listOf(mood).filter { it.isNotBlank() },listOf(occasion).filter { it.isNotBlank() },listOf(specialDay).filter { it.isNotBlank() })
        }.onSuccess { r->vm.update { it.copy(customRecipes=it.customRecipes.filterNot { old->old.id==r.id }+r) };dismiss() }.onFailure { error=it.message?:"Check your recipe" }
    }){Text("Save recipe")}},dismissButton={TextButton(onClick=dismiss){Text("Cancel")}})
}
