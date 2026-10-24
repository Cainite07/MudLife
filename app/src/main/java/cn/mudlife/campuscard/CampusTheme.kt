package cn.mudlife.campuscard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.mudlife.app.ui.theme.MudLifeThemeSpecific
import cn.mudlife.app.ui.theme.ThemeMode

@Composable fun CampusTheme(content: @Composable () -> Unit) =
    MudLifeThemeSpecific(if (isSystemInDarkTheme()) ThemeMode.DARK else ThemeMode.LIGHT, content)

@Composable internal fun Paper(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp), border = BorderStroke(.65.dp, MaterialTheme.colorScheme.outline)) {
        Column(Modifier.padding(16.dp), content = content)
    }
}
