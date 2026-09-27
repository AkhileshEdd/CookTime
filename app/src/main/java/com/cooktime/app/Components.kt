package com.cooktime.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.*

@Composable fun FoodArt(seed: String, modifier: Modifier = Modifier) {
    val colors = listOf(Color(0xFFF2D29B),Color(0xFFDF7750),Color(0xFFD8DEA7),Color(0xFFE5BB73))
    val n = seed.hashCode().toLong().let { kotlin.math.abs(it) }
    Canvas(modifier.clip(RoundedCornerShape(24.dp)).background(Color(0xFFE9EAD9))) {
        val c = center; val r = size.minDimension * .39f
        drawCircle(Color(0xFFCCD0BE),r*1.12f, c+Offset(4.dp.toPx(),6.dp.toPx()))
        drawCircle(Color(0xFFFFFCED),r*1.12f,c)
        drawCircle(Color(0xFFD7D5C3),r*.94f,c,style=Stroke(2.dp.toPx()))
        drawCircle(colors[(n%4).toInt()],r*.86f,c)
        for(i in 0..17) {
            val angle=i*2.39996f + n%6
            val radius=r*.67f*sqrt((i+1)/18f)
            val p=c+Offset(cos(angle)*radius,sin(angle)*radius)
            drawCircle(if(i%3==0) Color(0xFFF8E7BA) else if(i%3==1) Color(0xFFAA4E2C) else Color(0xFFE6A34B),r*.12f,p)
        }
        for(i in 0..6) {
            val angle=i*2.1f
            val p=c+Offset(cos(angle)*r*.57f,sin(angle)*r*.57f)
            drawOval(Color(0xFF58713D),topLeft=p-Offset(r*.15f,r*.06f),size=androidx.compose.ui.geometry.Size(r*.3f,r*.12f))
        }
    }
}
@Composable fun ChoiceRow(options: List<String>, selected: String, onSelect: (String)->Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        options.forEach { FilterChip(selected=it==selected,onClick={onSelect(it)},label={Text(it)}) }
    }
}
@Composable fun Heading(title: String, subtitle: String? = null) {
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(title,style=MaterialTheme.typography.headlineMedium)
        subtitle?.let { Text(it,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
@Composable fun RecipeCard(recipe: Recipe, state: KitchenState, onClick:()->Unit) {
    Card(onClick=onClick,colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),shape=RoundedCornerShape(22.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            FoodArt(recipe.id,Modifier.size(86.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text("${recipe.cuisine.uppercase()} · ${recipe.minutes} MIN",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
                Text(recipe.title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                Text(recipe.diet,style=MaterialTheme.typography.bodySmall)
                if(state.pantry.isNotEmpty()) {
                    val missing=KitchenLogic.missing(recipe,state.pantry).size
                    Text(if(missing==0) "All ingredients in your pantry" else "${recipe.ingredients.size-missing}/${recipe.ingredients.size} ingredients in pantry",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.secondary)
                }
            }
            if(recipe.id in state.favourites) Text("♥",color=MaterialTheme.colorScheme.primary)
        }
    }
}
@Composable fun EmptyState(title:String, description:String) {
    Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(title,style=MaterialTheme.typography.titleMedium)
            Text(description,style=MaterialTheme.typography.bodyMedium)
        }
    }
}
@Composable fun ProGate(title:String, description:String, onUpgrade:()->Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        Text("COOKTIME PRO",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
        Heading(title,description)
        FoodArt("pro",Modifier.fillMaxWidth().height(190.dp))
        Button(onClick=onUpgrade,modifier=Modifier.fillMaxWidth()) { Text("Explore the one-time upgrade") }
        Text("Your recipes, pantry matching and shopping list stay free.",style=MaterialTheme.typography.bodySmall)
    }
}
