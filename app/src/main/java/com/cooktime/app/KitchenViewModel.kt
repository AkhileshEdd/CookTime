package com.cooktime.app

import android.app.Application
import android.util.AtomicFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray

class KitchenViewModel(application: Application) : AndroidViewModel(application) {
    private val file = AtomicFile(File(application.filesDir, "kitchen.json"))
    val notice = MutableStateFlow("")
    val bundled = KitchenJson.recipes(JSONArray(application.assets.open("recipes.json").bufferedReader().use { it.readText() }))
    private val initial = if (file.baseFile.exists()) runCatching { KitchenJson.decode(file.openRead().bufferedReader().use { it.readText() }) }
        .getOrElse { notice.value = "Your saved data could not be read. The original file is preserved until you make a change."; KitchenState() } else KitchenState()
    private val mutable = MutableStateFlow(initial)
    val state = mutable.asStateFlow()
    private val writes = Channel<KitchenState>(Channel.CONFLATED)
    init {
        viewModelScope.launch(Dispatchers.IO) {
            for (state in writes) {
                var stream: java.io.FileOutputStream? = null
                try { stream = file.startWrite(); stream.write(KitchenJson.encode(state).toByteArray()); file.finishWrite(stream) }
                catch (e: Exception) { file.failWrite(stream); notice.value = "Could not save changes. Please check device storage." }
            }
        }
    }
    fun update(block: (KitchenState) -> KitchenState) { val next = block(mutable.value); mutable.value = next; writes.trySend(next) }
    fun favourite(id: String) = update { it.copy(favourites = if (id in it.favourites) it.favourites - id else it.favourites + id) }
    fun recipes() = bundled + mutable.value.customRecipes
    fun addGroceries(recipe: Recipe, servings: Int) { update { it.copy(shopping = KitchenLogic.groceries(it.shopping, listOf(recipe to servings), it.pantry)) }; notice.value = "Missing ingredients added to your shopping list" }
    fun restore(raw: String) { val restored = KitchenJson.decode(raw); update { restored }; notice.value = "Kitchen restored" }
}
