package cn.mudlife.app.model

import androidx.compose.ui.graphics.Color

data class DeviceInfo(
    val deviceId: Int,
    val deviceName: String,
    val snCode: String,
    val macAddress: String,
    val withholdMoney: Double,
    val onlineStatusId: Int,
    val projectId: Int? = null,
    val bigTypeId: Int? = null,
    val smallTypeId: Int? = null,
    val bigTypeName: String? = null,
    val smallTypeName: String? = null
) {
    val displayName: String get() = formatDeviceName(deviceName)
    val locationOnly: String get() = displayName

    val isDrinkingWater: Boolean get() =
        bigTypeId == 5 || deviceName.contains("饮水") || deviceName.contains("冷水") || (deviceName.contains("热水") && !deviceName.startsWith("热水器") && !deviceName.startsWith("热水表"))

    val isWashbasin: Boolean get() = deviceName.startsWith("洗手台")

    val typeEmoji: String get() = when {
        isDrinkingWater -> "🚰"
        isWashbasin -> "🪥"
        else -> "🚿"
    }

    val typeName: String get() = when {
        isDrinkingWater -> "直饮水"
        isWashbasin -> "洗手台"
        else -> "出水设备"
    }

    val actionText: String get() = when {
        isDrinkingWater -> "开始饮水"
        isWashbasin -> "开始出水"
        else -> "开始出水"
    }

    val stopText: String get() = when {
        isDrinkingWater -> "结束饮水"
        isWashbasin -> "结束出水"
        else -> "结束出水"
    }

    val startingText: String get() = when {
        isDrinkingWater -> "正在开启饮水机..."
        isWashbasin -> "正在开启出水..."
        else -> "正在开启出水..."
    }

    val stoppingText: String get() = when {
        isDrinkingWater -> "正在关闭饮水机..."
        isWashbasin -> "正在关闭出水..."
        else -> "正在关闭出水..."
    }

    val typeColor: Color get() = when {
        isDrinkingWater -> Color(0xFF0284C7)
        isWashbasin -> Color(0xFFFFCC80)
        else -> Color(0xFF0284C7)
    }

    val statusText: String get() = when {
        isDrinkingWater -> "正在出水中"
        isWashbasin -> "正在出水中"
        else -> "正在出水中"
    }

    companion object {
        fun formatDeviceName(name: String): String {
            var n = name
                .replace(Regex("^(直饮)?(开水|冷水|热水|温水)[- ]*"), "")
                .replace(Regex("^平衡[- ]*"), "")
                .replace(Regex("^直饮[冷热]?水[- ]*"), "")
                .replace(Regex("^热水[器表][- ]*"), "")
                .replace(Regex("^洗手台\\d*[- ]*"), "")
                .replace(Regex("-\\d+层-"), "-")
                .replace(Regex("-\\d+栋-"), "-")
                .replace(Regex("洗手台$"), "房")
                .replace("-", " ")
                .trim()
            if (n.startsWith("平衡 ")) {
                n = n.substringAfter("平衡 ").trim()
            }
            return n
        }
    }
}

data class NearbyDevice(
    val name: String,
    val mac: String,
    val rssi: Int,
    val deviceInfo: DeviceInfo? = null
) {
    val displayName: String get() = deviceInfo?.displayName ?: DeviceInfo.formatDeviceName(name)
    val signalText: String get() = "信号强度: $rssi dBm"

    // 优先从 deviceInfo 获取类型，扫描阶段默认 🚿
    val typeEmoji: String get() = deviceInfo?.typeEmoji ?: "🚿"
    val typeColor: Color get() = deviceInfo?.typeColor ?: if (name.startsWith("洗手台")) Color(0xFFFFCC80) else Color(0xFF2563EB)
}

data class RecentDevice(
    val name: String,
    val mac: String,
    val snCode: String,
    val emoji: String = "🚰",
    val waterType: String = "冷水",
    val projectId: String? = null
) {
    val isHot: Boolean get() = waterType == "热水" || waterType == "开水" || name.contains("热") || name.contains("开水") || snCode.contains(",M,")
    val cleanName: String get() = DeviceInfo.formatDeviceName(name).ifEmpty { if (isHot) "热水直饮机" else "冷水直饮机" }
}
