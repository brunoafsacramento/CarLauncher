package com.example.carlauncher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.Prefs
import java.util.Locale

/** Cores do tema: noite de garagem, fumaça azul-aço e uma cor de destaque que o usuário escolhe. */
object Ui {
    val NightTop = Color(0xFF0B1020)
    val NightBottom = Color(0xFF04060B)
    val Smoke = Color(0xFF5B6B8C)
    val Redline = Color(0xFFFF5A4F)
    val Amber = Color(0xFFFFB454)
    val Text = Color(0xFFF1F5FC)
    val TextDim = Color(0xFF98A4BA)

    val accentNames = listOf("Gelo", "Âmbar", "Verde", "Violeta")
    private val accents = listOf(Color(0xFF7FD6FF), Amber, Color(0xFF6FE3A1), Color(0xFFB79CFF))

    /** Cor de destaque atual (o nome ficou do primeiro tema, azul-gelo). */
    val Ice: Color
        get() = accents[Prefs.accent.value.coerceIn(0, accents.lastIndex)]

    val carColorNames = listOf("Prata", "Preto", "Branco", "Cinza", "Vermelho", "Azul")
    private val carColors = listOf(
        Color(0xFFB9C2D0), Color(0xFF262B35), Color(0xFFF0F3F7),
        Color(0xFF656D7C), Color(0xFFB3202A), Color(0xFF27488A)
    )
    val carColor: Color
        get() = carColors[Prefs.carColor.value.coerceIn(0, carColors.lastIndex)]
}

private val ptBr = Locale("pt", "BR")

/** Número com vírgula decimal, como se escreve no Brasil. */
fun Float.fmt(decimals: Int = 1): String = String.format(ptBr, "%.${decimals}f", this)

/**
 * Efeito de vidro: preenchimento translúcido + borda com brilho.
 * Não usa desfoque real de propósito, para rodar liso em centrais fracas.
 */
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(26.dp),
    tint: Color = Color.White,
    strength: Float = 1f
): Modifier = this
    .clip(shape)
    .background(
        Brush.verticalGradient(
            listOf(tint.copy(alpha = 0.13f * strength), tint.copy(alpha = 0.04f * strength))
        )
    )
    .border(
        1.dp,
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.40f),
                Color.White.copy(alpha = 0.04f),
                Color.White.copy(alpha = 0.16f)
            )
        ),
        shape
    )

/** Toque com animação: o item encolhe um pouco ao ser pressionado e volta com efeito de mola. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.pressable(onLongClick: (() -> Unit)? = null, onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f),
        label = "press"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            onLongClick = onLongClick,
            onClick = onClick
        )
}

@Composable
fun GlassButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier
            .pressable { if (enabled) onClick() }
            .glass(RoundedCornerShape(50), tint = Ui.Ice, strength = if (enabled) 1.6f else 0.5f)
            .padding(horizontal = 18.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, color = if (enabled) Ui.Text else Ui.TextDim,
            fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1
        )
    }
}

/** Mancha circular suave de raio 1, para ser esticada e movida sem recriar o pincel a cada quadro. */
fun softBlob(color: Color, alpha: Float): Brush = Brush.radialGradient(
    listOf(color.copy(alpha = alpha), color.copy(alpha = alpha * 0.35f), Color.Transparent),
    center = Offset.Zero,
    radius = 1f
)

@Composable
fun ScreenTitle(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(start = 6.dp)) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Ui.Text, maxLines = 1)
        Text(subtitle, fontSize = 13.sp, color = Ui.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Cartãozinho com um nome e um valor. */
@Composable
fun Stat(label: String, value: String?, modifier: Modifier = Modifier, valueColor: Color = Ui.Text) {
    Column(modifier.glass(RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(label, fontSize = 12.sp, color = Ui.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            value ?: "--", fontSize = 19.sp, fontWeight = FontWeight.Medium, color = valueColor,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun RoundButton(symbol: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).pressable(onClick = onClick).glass(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, fontSize = 22.sp, color = Ui.Text)
    }
}

/** Ajuste por botões de menos e mais, sem precisar de teclado. */
@Composable
fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 15.sp, color = Ui.Text)
        RoundButton("−", onMinus)
        Text(
            value, Modifier.padding(horizontal = 12.dp),
            fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Ui.Text, maxLines = 1
        )
        RoundButton("+", onPlus)
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = Ui.Text)
            if (subtitle.isNotEmpty()) Text(subtitle, fontSize = 12.sp, color = Ui.TextDim)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Ui.Ice, checkedThumbColor = Color.White)
        )
    }
}

/** Escolha de uma opção entre várias, em pílulas. */
@Composable
fun ChoiceRow(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 15.sp, color = Ui.Text)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEachIndexed { index, name ->
                val on = index == selected
                Box(
                    Modifier
                        .pressable { onSelect(index) }
                        .glass(RoundedCornerShape(50), tint = if (on) Ui.Ice else Color.White, strength = if (on) 2.4f else 0.8f)
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                ) {
                    Text(name, fontSize = 14.sp, color = if (on) Ui.Text else Ui.TextDim)
                }
            }
        }
    }
}

/** Campo de texto de vidro. */
@Composable
fun GlassField(
    value: String, onValueChange: (String) -> Unit, placeholder: String,
    modifier: Modifier = Modifier, numeric: Boolean = false
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text),
        modifier = modifier.height(46.dp).glass(RoundedCornerShape(23.dp)).padding(horizontal = 16.dp),
        singleLine = true,
        textStyle = TextStyle(color = Ui.Text, fontSize = 15.sp),
        cursorBrush = SolidColor(Ui.Ice),
        decorationBox = { inner ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, color = Ui.TextDim, fontSize = 15.sp)
                inner()
            }
        }
    )
}
