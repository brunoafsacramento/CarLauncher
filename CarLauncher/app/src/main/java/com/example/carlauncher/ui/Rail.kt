package com.example.carlauncher.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Barra lateral de vidro com as abas do launcher. */
@Composable
fun GlassRail(tab: Int, onTab: (Int) -> Unit) {
    Column(
        Modifier
            .fillMaxHeight()
            .padding(start = 12.dp, top = 12.dp, bottom = 12.dp)
            .width(78.dp)
            .glass(RoundedCornerShape(30.dp))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        RailItem(Icons.Default.Home, "Início", tab == 0) { onTab(0) }
        RailItem(Icons.Default.Menu, "Apps", tab == 1) { onTab(1) }
        RailItem(Icons.Default.Build, "Motor", tab == 2) { onTab(2) }
        RailItem(Icons.Default.Favorite, "Saúde", tab == 3) { onTab(3) }
        RailItem(Icons.Default.Place, "Viagem", tab == 4) { onTab(4) }
        RailItem(Icons.Default.Settings, "Ajustes", tab == 5) { onTab(5) }
    }
}

@Composable
private fun RailItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(
        if (selected) Ui.Ice.copy(alpha = 0.20f) else Color.Transparent, label = "railBackground"
    )
    val content by animateColorAsState(if (selected) Ui.Ice else Ui.TextDim, label = "railContent")
    Column(
        Modifier
            .pressable(onClick = onClick)
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .width(64.dp)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = content, modifier = Modifier.size(22.dp))
        Text(label, fontSize = 11.sp, color = content, maxLines = 1)
    }
}
