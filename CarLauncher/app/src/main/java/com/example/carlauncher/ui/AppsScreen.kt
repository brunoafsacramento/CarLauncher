package com.example.carlauncher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.MainViewModel
import com.example.carlauncher.apps.AppInfo
import com.example.carlauncher.apps.launchApp

@Composable
fun AppsScreen(vm: MainViewModel) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val favorites = vm.favorites
    val apps = vm.apps.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SearchField(query, { query = it }, Modifier.weight(1f))
            Text("Segure um app para fixar no Início", color = Ui.TextDim, fontSize = 13.sp, maxLines = 1)
        }
        if (apps.isEmpty()) {
            Text(
                if (vm.apps.isEmpty()) "Carregando apps…" else "Nenhum app com esse nome",
                color = Ui.TextDim, modifier = Modifier.padding(8.dp)
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(112.dp),
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(apps, key = { it.pkg }) { app ->
                AppTile(
                    app = app,
                    pinned = app.pkg in favorites,
                    onClick = { launchApp(context, app.pkg) },
                    onLongClick = { vm.toggleFavorite(app.pkg) }
                )
            }
        }
    }
}

@Composable
private fun AppTile(app: AppInfo, pinned: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().pressable(onLongClick = onLongClick, onClick = onClick)) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(app.icon, null, Modifier.size(56.dp))
            Spacer(Modifier.height(6.dp))
            Text(app.label, fontSize = 13.sp, color = Ui.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (pinned) {
            Icon(
                Icons.Default.Star, "Fixado no Início", tint = Ui.Ice,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(16.dp)
            )
        }
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.height(48.dp).glass(RoundedCornerShape(24.dp)).padding(horizontal = 16.dp),
        singleLine = true,
        textStyle = TextStyle(color = Ui.Text, fontSize = 16.sp),
        cursorBrush = SolidColor(Ui.Ice),
        decorationBox = { inner ->
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, null, tint = Ui.TextDim, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text("Buscar app", color = Ui.TextDim, fontSize = 16.sp)
                    inner()
                }
            }
        }
    )
}
