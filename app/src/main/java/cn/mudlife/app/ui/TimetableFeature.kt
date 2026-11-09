package cn.mudlife.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable

internal interface TimetableFeature {
    @Composable
    fun rememberTab(activity: ComponentActivity, owner: String?): TimetableTabPage?
}

internal interface TimetableTabPage {
    @Composable
    fun Content(contentPadding: PaddingValues, onAccountChanged: () -> Unit)
}
