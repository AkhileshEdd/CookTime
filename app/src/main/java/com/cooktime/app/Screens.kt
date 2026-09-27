package com.cooktime.app

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable fun DiscoverScreen(state:KitchenState, recipes:List<Recipe>, open:(String)->Unit, pantry:()->Unit) {
    var time by rememberSaveable { mutableStateOf("Any time") }
    var shuffled by rememberSaveable { mutableIntStateOf(0) }
    val max=when(time){"15 min"->15;"30 min"->30;else->600}
    val ranked=KitchenLogic.rank(recipes,state,max)
    val pick=ranked.getOrNull(if(ranked.isEmpty()) 0 else shuffled%minOf(ranked.size,5))
    LazyColumn(contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        item { Text("A GOOD MEAL STARTS HERE",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.secondary) }
        item { Heading("What should\nyou cook?","A little less deciding. A lot more enjoying.") }
        item { ChoiceRow(listOf("Any time","15 min","30 min"),time,{time=it;shuffled=0}) }
        item {
            if(pick!=null) Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    FoodArt(pick.id,Modifier.fillMaxWidth().height(178.dp))
                    Text(if(state.pantry.isEmpty()) "TODAY'S INSPIRATION" else "PICKED FOR YOUR PANTRY",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
                    Text(pick.title,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                    Text(pick.description)
                    Text("${pick.minutes} min  •  ${pick.diet}  •  ${pick.cuisine}",style=MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Button(onClick={open(pick.id)},modifier=Modifier.weight(1f)) { Text("Let's cook") }
                        OutlinedButton(onClick={shuffled++},enabled=ranked.size>1) { Icon(Icons.Outlined.Shuffle,"Another idea") }
                    }
                }
            } else EmptyState("No matching recipes","Try a longer cooking time or adjust your dietary and ingredient filters in Settings.")
        }
        if(state.pantry.isEmpty()) item { OutlinedButton(onClick=pantry,modifier=Modifier.fillMaxWidth()) { Text("Add ingredients for better suggestions") } }
        item { Heading("Worth making tonight", "Simple recipes, stored right here.") }
        items(ranked.take(6),key={it.id}) { RecipeCard(it,state,{open(it.id)}) }
        item { Text("Pantry matches check ingredient names, not quantities. Check your supplies before starting.",style=MaterialTheme.typography.bodySmall) }
    }
}
@Composable fun RecipesScreen(state:KitchenState, recipes:List<Recipe>, open:(String)->Unit, add:()->Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var cuisine by rememberSaveable { mutableStateOf("All cuisines") }
    var collection by rememberSaveable { mutableStateOf("All recipes") }
    var category by rememberSaveable { mutableStateOf("Any meal") }
    val filtered=recipes.filter { KitchenLogic.allowed(it,state.diet,state.excluded) &&
        (cuisine=="All cuisines" || it.cuisine==cuisine) &&
        (category=="Any meal" || it.category==category) &&
        (collection!="Favourites" || it.id in state.favourites) && (collection!="My recipes" || it.custom) &&
        (it.title.contains(query,true) || it.ingredients.any { i->i.name.contains(query,true) }) }
    LazyColumn(contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item { Heading("Your recipe book","Indian comfort. International favourites.") }
        item { OutlinedTextField(query,{query=it},label={Text("Search dishes or ingredients")},leadingIcon={Icon(Icons.Outlined.Search,null)},modifier=Modifier.fillMaxWidth(),singleLine=true) }
        item { ChoiceRow(listOf("All recipes","Favourites","My recipes"),collection,{collection=it}) }
        item { ChoiceRow(listOf("All cuisines","Indian","International"),cuisine,{cuisine=it}) }
        item { ChoiceRow(listOf("Any meal","Breakfast","Lunch","Dinner","Sides"),category,{category=it}) }
        item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Text("${filtered.size} recipes",style=MaterialTheme.typography.labelLarge)
            TextButton(onClick=add) { Icon(Icons.Outlined.Add,null); Text("Your recipe") }
        } }
        if(filtered.isEmpty()) item { EmptyState("Nothing here yet","Try another search or filter. Tap the heart on any recipe to save it.") }
        items(filtered,key={it.id}) { RecipeCard(it,state,{open(it.id)}) }
    }
}
@Composable fun PantryScreen(state:KitchenState, vm:KitchenViewModel, pro:Boolean, upgrade:()->Unit) {
    var editing by remember { mutableStateOf<PantryItem?>(null) }
    var add by rememberSaveable { mutableStateOf(false) }
    LazyColumn(contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item { Heading("In your kitchen","Keep track of what you have. Cook more, waste less.") }
        item { Button(onClick={add=true},modifier=Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Add,null); Text("Add an ingredient") } }
        if(!pro) item { TextButton(onClick=upgrade) { Text("Unlock expiry tracking with Pro") } }
        if(state.pantry.isEmpty()) item { EmptyState("Start with a few staples","Add ingredients such as rice, tomato, onion and oil. Use the ingredient names shown in recipes for the best matches.") }
        items(state.pantry.sortedWith(compareBy<PantryItem> { it.expiry.ifEmpty { "9999" } }.thenBy { it.name }),key={it.name}) { item ->
            Card(onClick={editing=item}) {
                Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(item.name.replaceFirstChar { it.uppercase() },fontWeight=FontWeight.SemiBold)
                        if(item.quantity.isNotBlank()) Text(item.quantity,style=MaterialTheme.typography.bodySmall)
                        if(item.expiry.isNotBlank()) {
                            val days=ChronoUnit.DAYS.between(LocalDate.now(),LocalDate.parse(item.expiry))
                            Text(when { days<0->"Expired ${-days} days ago · excluded from matches";days==0L->"Use today";else->"Use by ${item.expiry}" },style=MaterialTheme.typography.labelSmall,color=if(days<=2) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary)
                        }
                    }
                    IconButton(onClick={vm.update { s->s.copy(pantry=s.pantry.filterNot { it.name==item.name }) }}) { Icon(Icons.Outlined.Delete,"Remove ${item.name}") }
                }
            }
        }
        item { Text("Expiry dates are entered by you; follow the food's label and storage instructions.",style=MaterialTheme.typography.bodySmall) }
    }
    if(add || editing!=null) PantryDialog(editing,pro,{add=false;editing=null},{new->
        val old=editing
        vm.update { s->s.copy(pantry=s.pantry.filterNot { KitchenLogic.key(it.name)==KitchenLogic.key(new.name) || it.name==old?.name }+new) }
        add=false;editing=null
    })
}
@Composable private fun PantryDialog(item:PantryItem?,pro:Boolean,dismiss:()->Unit,save:(PantryItem)->Unit) {
    var name by remember { mutableStateOf(item?.name?:"") }
    var quantity by remember { mutableStateOf(item?.quantity?:"") }
    var expiry by remember { mutableStateOf(item?.expiry?:"") }
    val validDate=expiry.isBlank() || runCatching { LocalDate.parse(expiry) }.isSuccess
    AlertDialog(onDismissRequest=dismiss,title={Text(if(item==null) "Add to pantry" else "Edit ingredient")},text={
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name,{name=it.take(80)},label={Text("Ingredient name")},singleLine=true)
            OutlinedTextField(quantity,{quantity=it.take(80)},label={Text("Quantity / note (optional)")},singleLine=true)
            OutlinedTextField(expiry,{expiry=it.take(10)},label={Text("Expiry: YYYY-MM-DD")},enabled=pro,singleLine=true,isError=!validDate,supportingText={Text(if(pro) "Optional; expired items are excluded from matches" else "Expiry tracking is a Pro feature")})
        }
    },confirmButton={TextButton(enabled=name.isNotBlank()&&validDate,onClick={save(PantryItem(KitchenLogic.key(name),quantity.trim(),expiry.trim()))}) { Text("Save ingredient") }},dismissButton={TextButton(onClick=dismiss){Text("Cancel")}})
}
@Composable fun ShoppingScreen(state:KitchenState,vm:KitchenViewModel) {
    var name by rememberSaveable { mutableStateOf("") }
    val context=LocalContext.current
    LazyColumn(contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item { Heading("A little shopping","${state.shopping.count { !it.checked }} items left to pick up") }
        item { Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name,{name=it.take(100)},label={Text("Add a shopping item")},modifier=Modifier.weight(1f),singleLine=true)
            FilledIconButton(enabled=name.isNotBlank(),onClick={vm.update { it.copy(shopping=it.shopping+ShoppingItem(name.trim())) };name=""}) { Icon(Icons.Outlined.Add,"Add item") }
        } }
        item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled=state.shopping.any { !it.checked },onClick={
                val text="CookTime shopping list\n"+state.shopping.filterNot { it.checked }.joinToString("\n") { "• ${it.label}" }
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,text),"Share shopping list"))
            }) { Text("Share list") }
            TextButton(enabled=state.shopping.any { it.checked },onClick={vm.update { it.copy(shopping=it.shopping.filterNot { x->x.checked }) }}) { Text("Clear checked") }
        } }
        if(state.shopping.isEmpty()) item { EmptyState("Your list is ready for ideas","Open a recipe and add its missing ingredients, or type an item above.") }
        items(state.shopping.size) { index ->
            val item=state.shopping[index]
            Card {
                Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(checked=item.checked,onCheckedChange={checked->vm.update { s->s.copy(shopping=s.shopping.mapIndexed { i,x->if(i==index) x.copy(checked=checked) else x }) }})
                    Text(item.label,modifier=Modifier.weight(1f),color=if(item.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                    IconButton(onClick={vm.update { s->s.copy(shopping=s.shopping.filterIndexed { i,_->i!=index }) }}) { Icon(Icons.Outlined.Close,"Remove ${item.name}") }
                }
            }
        }
        if(state.shopping.any { it.checked }) item { OutlinedButton(onClick={
            vm.update { s->
                val added=s.shopping.filter { it.checked }.map { PantryItem(KitchenLogic.key(it.name),if(it.amount>0) "${it.amount} ${it.unit}" else "") }
                s.copy(pantry=(s.pantry+added.filter { a->s.pantry.none { KitchenLogic.key(it.name)==a.name } }).distinctBy { it.name },shopping=s.shopping.filterNot { it.checked })
            }
            vm.notice.value="Checked items moved to pantry; existing quantities were left unchanged"
        },modifier=Modifier.fillMaxWidth()) { Text("Move checked items to pantry") } }
    }
}
@Composable fun PlanScreen(state:KitchenState,vm:KitchenViewModel,pro:Boolean,upgrade:()->Unit,open:(String)->Unit) {
    if(!pro) { ProGate("A calmer week starts here","Plan breakfast, lunch and dinner, then build a shopping list from the ingredients you're missing.",upgrade); return }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var choosing by remember { mutableStateOf<Pair<String,String>?>(null) }
    val start=LocalDate.now().with(java.time.DayOfWeek.MONDAY).plusWeeks(offset.toLong())
    val dates=(0..6).map { start.plusDays(it.toLong()) }
    val meals=state.meals.filter { m->dates.any { it.toString()==m.date } }
    val recipes=vm.recipes()
    LazyColumn(contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { Heading("Your week, sorted","Make a little room for good food.") }
        item { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
            IconButton(onClick={offset--}) { Icon(Icons.Outlined.ChevronLeft,"Previous week") }
            Text("${start.format(DateTimeFormatter.ofPattern("d MMM"))} – ${start.plusDays(6).format(DateTimeFormatter.ofPattern("d MMM"))}",fontWeight=FontWeight.Bold)
            IconButton(onClick={offset++}) { Icon(Icons.Outlined.ChevronRight,"Next week") }
        } }
        item { Button(enabled=meals.isNotEmpty(),onClick={
            val planned=meals.mapNotNull { m->recipes.find { it.id==m.recipeId }?.let { it to m.servings } }
            vm.update { it.copy(shopping=KitchenLogic.groceries(it.shopping,planned,it.pantry)) }
            vm.notice.value="This week's missing ingredients added. Repeating this adds quantities again."
        },modifier=Modifier.fillMaxWidth()) { Text("Add week to shopping list") } }
        items(dates) { date ->
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(date.format(DateTimeFormatter.ofPattern("EEEE, d MMM")),style=MaterialTheme.typography.titleMedium)
                    listOf("Breakfast","Lunch","Dinner").forEach { slot ->
                        val meal=meals.find { it.date==date.toString() && it.slot==slot }
                        val recipe=recipes.find { it.id==meal?.recipeId }
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            TextButton(onClick={if(recipe!=null) open(recipe.id) else choosing=date.toString() to slot},modifier=Modifier.weight(1f)) {
                                Text("$slot · ${recipe?.title?:"Add a meal"}"+(meal?.let { " (${it.servings})" }?:""),modifier=Modifier.fillMaxWidth())
                            }
                            if(meal!=null) IconButton(onClick={vm.update { s->s.copy(meals=s.meals-meal) }}) { Icon(Icons.Outlined.Close,"Remove $slot") }
                        }
                    }
                }
            }
        }
    }
    choosing?.let { (date,slot) ->
        AlertDialog(onDismissRequest={choosing=null},title={Text("$slot · $date")},text={
            LazyColumn(modifier=Modifier.heightIn(max=360.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                items(recipes.filter { KitchenLogic.allowed(it,state.diet,state.excluded) }) { r ->
                    TextButton(onClick={vm.update { s->s.copy(meals=s.meals.filterNot { it.date==date && it.slot==slot }+Meal(date,slot,r.id,r.servings)) };choosing=null}) { Text(r.title) }
                }
            }
        },confirmButton={},dismissButton={TextButton(onClick={choosing=null}){Text("Cancel")}})
    }
}
