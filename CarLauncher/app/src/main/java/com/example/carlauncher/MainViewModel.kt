package com.example.carlauncher

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.carlauncher.apps.AppInfo
import com.example.carlauncher.apps.AppRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Estado das telas: aba atual, apps instalados e atalhos. O carro fica em CarHub. */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    var tab by mutableIntStateOf(0)

    var apps by mutableStateOf<List<AppInfo>>(emptyList()); private set
    var favorites by mutableStateOf<List<String>>(emptyList()); private set

    private val prefs = app.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    /** Apps fixados na tela inicial, na ordem em que foram fixados. */
    val favoriteApps: List<AppInfo>
        get() = favorites.mapNotNull { pkg -> apps.firstOrNull { it.pkg == pkg } }

    fun loadApps() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { AppRepo.load(getApplication<Application>()) }
            apps = list
            // Enquanto o usuário não fixar nada, usa os apps sugeridos (rádio, música, mapas...)
            favorites = prefs.getString("favorites", null)?.split('|')?.filter { it.isNotEmpty() }
                ?: AppRepo.defaultFavorites(list)
        }
    }

    fun toggleFavorite(pkg: String) {
        val updated = if (pkg in favorites) favorites - pkg else favorites + pkg
        favorites = updated
        prefs.edit().putString("favorites", updated.joinToString("|")).apply()
    }
}
