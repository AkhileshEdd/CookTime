package com.cooktime.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private lateinit var billing: ProBilling
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        billing = ProBilling(this)
        setContent { CookTime(billing) }
    }
    override fun onResume() { super.onResume(); if(::billing.isInitialized) billing.connect() }
    override fun onDestroy() { if(::billing.isInitialized) billing.close(); super.onDestroy() }
}
private val Light = lightColorScheme(primary=Color(0xFFAE452C), onPrimary=Color.White, secondary=Color(0xFF526443),
    background=Color(0xFFFBF7EF), surface=Color(0xFFFFFCF6), surfaceVariant=Color(0xFFF0E8DC),
    onBackground=Color(0xFF292D24), onSurface=Color(0xFF292D24), outline=Color(0xFF968C80))
private val Dark = darkColorScheme(primary=Color(0xFFFFAD91), secondary=Color(0xFFB8CD9F),
    background=Color(0xFF1B201A), surface=Color(0xFF232A22), surfaceVariant=Color(0xFF333B30))

@Composable fun CookTime(billing: ProBilling, vm: KitchenViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val purchased by billing.pro.collectAsStateWithLifecycle()
    var previewPro by rememberSaveable { mutableStateOf(false) }
    val pro = purchased || (BuildConfig.DEBUG && previewPro)
    var tab by rememberSaveable { mutableStateOf("Discover") }
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    var modal by rememberSaveable { mutableStateOf<String?>(null) }
    val snack = remember { SnackbarHostState() }
    val notice by vm.notice.collectAsStateWithLifecycle()
    val billingNotice by billing.message.collectAsStateWithLifecycle()
    LaunchedEffect(notice) { if(notice.isNotBlank()) { snack.showSnackbar(notice); vm.notice.value = "" } }
    LaunchedEffect(billingNotice) { if(billingNotice.isNotBlank()) { snack.showSnackbar(billingNotice); billing.message.value = "" } }
    MaterialTheme(colorScheme=if(state.dark) Dark else Light, typography=Typography(
        headlineLarge=Typography().headlineLarge.copy(fontFamily=FontFamily.Serif,fontWeight=FontWeight.Bold),
        headlineMedium=Typography().headlineMedium.copy(fontFamily=FontFamily.Serif,fontWeight=FontWeight.Bold),
        titleLarge=Typography().titleLarge.copy(fontFamily=FontFamily.Serif,fontWeight=FontWeight.Bold))) {
        Surface(Modifier.fillMaxSize(), color=MaterialTheme.colorScheme.background) {
            val recipe = vm.recipes().find { it.id == detail }
            BackHandler(detail != null || modal != null || tab != "Discover") {
                if(modal != null) modal = null else if(detail != null) detail = null else tab = "Discover"
            }
            Scaffold(containerColor=MaterialTheme.colorScheme.background, snackbarHost={ SnackbarHost(snack) }, bottomBar={
                if(recipe == null && state.onboarded) NavigationBar(containerColor=MaterialTheme.colorScheme.surface) {
                    listOf("Discover" to Icons.Outlined.Explore, "Recipes" to Icons.Outlined.MenuBook,
                        "Pantry" to Icons.Outlined.Kitchen, "Plan" to Icons.Outlined.DateRange, "Shop" to Icons.Outlined.ShoppingBag).forEach { (name, icon) ->
                        NavigationBarItem(selected=tab==name,onClick={tab=name},icon={Icon(icon,name)},label={Text(name,maxLines=1)})
                    }
                }
            }) { padding ->
                Box(Modifier.padding(padding).fillMaxSize()) {
                    if(!state.onboarded) Welcome(state, { vm.update { it.copy(diet=it.diet, onboarded=true) } }, { diet -> vm.update { it.copy(diet=diet) } })
                    else if(recipe != null) RecipeDetail(recipe,state,vm,{ detail=null }, { if(pro) modal="plan:${recipe.id}" else modal="pro" })
                    else Column(Modifier.fillMaxSize()) {
                        Row(Modifier.fillMaxWidth().padding(start=24.dp,end=12.dp,top=8.dp,bottom=8.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                            Text("CookTime",style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(top=10.dp))
                            Row {
                                TextButton(onClick={modal="pro"}) { Text(if(pro) "PRO ✓" else "Get Pro",fontWeight=FontWeight.Bold) }
                                IconButton(onClick={modal="settings"}) { Icon(Icons.Outlined.Tune,"Settings") }
                            }
                        }
                        when(tab) {
                            "Discover" -> DiscoverScreen(state,vm.recipes(),{detail=it},{tab="Pantry"})
                            "Recipes" -> RecipesScreen(state,vm.recipes(),{detail=it},{if(pro) modal="custom" else modal="pro"})
                            "Pantry" -> PantryScreen(state,vm,pro,{modal="pro"})
                            "Plan" -> PlanScreen(state,vm,pro,{modal="pro"},{detail=it})
                            "Shop" -> ShoppingScreen(state,vm)
                        }
                    }
                }
            }
            when {
                modal == "pro" -> ProDialog(billing,pro,{modal=null},{previewPro=!previewPro})
                modal == "settings" -> SettingsDialog(state,vm,pro,{modal=null},{modal="pro"})
                modal == "custom" -> CustomRecipeDialog(vm,{modal=null})
                modal?.startsWith("plan:") == true -> vm.recipes().find { it.id == modal!!.removePrefix("plan:") }?.let { PlanRecipeDialog(it,vm,{modal=null}) }
            }
        }
    }
}
@Composable private fun Welcome(state: KitchenState, done:()->Unit, diet:(String)->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        Text("COOKTIME  /  YOUR EVERYDAY KITCHEN",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelLarge)
        FoodArt("welcome",Modifier.fillMaxWidth().height(210.dp))
        Text("A little inspiration.\nA delicious everyday.",style=MaterialTheme.typography.headlineLarge)
        Text("Find something good to cook with what you already have. 60 recipes, two worlds of flavour, always at hand—even offline.",style=MaterialTheme.typography.bodyLarge)
        Text("How do you like to eat?",style=MaterialTheme.typography.titleMedium)
        ChoiceRow(listOf("All","Vegetarian","Vegan"),state.diet,diet)
        Text("You can change this anytime. Ingredient filters are a convenience, not an allergy guarantee—always check labels.",style=MaterialTheme.typography.bodySmall)
        Button(onClick=done,modifier=Modifier.fillMaxWidth().height(54.dp)) { Text("Let's cook") }
        Text("No account. No ads. Your kitchen stays on your device.",style=MaterialTheme.typography.bodySmall)
    }
}
