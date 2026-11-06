package com.orra.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { OrraTheme { OrraApp() } }
    }
}

private data class Tab(val label: String, val icon: ImageVector, val color: Color)

private val tabs = listOf(
    Tab("Chats", Icons.Filled.Email, GlowChats),
    Tab("Calls", Icons.Filled.Call, GlowCalls),
    Tab("Keypad", Icons.Filled.Phone, GlowKeypad),
    Tab("Contacts", Icons.Filled.Person, GlowContacts),
    Tab("Settings", Icons.Filled.Settings, GlowSettings)
)

@Composable
fun OrraApp() {
    var splash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(1200); splash = false }
    if (splash) { Splash(); return }

    var selected by remember { mutableIntStateOf(2) }
    Scaffold(bottomBar = { NeonBar(selected) { selected = it } }) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            Box(Modifier.fillMaxSize().statusBarsPadding()) {
                if (selected == 1) CallsScreen()
                else if (selected == 2) DialerScreen()
                else if (selected == 3) ContactsScreen()
                else if (selected == 4) SettingsScreen()
                else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(tabs[selected].label + " — coming soon")
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 7.dp)
            ) {
                OrraDynamicIsland()
            }
        }
    }
}

@Composable
private fun NeonBar(selected: Int, onSelect: (Int) -> Unit) {
    val shape = RoundedCornerShape(34.dp)
    Row(
        Modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f), shape)
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), shape)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tabs.forEachIndexed { i, t -> NavItem(t, selected == i) { onSelect(i) } }
    }
}

@Composable
private fun RowScope.NavItem(tab: Tab, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    val tint = if (selected) tab.color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
    Column(
        Modifier
            .weight(1f)
            .height(62.dp)
            .neonPress(tab.color, 22.dp, if (selected) 0.8f else 0f, onClick)
            .background(if (selected) tab.color.copy(alpha = 0.12f) else Color.Transparent, shape)
            .then(if (selected) Modifier.border(1.5.dp, tab.color, shape) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(tab.icon, contentDescription = tab.label, tint = tint, modifier = Modifier.size(24.dp))
        Text(tab.label, fontSize = 11.sp, color = tint)
    }
}

@Composable
private fun Splash() {
    Box(Modifier.fillMaxSize().background(Ink), contentAlignment = Alignment.Center) {
        OrraWordmark(Modifier.fillMaxWidth(0.55f), color = Color.White, dot = Mint)
    }
}
