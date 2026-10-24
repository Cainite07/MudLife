package cn.mudlife.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Shower
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector

/** Present the existing device/category hints with one consistent vector family. */
internal fun deviceSymbol(hint: String): ImageVector = when (hint) {
    "❄️" -> Icons.Outlined.AcUnit
    "♨️", "🔥" -> Icons.Outlined.LocalFireDepartment
    "🚿" -> Icons.Outlined.Shower
    "💨" -> Icons.Outlined.Air
    "📋" -> Icons.Outlined.ReceiptLong
    else -> Icons.Outlined.WaterDrop
}
