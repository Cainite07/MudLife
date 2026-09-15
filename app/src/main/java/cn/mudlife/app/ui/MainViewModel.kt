package cn.mudlife.app.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.mudlife.app.api.NetworkModule
import cn.mudlife.app.api.closeOrderSafe
import cn.mudlife.app.api.closeOrderResultSafe
import cn.mudlife.app.api.downRateSafe
import cn.mudlife.app.api.downRateResultSafe
import cn.mudlife.app.api.getBillListSafe
import cn.mudlife.app.api.getUserProjectsSafe
import cn.mudlife.app.api.getDeviceInfoSafe
import cn.mudlife.app.api.getUseCodeSafe
import cn.mudlife.app.api.updateUseCodeStatusSafe
import cn.mudlife.app.api.generateUseCodeSafe
import cn.mudlife.app.api.getWalletSafe
import cn.mudlife.app.api.queryUsingSafe
import cn.mudlife.app.model.ActiveOrder
import cn.mudlife.app.model.UseCodeData
import cn.mudlife.app.model.BillItem
import cn.mudlife.app.model.BillDTO
import cn.mudlife.app.model.DeviceInfo
import cn.mudlife.app.model.MqttOrderMsg
import cn.mudlife.app.model.NearbyDevice
import cn.mudlife.app.model.WalletData
import cn.mudlife.app.utils.MqttManager
import cn.mudlife.app.utils.PrefsHelper
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

class MainViewModel : ViewModel() {

    var walletInfo by mutableStateOf<WalletData?>(null)
    val nearbyDevices = mutableStateListOf<NearbyDevice>()
    var isScanning by mutableStateOf(false)
    private var scanStartTime = 0L

    var isShowering by mutableStateOf(false)
    var isStartingShower by mutableStateOf(false)
    var isStopping by mutableStateOf(false)
    var showerSnCode by mutableStateOf<String?>(null)
    var currentOrderNo by mutableStateOf<String?>(null)
    var showerConsumed by mutableStateOf(0.0)
    var showerPreDeduct by mutableStateOf(0.0)
    var showerRemaining by mutableStateOf("0.00")
    var showerElapsedSec by mutableStateOf(0)
    var autoDisConSec by mutableStateOf(0)
    var showerError by mutableStateOf<String?>(null)
    var isOwner by mutableStateOf(true)

    // 自动关停确认弹窗
    var showAutoCloseDialog by mutableStateOf(false)
    var autoCloseDeviceName by mutableStateOf("")
    var autoCloseElapsed by mutableStateOf(0)
    var autoCloseConsumed by mutableStateOf(0.0)
    var autoCloseLoading by mutableStateOf(false)

    var toastMessage by mutableStateOf<String?>(null)
    var kickedOut by mutableStateOf(false)

    var selectedDevice by mutableStateOf<DeviceInfo?>(null)
    var showDeviceDetail by mutableStateOf(false)

    var lastDeviceName by mutableStateOf("")
    var lastDeviceMac by mutableStateOf("")
    var lastDeviceSnCode by mutableStateOf("")
    var lastDeviceEmoji by mutableStateOf("🚰")

    // 常用饮水机列表
    var recentDevices by mutableStateOf<List<cn.mudlife.app.model.RecentDevice>>(emptyList())

    val activeOrders = mutableStateListOf<ActiveOrder>()
    private var activeDeviceSnCodes = mutableSetOf<String>()
    private val fetchingMacs = mutableSetOf<String>()
    private val gson = Gson()
    private var mqttManager: MqttManager? = null
    private var timerJob: Job? = null
    private var orderPollJob: Job? = null

    var billList by mutableStateOf<List<BillItem>>(emptyList())
    var isLoadingBills by mutableStateOf(false)
    var useCodeData by mutableStateOf<UseCodeData?>(
        if (PrefsHelper.useCode.isNotEmpty()) {
            UseCodeData(
                useCode = PrefsHelper.useCode,
                useCodeStatus = if (PrefsHelper.useCodeStatus) 1 else 0
            )
        } else null
    )
    var campusCardBalance by mutableStateOf(
        if ((PrefsHelper.campusCardBalance.toDoubleOrNull() ?: 0.0) > 0.0) PrefsHelper.campusCardBalance else ""
    )
    var campusCardBalanceTime by mutableStateOf(PrefsHelper.campusCardBalanceTime)

    // ── 吹风机使用码 20 次抽选架构 ──
    var candidateUseCode by mutableStateOf("")
    var remainUseCodeTimes by mutableStateOf(20)
    var isRollingUseCode by mutableStateOf(false)
    var showDryerCodeModal by mutableStateOf(false)
    private var useCodeLastConfirmedAt by mutableStateOf(PrefsHelper.useCodeLastConfirmedAt)

    // ── 国内免翻墙检查更新架构 ──
    var isCheckingUpdate by mutableStateOf(false)
    var updateInfo by mutableStateOf<cn.mudlife.app.utils.UpdateInfo?>(null)
    var showUpdateDialog by mutableStateOf(false)
    var isDownloadingUpdate by mutableStateOf(false)
    var downloadProgress by mutableStateOf(0f)

    // ── 后勤热水使用码 (江大专区) ──
    var qzhqUseCodeData by mutableStateOf<cn.mudlife.app.model.QzhqUseCodeData?>(
        if (PrefsHelper.qzhqRandomCode.isNotEmpty()) {
            cn.mudlife.app.model.QzhqUseCodeData(
                randomCode = PrefsHelper.qzhqRandomCode,
                useCodeStatus = PrefsHelper.qzhqStatus
            )
        } else null
    )
    var isQzhqLoggedIn by mutableStateOf(PrefsHelper.isQzhqLoggedIn)
    var qzhqLoading by mutableStateOf(false)

    fun initManagers(context: Context) {
        if (mqttManager == null) mqttManager = MqttManager(context, { handleMqttMessage(it) })

        lastDeviceName = PrefsHelper.lastDeviceName
        lastDeviceMac = PrefsHelper.lastDeviceMac
        lastDeviceSnCode = PrefsHelper.lastDeviceSnCode
        lastDeviceEmoji = PrefsHelper.lastDeviceEmoji

        // 恢复所有活跃订单，并主动并发向服务器核实（清除幽灵订单）
        val saved = PrefsHelper.getActiveOrders()
        activeOrders.clear()
        activeOrders.addAll(saved)
        saved.forEach { activeDeviceSnCodes.add(it.snCode) }
        recentDevices = PrefsHelper.getRecentDevices()

        if (saved.isNotEmpty()) {
            cleanInactiveOrders()
        }
    }

    fun cleanInactiveOrders() {
        viewModelScope.launch {
            val toRemove = mutableListOf<String>()
            val current = activeOrders.toList()
            current.forEach { order ->
                try {
                    val q = NetworkModule.apiService.queryUsingSafe(snCode = order.snCode, auth = NetworkModule.authFields(overrideProjectId = order.projectId))
                    if (q.success && q.data?.orderNo == null && q.errorCode != 307) {
                        toRemove.add(order.snCode)
                    }
                } catch (_: Exception) {}
            }
            if (toRemove.isNotEmpty()) {
                activeOrders.removeAll { toRemove.contains(it.snCode) }
                activeDeviceSnCodes.removeAll(toRemove.toSet())
                toRemove.forEach { sn ->
                    PrefsHelper.setStartedAt(sn, 0L)
                    PrefsHelper.clearAutoDiscon(sn)
                }
                saveOrders()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel(); orderPollJob?.cancel()
        mqttManager?.disconnect()
    }

    fun startScan() {
        nearbyDevices.clear(); activeDeviceSnCodes.clear()
        isScanning = true; scanStartTime = System.currentTimeMillis(); onScanTimeout()
    }
    fun onScanTimeout() {
        val e = System.currentTimeMillis() - scanStartTime
        if (e < 600) viewModelScope.launch { delay(600 - e); isScanning = false }
        else isScanning = false
    }

    // ── 扫码绑定 ──
    fun scanBind(snCode: String) {
        viewModelScope.launch {
            try {
                val resp = NetworkModule.apiService.getDeviceInfoSafe(snCode)
                if (resp.success && resp.data != null) {
                    val info = resp.data
                    // 保存为常用设备
                    val waterType = if (info.snCode.contains(",G,") || info.displayName.contains("冷")) "冷水"
                                    else if (info.snCode.contains(",M,") || info.displayName.contains("热")) "热水"
                                    else "直饮水"
                    val recentDev = cn.mudlife.app.model.RecentDevice(
                        name = info.displayName,
                        mac = info.macAddress,
                        snCode = info.snCode,
                        emoji = info.typeEmoji,
                        waterType = waterType,
                        projectId = info.projectId?.toString()
                    )
                    PrefsHelper.addRecentDevice(recentDev)
                    recentDevices = PrefsHelper.getRecentDevices()
                    // 弹出设备详情
                    selectedDevice = info; showDeviceDetail = true
                    refreshDeviceStatus(info.snCode, info.projectId?.toString())
                    isScanning = false
                } else {
                    toastMessage = resp.displayMessage ?: "未找到该设备"
                    checkKick(resp.displayMessage)
                }
            } catch (e: Exception) {
                checkKickEx(e)
                val msg = e.message ?: ""
                toastMessage = if (msg.contains("Unable to resolve host", ignoreCase = true) ||
                    msg.contains("No address associated", ignoreCase = true) ||
                    msg.contains("Network is unreachable", ignoreCase = true) ||
                    msg.contains("Failed to connect", ignoreCase = true)) {
                    "网络连接失败，请检查网络设置"
                } else {
                    "查询设备失败"
                }
            }
        }
    }

    // ── 点击设备 ──
    fun fetchDeviceInfo(mac: String) {
        val cached = nearbyDevices.find { it.mac == mac }?.deviceInfo
        if (cached != null) {
            selectedDevice = cached; showDeviceDetail = true
            viewModelScope.launch { refreshDeviceStatus(cached.snCode, cached.projectId?.toString()) }
            return
        }
        viewModelScope.launch {
            try {
                val resp = NetworkModule.apiService.getDeviceInfoSafe(mac)
                if (resp.success && resp.data != null) {
                    selectedDevice = resp.data; showDeviceDetail = true
                    refreshDeviceStatus(resp.data.snCode, resp.data.projectId?.toString())
                } else checkKick(resp.displayMessage)
            } catch (e: Exception) {
                checkKickEx(e)
                val msg = e.message ?: ""
                if (msg.contains("Unable to resolve host", ignoreCase = true) ||
                    msg.contains("No address associated", ignoreCase = true) ||
                    msg.contains("Network is unreachable", ignoreCase = true) ||
                    msg.contains("Failed to connect", ignoreCase = true)) {
                    toastMessage = "网络连接失败，请检查网络设置"
                }
            }
        }
    }

    private suspend fun refreshDeviceStatus(snCode: String, overrideProjectId: String? = null) {
        try {
            val pid = overrideProjectId ?: selectedDevice?.projectId?.toString()?.takeIf { it.isNotBlank() && it != "0" && it != "null" }
            val q = NetworkModule.apiService.queryUsingSafe(snCode = snCode, auth = NetworkModule.authFields(overrideProjectId = pid))
            if (q.errorCode == 307 || (q.success && q.data?.orderNo != null)) {
                activeDeviceSnCodes.add(snCode)
                isOwner = q.data?.isOwner ?: true
                // 只有自己的订单才加入 activeOrders
                if (isOwner && activeOrders.none { it.snCode == snCode }) {
                    val orderNo = q.data?.orderNo ?: ""
                    val deviceInfo = nearbyDevices.find { it.deviceInfo?.snCode == snCode }?.deviceInfo ?: selectedDevice
                    if (deviceInfo != null) {
                        activeOrders.add(ActiveOrder(snCode, orderNo, deviceInfo.displayName, deviceInfo.macAddress, deviceInfo.typeEmoji, deviceInfo.withholdMoney, projectId = pid))
                        saveOrders()
                    }
                }
            } else isOwner = true
        } catch (_: Exception) {}
    }

    fun startLastDevice(phone: String) {
        val sn = lastDeviceSnCode.ifEmpty { return }
        // 找对应的活跃订单
        val order = activeOrders.find { it.snCode == sn }
        if (order != null) {
            viewModelScope.launch {
                try {
                    val resp = NetworkModule.apiService.getDeviceInfoSafe(order.deviceMac)
                    if (resp.success && resp.data != null) {
                        selectedDevice = resp.data; startShower(phone)
                    } else {
                        toastMessage = "获取设备信息失败"
                    }
                } catch (e: Exception) {
                    toastMessage = "获取设备信息失败"
                }
            }
            return
        }
        val mac = lastDeviceMac.ifEmpty { return }
        viewModelScope.launch {
            try {
                val resp = NetworkModule.apiService.getDeviceInfoSafe(mac)
                if (resp.success && resp.data != null) {
                    selectedDevice = resp.data; startShower(phone)
                } else {
                    toastMessage = "获取设备信息失败"
                }
            } catch (e: Exception) {
                toastMessage = "获取设备信息失败"
            }
        }
    }

    fun startRecentDevice(dev: cn.mudlife.app.model.RecentDevice, phone: String) {
        lastDeviceSnCode = dev.snCode
        lastDeviceMac = dev.mac
        lastDeviceName = dev.name
        lastDeviceEmoji = dev.emoji
        viewModelScope.launch {
            try {
                val queryKey = if (dev.mac.isNotEmpty()) dev.mac else dev.snCode
                val resp = NetworkModule.apiService.getDeviceInfoSafe(queryKey)
                if (resp.success && resp.data != null) {
                    val info = resp.data
                    selectedDevice = if (info.projectId == null && dev.projectId != null) {
                        info.copy(projectId = dev.projectId.toIntOrNull())
                    } else info
                    startShower(phone)
                } else {
                    toastMessage = resp.displayMessage ?: "获取饮水机信息失败"
                }
            } catch (e: Exception) {
                toastMessage = "网络连接失败，请重试"
            }
        }
    }

    fun pinRecentDrinkingDevice(snCode: String) {
        PrefsHelper.pinRecentDevice(snCode)
        recentDevices = PrefsHelper.getRecentDevices()
    }

    fun deleteRecentDrinkingDevice(snCode: String) {
        PrefsHelper.deleteRecentDevice(snCode)
        recentDevices = PrefsHelper.getRecentDevices()
    }

    // ════════════════════════════════════════════
    fun startShower(phone: String) {
        val device = selectedDevice ?: return
        val snCode = device.snCode
        if (snCode.isNullOrBlank()) {
            showerError = "设备信息不完整"
            toastMessage = "设备信息不完整"
            return
        }

        isStartingShower = true
        viewModelScope.launch {
            try {
                showerError = null
                showerSnCode = snCode
                val devPid = device.projectId?.toString()?.takeIf { it.isNotBlank() && it != "0" && it != "null" }
                // 若该项目尚未同步过 accountId，主动拉取一次多项目列表（如直饮水 3255 匹配 38184）
                if (devPid != null && NetworkModule.getAccountIdForProject(devPid) == null) {
                    try {
                        val pResp = NetworkModule.apiService.getUserProjectsSafe()
                        if (pResp.success && !pResp.data.isNullOrEmpty()) {
                            val map = pResp.data.associate { it.projectId.toString() to it.accountId.toString() }
                            NetworkModule.setProjectAccounts(map)
                            PrefsHelper.saveProjectAccounts(map)
                            cn.mudlife.app.utils.AppLogger.i("Shower", "开阀前补全多项目账户映射: $map")
                        }
                    } catch (_: Exception) {}
                }
                val devAuth = NetworkModule.authFields(overrideProjectId = devPid)

                val existing = NetworkModule.apiService.queryUsingSafe(snCode = snCode, auth = devAuth)
                if (existing.errorCode == 307 || (existing.success && existing.data?.orderNo != null)) {
                    val oNo = existing.data?.orderNo ?: ""
                    if (activeOrders.none { it.snCode == snCode }) {
                        activeOrders.add(ActiveOrder(snCode, oNo, device.displayName, device.macAddress, device.typeEmoji, device.withholdMoney, projectId = devPid))
                        saveOrders()
                    }
                    // 恢复订单：从持久化恢复自动关停剩余时间
                    val remain = PrefsHelper.getAutoDisconRemain(snCode)
                    enterShowerState(oNo, snCode, device, remain)
                    return@launch
                }

                mqttManager?.connect(phone)
                val resp = NetworkModule.apiService.downRateSafe(snCode = snCode, auth = devAuth)
                if (!resp.success) {
                    val msg = resp.displayMessage ?: "开始失败"
                    showerError = msg
                    toastMessage = "出水失败: $msg"
                    cn.mudlife.app.utils.AppLogger.e("Shower", "出水失败: $msg | 设备: ${device.displayName}, sn: $snCode, projectId: $devPid, accountId: ${devAuth["accountId"]}")
                    checkKick(resp.displayMessage); mqttManager?.disconnect(); return@launch
                }

                // 开阀确认：轮询 downRateResult，确认开阀成功
                var opened = false
                var autoDiscon = resp.data?.autoDisConTime ?: 0
                for (i in 0..8) {
                    delay(700)
                    try {
                        val r = NetworkModule.apiService.downRateResultSafe(snCode = snCode, auth = devAuth)
                        val d = r.data
                        if (r.success && (d?.state == 0 || d?.result == 0 || d?.orderNo != null)) {
                            opened = true
                            val autoTime = d?.autoDisConTime
                            if (autoTime != null && autoTime > 0) {
                                autoDiscon = autoTime
                            }
                            break
                        }
                    } catch (_: Exception) {}
                }

                if (!opened) {
                    // 开阀确认失败，退出并提示
                    showerError = "开阀未确认成功，请确认设备是否已开启"
                    toastMessage = "开阀未确认，请检查水机或网络重试"
                    cn.mudlife.app.utils.AppLogger.e("Shower", "开阀确认失败: 9次轮询未返回开启状态 | 设备: ${device.displayName}, sn: $snCode, devPid: $devPid")
                    mqttManager?.disconnect()
                    return@launch
                }

                // 记录开阀时间戳（无条件重置为当前开阀时间，秒数清零）
                val now = System.currentTimeMillis()
                PrefsHelper.setStartedAt(snCode, now)
                showerElapsedSec = 0

                val wType = if (snCode.contains(",G,") || device.displayName.contains("冷")) "冷水"
                            else if (snCode.contains(",M,") || device.displayName.contains("热")) "热水"
                            else "直饮水"
                PrefsHelper.addRecentDevice(cn.mudlife.app.model.RecentDevice(
                    name = device.displayName,
                    mac = device.macAddress,
                    snCode = device.snCode,
                    emoji = device.typeEmoji,
                    waterType = wType,
                    projectId = devPid
                ))
                recentDevices = PrefsHelper.getRecentDevices()

                activeOrders.add(ActiveOrder(snCode, "", device.displayName, device.macAddress, device.typeEmoji, device.withholdMoney, projectId = devPid))
                saveOrders()
                enterShowerState(null, snCode, device, autoDiscon)

                orderPollJob?.cancel()
                orderPollJob = viewModelScope.launch {
                    for (i in 0..10) {
                        delay(800)
                        if (currentOrderNo != null) break
                        try {
                            val p = NetworkModule.apiService.queryUsingSafe(snCode = snCode, auth = devAuth)
                            if (p.errorCode == 307 || (p.success && p.data?.orderNo != null)) {
                                currentOrderNo = p.data?.orderNo
                                updateOrderNo(snCode, currentOrderNo ?: "")
                            } else checkKick(p.displayMessage)
                        } catch (e: Exception) { checkKickEx(e) }
                    }
                }
            } catch (e: Exception) {
                checkKickEx(e)
                val msg = e.message ?: "网络错误"
                if (!isShowering) {
                    showerError = msg
                    toastMessage = "出水异常: $msg"
                }
            } finally { isStartingShower = false }
        }
    }

    private fun updateOrderNo(snCode: String, orderNo: String) {
        val i = activeOrders.indexOfFirst { it.snCode == snCode }
        if (i >= 0 && orderNo.isNotEmpty()) {
            activeOrders[i] = activeOrders[i].copy(orderNo = orderNo)
            saveOrders()
        }
    }

    private fun enterShowerState(orderNo: String?, snCode: String, device: DeviceInfo, autoDiscon: Int = 0) {
        currentOrderNo = orderNo; isShowering = true
        showerPreDeduct = device.withholdMoney; showerConsumed = 0.0
        showerRemaining = "%.2f".format(device.withholdMoney)
        activeDeviceSnCodes.add(snCode)

        // 自动关停倒计时：仅在已知且尚未开始时初始化
        if (autoDiscon > 0 && PrefsHelper.getAutoDisconRemain(snCode) <= 0) {
            autoDisConSec = autoDiscon
            PrefsHelper.setAutoDisconRemain(snCode, autoDiscon)
        }

        val now = System.currentTimeMillis()
        val st = PrefsHelper.getStartedAt(snCode)
        val isValidSt = st > 0L && st <= now && (now - st) < 4 * 3600 * 1000L
        if (orderNo == null || !isValidSt) {
            PrefsHelper.setStartedAt(snCode, now)
            showerElapsedSec = 0
        } else {
            showerElapsedSec = ((now - st) / 1000).toInt()
        }

        lastDeviceName = device.displayName; lastDeviceMac = device.macAddress; lastDeviceSnCode = snCode; lastDeviceEmoji = device.typeEmoji
        val waterType = if (snCode.contains(",G,") || device.displayName.contains("冷")) "冷水"
                        else if (snCode.contains(",M,") || device.displayName.contains("热")) "热水"
                        else "直饮水"
        val recentDev = cn.mudlife.app.model.RecentDevice(
            name = device.displayName,
            mac = device.macAddress,
            snCode = snCode,
            emoji = device.typeEmoji,
            waterType = waterType
        )
        PrefsHelper.addRecentDevice(recentDev)
        recentDevices = PrefsHelper.getRecentDevices()

        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var tick = 0
            while (isShowering) {
                delay(500); tick++
                val curSt = PrefsHelper.getStartedAt(snCode)
                if (curSt > 0) {
                    val diff = (System.currentTimeMillis() - curSt) / 1000
                    showerElapsedSec = if (diff >= 0) diff.toInt() else 0
                }

                // 自动关停倒计时递减（每秒递减，500ms * 2 = 1s）
                if (tick % 2 == 0) {
                    val remain = PrefsHelper.getAutoDisconRemain(snCode)
                    if (remain > 0) {
                        val newRemain = remain - 1
                        PrefsHelper.setAutoDisconRemain(snCode, newRemain)
                        autoDisConSec = newRemain
                        if (newRemain <= 0) {
                            onAutoClose(snCode)
                            return@launch
                        }
                    }
                }

                // 0.5 秒极速探活：实时检查设备是否已被机身按键关闭
                try {
                    val overridePid = selectedDevice?.projectId?.toString()
                    val q = NetworkModule.apiService.queryUsingSafe(snCode = snCode, auth = NetworkModule.authFields(overrideProjectId = overridePid))
                    if (q.success && q.data?.orderNo == null && q.errorCode != 307) {
                        // 设备已在机身按键关闭 → 立即完成出水
                        onAutoClose(snCode)
                        return@launch
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun handleMqttMessage(message: String) {
        try {
            val msg = gson.fromJson(message, MqttOrderMsg::class.java)
            msg.orderNo?.let { orderNo -> if (currentOrderNo == null) { currentOrderNo = orderNo; showerSnCode?.let { sn -> updateOrderNo(sn, orderNo) } } }
            msg.consumeMoney?.let {
                showerConsumed = it
                showerRemaining = "%.2f".format(if (showerPreDeduct - it < 0) 0.0 else showerPreDeduct - it)
            }
        } catch (_: Exception) {}
    }

    /**
     * 设备自动关闭：停止计时，查询消费金额，弹出确认框。
     * 用户点确认后才真正退出（finishShower）。
     */
    private fun onAutoClose(snCode: String) {
        if (!isShowering) return
        // 停止计时（弹窗期间洗澡界面不再走秒）
        timerJob?.cancel(); orderPollJob?.cancel()

        autoCloseDeviceName = selectedDevice?.displayName ?: lastDeviceName.ifEmpty { "热水器" }
        autoCloseElapsed = showerElapsedSec
        autoCloseConsumed = 0.0
        autoCloseLoading = false
        showAutoCloseDialog = true

        val startTime = PrefsHelper.getStartedAt(snCode)
        // 立即清除持久化的开阀时间戳与倒计时，防止用户不点弹窗确认直接退出时残留脏时间戳
        PrefsHelper.setStartedAt(snCode, 0L)
        PrefsHelper.clearAutoDiscon(snCode)

        // 后台异步静默等账单结算，若拿到金额则更新弹窗显示，不阻塞用户点击确认退出
        viewModelScope.launch {
            val orderNo = currentOrderNo ?: activeOrders.find { it.snCode == snCode }?.orderNo ?: ""
            try {
                val amount = queryLastBillAmount(orderNo, startTime) ?: 0.0
                if (amount > 0.0) {
                    autoCloseConsumed = amount
                }
            } catch (_: Exception) {}
        }
    }

    /** 用户点确认：退出洗澡界面并清理 */
    fun confirmAutoClose() {
        showAutoCloseDialog = false
        val snCode = showerSnCode ?: ""
        finishShower(snCode, null)
    }

    // ════════════════════════════════════════════
    fun stopShower(skipNetwork: Boolean = false) {
        if (!isShowering || isStopping) return
        val snCode = showerSnCode ?: ""
        val oNo = currentOrderNo ?: activeOrders.find { it.snCode == snCode }?.orderNo ?: ""

        // 挤号等场景：loginCode 已失效，跳过网络请求，直接本地清理，避免再次触发挤号
        if (skipNetwork) {
            finishShower(snCode, null)
            return
        }

        isStopping = true
        viewModelScope.launch {
            try {
                val activeOrder = activeOrders.find { it.snCode == snCode }
                val devPid = selectedDevice?.projectId?.toString()?.takeIf { it.isNotBlank() && it != "0" && it != "null" }
                    ?: activeOrder?.projectId
                val devAuth = NetworkModule.authFields(overrideProjectId = devPid)

                var orderNo = oNo
                if (orderNo.isEmpty()) {
                    val p = NetworkModule.apiService.queryUsingSafe(snCode = snCode, auth = devAuth)
                    orderNo = p.data?.orderNo ?: ""
                }

                // 1. 发送关阀指令
                val close = NetworkModule.apiService.closeOrderSafe(snCode = snCode, orderNo = orderNo, auth = devAuth)
                if (!close.success) {
                    checkKick(close.displayMessage)
                    // 服务器已接受/已在关闭中时也视为成功
                    if (close.errorCode == 307 || close.displayMessage?.contains("已在") == true) {
                        finishShower(snCode, null)
                    } else {
                        toastMessage = close.displayMessage ?: "关闭失败，请重试"
                        cn.mudlife.app.utils.AppLogger.e("Shower", "关阀请求失败: ${close.displayMessage} | sn: $snCode, 订单: $orderNo, devPid: $devPid")
                        isStopping = false
                    }
                    return@launch
                }

                // 2. 轮询确认关阀结果（最多 5 次，间隔 1 秒）
                var closedOk = false
                for (i in 0 until 5) {
                    delay(1000)
                    try {
                        val r = NetworkModule.apiService.closeOrderResultSafe(snCode = snCode, orderNo = orderNo, auth = devAuth)
                        if (r.success) {
                            val d = r.data
                            val closed = d == null ||
                                d.state == 0 ||
                                d.status == 0 ||
                                d.orderNo.isNullOrEmpty()
                            if (closed) { closedOk = true; break }
                        } else {
                            checkKick(r.displayMessage)
                        }
                    } catch (_: Exception) {}
                }

                // 3. 关阀确认成功后：先立即退出（不阻塞），后台异步等账单结算后弹金额
                if (closedOk) {
                    val startTime = PrefsHelper.getStartedAt(snCode)  // 开阀时间戳，未开始时为 0
                    finishShower(snCode, null)
                    // 后台轮询账单（最多 10 次 × 2 秒 = 20 秒），拿到金额后弹 toast
                    viewModelScope.launch {
                        val amount = queryLastBillAmount(orderNo, startTime)
                        val isWater = selectedDevice?.isDrinkingWater == true ||
                            selectedDevice?.displayName?.contains("饮水") == true ||
                            (activeOrder?.deviceName?.contains("饮水") == true)
                        val devTitle = if (isWater) "饮水机" else "热水器"
                        if (amount != null) {
                            toastMessage = if (amount > 0) {
                                "已停止，本次消费 ¥%.2f".format(amount)
                            } else {
                                "${devTitle}已关闭，本次无消费"
                            }
                        } else {
                            toastMessage = "${devTitle}已关闭"
                        }
                    }
                } else {
                    finishShower(snCode, null)
                }
            } catch (e: Exception) {
                checkKickEx(e)
                toastMessage = "停止用水失败，请检查网络后重试"
                isStopping = false
            }
        }
    }

    /**
     * 查询账单获取本次消费金额（后台异步调用，不阻塞关闭流程）。
     * 账单结算可能有延迟，故轮询最多 10 次（每次间隔 2 秒，共约 20 秒）等服务器结算完成。
     * 只统计 [startTime]（开阀时间戳，毫秒）之后产生的账单，避免读到上一次的消费；
     * 优先匹配当前订单号（billRequestType=2 时 orderId 与 orderNo 对应）。
     * 超时仍无消费时返回 0.0。
     */
    private suspend fun queryLastBillAmount(orderNo: String, startTime: Long): Double? {
        val parsers = listOf(
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()),
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault()),
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault())
        )
        val fmt = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.getDefault())
        val month = fmt.format(java.util.Calendar.getInstance().time)

        for (attempt in 0 until 10) {
            try {
                val resp = NetworkModule.apiService.getBillListSafe(month = month)
                val bills = resp.data ?: return null

                // 优先精确匹配订单号
                if (orderNo.isNotEmpty()) {
                    val matched = bills.firstOrNull { it.safeConsumeBillDTO.safeOrderId == orderNo }
                    if (matched != null) {
                        val m = matched.safeConsumeBillDTO.safeConsumeMoney.toDoubleOrNull()
                        if (m != null && m > 0) return m
                        // 金额仍为 0 → 可能结算中，继续轮询
                    }
                }

                // 开阀之后的账单取最新一笔
                val recent = bills.filter { bill ->
                    if (startTime <= 0) return@filter true
                    val t = parsers.asSequence()
                        .map { p -> try { p.parse(bill.safeConsumeBillDTO.safeConsumeDate)?.time ?: 0L } catch (_: Exception) { 0L } }
                        .maxOrNull() ?: 0L
                    t >= startTime
                }
                if (recent.isNotEmpty()) {
                    val latest = recent.maxByOrNull { it.safeConsumeBillDTO.safeConsumeDate }
                    val m = latest?.safeConsumeBillDTO?.safeConsumeMoney?.toDoubleOrNull()
                    if (m != null && m > 0) return m
                }
            } catch (_: Exception) {}
            if (attempt < 9) delay(2000)
        }
        return 0.0
    }

    /** 完成停止流程：退出洗澡界面，清理状态，显示结算结果 */
    private fun finishShower(snCode: String, consumed: Double?) {
        isShowering = false
        isStopping = false

        // 从活跃列表移除
        activeOrders.removeAll { it.snCode == snCode }
        saveOrders()
        activeDeviceSnCodes.remove(snCode)

        if (consumed != null) {
            toastMessage = "已停止，本次消费 ¥%.2f".format(consumed)
        } else {
            toastMessage = "热水器已关闭"
        }

        currentOrderNo = null; showerConsumed = 0.0; showerPreDeduct = 0.0
        showerRemaining = "0.00"; showerElapsedSec = 0
        autoDisConSec = 0
        PrefsHelper.setStartedAt(snCode, 0L) // 重置该设备计时器
        PrefsHelper.clearAutoDiscon(snCode)  // 清除自动关停倒计时
        showerSnCode = null; timerJob?.cancel(); orderPollJob?.cancel()
        try { mqttManager?.disconnect() } catch (_: Exception) {}
    }

    fun logout() {
        // 停止所有后台任务
        timerJob?.cancel()
        orderPollJob?.cancel()
        
        // 断开 MQTT 连接
        try { mqttManager?.disconnect() } catch (_: Exception) {}
        
        NetworkModule.clearAuth()
        
        // 重置所有状态
        isShowering = false
        showerSnCode = null
        currentOrderNo = null
        showerConsumed = 0.0
        showerPreDeduct = 0.0
        showerRemaining = "0.00"
        showerElapsedSec = 0
        showerError = null
        toastMessage = null
        kickedOut = false
        selectedDevice = null
        showDeviceDetail = false
        nearbyDevices.clear()
        recentDevices = emptyList()
        activeOrders.clear()
        activeDeviceSnCodes.clear()
        billList = emptyList()
        useCodeData = null
        walletInfo = null
    }

    private fun saveOrders() { PrefsHelper.saveActiveOrders(activeOrders.toList()) }

    fun isDeviceActive(snCode: String) = snCode in activeDeviceSnCodes

    // ── 寝室绑定 / 设备筛选 ──

    /** 当前是否有绑定寝室 */
    val hasBoundRoom: Boolean get() = PrefsHelper.boundRoom.isNotBlank()

    /** 从附近设备名提取位置关键词：去掉 "热水器-"/"热水表-"/"洗手台N-" 前缀 */
    fun extractLocationFromDevice(name: String): String {
        var n = name
        n = n.replaceFirst(Regex("^洗手台\\d*"), "").trim('-').trim()
        n = n.replaceFirst(Regex("^热水[器表]"), "").trim('-').trim()
        return n.trim()
    }

    /** 判断设备名是否匹配绑定的寝室（忽略大小写、空格、连字符） */
    fun matchesBoundRoom(deviceName: String): Boolean {
        val key = PrefsHelper.boundRoom.trim()
        if (key.isEmpty()) return true
        val norm = { s: String -> s.lowercase().replace(" ", "").replace("-", "") }
        return norm(deviceName).contains(norm(key))
    }

    // ── 挤号 ──
    private fun checkKick(msg: String?) {
        if (msg.isNullOrEmpty()) return
        val isRealKick = msg.contains("被挤下线") ||
                msg.contains("账号已在其他设备") ||
                msg.contains("在其他设备登录") ||
                msg.contains("在另一台设备登录") ||
                msg.contains("token失效") ||
                msg.contains("token过期")
        if (isRealKick) {
            cn.mudlife.app.utils.AppLogger.e("Auth", "触发账号强制下线: $msg")
            kickedOut = true
            PrefsHelper.clear()
        }
    }
    private fun checkKickEx(e: Exception) {
        val m = e.message ?: return
        if (m.contains("401") || m.contains("403") || m.contains("Unauthorized") || m.contains("Forbidden")) {
            cn.mudlife.app.utils.AppLogger.e("Auth", "触发网络鉴权失效下线: $m")
            kickedOut = true
            PrefsHelper.clear()
        }
    }

    fun refreshWallet() {
        viewModelScope.launch {
            try { 
                val resp = NetworkModule.apiService.getWalletSafe()
                if (resp.success) walletInfo = resp.data else checkKick(resp.displayMessage) 
            } catch (e: Exception) { 
                checkKickEx(e)
                val msg = e.message ?: ""
                if (msg.contains("Unable to resolve host", ignoreCase = true) ||
                    msg.contains("No address associated", ignoreCase = true) ||
                    msg.contains("Network is unreachable", ignoreCase = true) ||
                    msg.contains("Failed to connect", ignoreCase = true)) {
                    toastMessage = "网络连接失败，请检查网络设置"
                }
            }
        }
    }

    fun loadUseCode() {
        viewModelScope.launch {
            try {
                val resp = NetworkModule.apiService.getUseCodeSafe()
                if (resp.success && resp.data != null) {
                    val remoteCode = resp.data.useCode
                    val localCode = PrefsHelper.useCode
                    val hasLocalConfirmed = useCodeLastConfirmedAt > 0L && localCode.isNotEmpty()

                    if (hasLocalConfirmed && remoteCode != localCode) {
                        // 本地有通过 generate 确认的新码，但远端仍返回旧码 → 不覆盖
                        cn.mudlife.app.utils.AppLogger.i("UseCode", "远端返回旧码 $remoteCode，本地新码 $localCode 受保护不予覆盖")
                        useCodeData = useCodeData?.copy(useCodeStatus = resp.data.useCodeStatus)
                            ?: resp.data.copy(useCode = localCode)
                    } else {
                        // 远端返回与本地一致（同步成功）或本地无确认记录 → 正常采纳远端
                        useCodeData = resp.data
                        if (remoteCode.isNotEmpty()) {
                            PrefsHelper.useCode = remoteCode
                            PrefsHelper.useCodeStatus = resp.data.useCodeStatus == 1
                        }
                        // 远端已同步，清除保护标记
                        if (hasLocalConfirmed && remoteCode == localCode) {
                            useCodeLastConfirmedAt = 0L
                            PrefsHelper.useCodeLastConfirmedAt = 0L
                        }
                    }
                    if (resp.data.remainTimes in 0..20) {
                        remainUseCodeTimes = resp.data.remainTimes
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun toggleUseCodeStatus(enabled: Boolean) {
        viewModelScope.launch {
            try {
                val newStatus = if (enabled) 1 else 0
                val resp = NetworkModule.apiService.updateUseCodeStatusSafe(newStatus, NetworkModule.authFields())
                if (resp.success) {
                    useCodeData = useCodeData?.copy(useCodeStatus = newStatus)
                        ?: UseCodeData(useCode = PrefsHelper.useCode, useCodeStatus = newStatus)
                    PrefsHelper.useCodeStatus = enabled
                    toastMessage = if (enabled) "吹风机使用码已开启" else "吹风机使用码已关闭"
                } else {
                    toastMessage = resp.displayMessage.orEmpty().ifEmpty { "操作失败" }
                }
            } catch (_: Exception) {
                toastMessage = "网络请求失败，请检查网络"
            }
        }
    }

    fun openDryerCodeModal() {
        showDryerCodeModal = true
        if (candidateUseCode.isEmpty()) {
            if (remainUseCodeTimes > 0) {
                rollNewUseCode()
            } else {
                candidateUseCode = PrefsHelper.useCode
            }
        }
    }

    fun closeDryerCodeModal() {
        showDryerCodeModal = false
        candidateUseCode = ""
    }

    fun rollNewUseCode() {
        if (isRollingUseCode) return
        if (remainUseCodeTimes <= 0) {
            toastMessage = "今日抽选机会已用尽"
            return
        }
        viewModelScope.launch {
            isRollingUseCode = true
            try {
                val resp = NetworkModule.apiService.generateUseCodeSafe(NetworkModule.authFields())
                if (resp.success && resp.data != null) {
                    if (resp.data.useCode.isNotEmpty()) {
                        candidateUseCode = resp.data.useCode
                    }
                    if (resp.data.remainTimes in 0..20) {
                        remainUseCodeTimes = resp.data.remainTimes
                    } else if (remainUseCodeTimes > 0) {
                        remainUseCodeTimes--
                    }
                } else {
                    toastMessage = resp.displayMessage.orEmpty().ifEmpty { "抽选新码失败" }
                }
            } catch (_: Exception) {
                toastMessage = "网络请求失败，请检查网络"
            } finally {
                isRollingUseCode = false
            }
        }
    }

    fun confirmUseCandidateCode() {
        val codeToSave = candidateUseCode.ifEmpty { return }
        viewModelScope.launch {
            try {
                // 1. 本地立即生效并持久化，保持原有开启状态
                useCodeData = (useCodeData ?: UseCodeData()).copy(
                    useCode = codeToSave,
                    useCodeStatus = 1
                )
                PrefsHelper.useCode = codeToSave
                PrefsHelper.useCodeStatus = true
                useCodeLastConfirmedAt = System.currentTimeMillis()
                PrefsHelper.useCodeLastConfirmedAt = useCodeLastConfirmedAt
                showDryerCodeModal = false
                candidateUseCode = ""

                // 2. 链式调用服务端状态激活接口，确保服务端白名单与硬件同步
                NetworkModule.apiService.updateUseCodeStatusSafe(1, NetworkModule.authFields())
                toastMessage = "已成功更换并激活吹风机码"
            } catch (_: Exception) {
                toastMessage = "激活新码网络异常，请检查网络"
            }
        }
    }

    // ── 国内免翻墙检查更新方法 ──
    fun checkUpdate(isManual: Boolean = false) {
        if (isCheckingUpdate) return
        if (isManual) {
            toastMessage = "正在检测更新..."
        }
        viewModelScope.launch {
            isCheckingUpdate = true
            try {
                val result = cn.mudlife.app.utils.UpdateManager.checkUpdate()
                if (result.isSuccess) {
                    val info = result.getOrNull()
                    if (info != null && info.hasUpdate) {
                        updateInfo = info
                        showUpdateDialog = true
                    } else if (isManual) {
                        toastMessage = "当前已是最新版本"
                    }
                } else if (isManual) {
                    toastMessage = "检测更新失败，请检查网络后重试"
                }
            } catch (_: Exception) {
                if (isManual) toastMessage = "检测更新异常"
            } finally {
                isCheckingUpdate = false
            }
        }
    }

    fun closeUpdateDialog() {
        if (!isDownloadingUpdate) {
            showUpdateDialog = false
        }
    }

    fun startDownloadUpdate(context: android.content.Context) {
        val info = updateInfo ?: return
        if (isDownloadingUpdate) return
        viewModelScope.launch {
            isDownloadingUpdate = true
            downloadProgress = 0f
            try {
                val result = cn.mudlife.app.utils.UpdateManager.downloadApk(context, info) { progress ->
                    downloadProgress = progress
                }
                if (result.isSuccess) {
                    val file = result.getOrNull()
                    if (file != null) {
                        showUpdateDialog = false
                        cn.mudlife.app.utils.UpdateManager.installApk(context, file)
                    } else {
                        toastMessage = "安装包解析异常"
                    }
                } else {
                    toastMessage = "下载更新失败，正在为您调起浏览器直接下载"
                    val fallbackUrl = info.mirrors.firstOrNull() ?: info.downloadUrl
                    cn.mudlife.app.utils.UpdateManager.openBrowser(context, fallbackUrl)
                }
            } catch (_: Exception) {
                toastMessage = "下载更新异常"
            } finally {
                isDownloadingUpdate = false
            }
        }
    }

    // ── 后勤洗浴使用码控制 (江大专区) ──
    fun loadQzhqUseCode() {
        val phone = PrefsHelper.qzhqPhone.ifEmpty { PrefsHelper.telephone }
        val loginCode = PrefsHelper.qzhqLoginCode
        if (phone.isEmpty() || loginCode.isEmpty()) return

        viewModelScope.launch {
            qzhqLoading = true
            try {
                val resp = cn.mudlife.app.api.QzhqNetwork.getUseCode(phone, loginCode)
                if (resp.isSuccess && resp.data != null) {
                    qzhqUseCodeData = resp.data
                } else if (resp.errorCode == 8 || resp.errorCode == 401) {
                    isQzhqLoggedIn = false
                    PrefsHelper.qzhqLoginCode = ""
                    toastMessage = "后勤账号登录失效，请重新登录"
                }
            } catch (_: Exception) {
            } finally {
                qzhqLoading = false
            }
        }
    }

    fun toggleQzhqStatus(newStatus: Int) {
        val phone = PrefsHelper.qzhqPhone.ifEmpty { PrefsHelper.telephone }
        val loginCode = PrefsHelper.qzhqLoginCode
        if (phone.isEmpty() || loginCode.isEmpty()) {
            toastMessage = "请先登录后勤专区"
            return
        }

        viewModelScope.launch {
            try {
                val resp = cn.mudlife.app.api.QzhqNetwork.updateUseCodeStatus(newStatus, phone, loginCode)
                if (resp.isSuccess) {
                    qzhqUseCodeData = qzhqUseCodeData?.copy(useCodeStatus = newStatus)
                        ?: cn.mudlife.app.model.QzhqUseCodeData(randomCode = PrefsHelper.qzhqRandomCode, useCodeStatus = newStatus)
                    toastMessage = if (newStatus == 1) "洗澡码已开启" else "洗澡码已关闭"
                } else {
                    toastMessage = resp.message.ifEmpty { "操作失败" }
                }
            } catch (_: Exception) {
                toastMessage = "网络请求失败，请检查网络"
            }
        }
    }

    fun setQzhqUseCode(newCode5: String, onComplete: (Boolean, String) -> Unit) {
        val phone = PrefsHelper.qzhqPhone.ifEmpty { PrefsHelper.telephone }
        val loginCode = PrefsHelper.qzhqLoginCode
        if (phone.isEmpty() || loginCode.isEmpty()) {
            onComplete(false, "请先登录后勤专区")
            return
        }

        viewModelScope.launch {
            try {
                val resp = cn.mudlife.app.api.QzhqNetwork.setUseCode(newCode5, phone, loginCode)
                if (resp.isSuccess) {
                    PrefsHelper.qzhqCustomCode = newCode5
                    val codeResp = cn.mudlife.app.api.QzhqNetwork.getUseCode(phone, loginCode)
                    val random3 = if (codeResp.isSuccess && codeResp.data != null && !codeResp.data.randomCode.isNullOrEmpty()) {
                        codeResp.data.randomCode!!
                    } else {
                        PrefsHelper.qzhqRandomCode
                    }
                    val fullCode = newCode5 + random3
                    qzhqUseCodeData = cn.mudlife.app.model.QzhqUseCodeData(
                        randomCode = random3,
                        useCodeStatus = qzhqUseCodeData?.useCodeStatus ?: 1
                    )
                    PrefsHelper.qzhqRandomCode = random3
                    toastMessage = "洗澡码修改成功：$fullCode"
                    onComplete(true, "修改成功")
                } else {
                    val msg = resp.message.ifEmpty { "设置失败" }
                    toastMessage = msg
                    onComplete(false, msg)
                }
            } catch (e: Exception) {
                val msg = "网络连接异常，请重试"
                toastMessage = msg
                onComplete(false, msg)
            }
        }
    }

    fun loginQzhq(phone: String, passwordRaw: String, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val resp = cn.mudlife.app.api.QzhqNetwork.login(phone, passwordRaw)
                if (resp.isSuccess && resp.data != null) {
                    isQzhqLoggedIn = true
                    loadQzhqUseCode()
                    loadBills()
                    toastMessage = "后勤专区登录成功，已同步洗澡码"
                    onComplete(true, "登录成功")
                } else {
                    val msg = resp.message.ifEmpty { "登录失败，请核对手机号或密码" }
                    toastMessage = msg
                    onComplete(false, msg)
                }
            } catch (e: Exception) {
                val msg = "网络异常：${e.message ?: "连接失败"}"
                toastMessage = msg
                onComplete(false, msg)
            }
        }
    }

    fun smsLoginQzhq(phone: String, smsCode: String, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val resp = cn.mudlife.app.api.QzhqNetwork.smsLogin(phone, smsCode)
                if (resp.isSuccess && resp.data != null) {
                    isQzhqLoggedIn = true
                    loadQzhqUseCode()
                    loadBills()
                    toastMessage = "后勤专区登录成功，已同步洗澡码"
                    onComplete(true, "登录成功")
                } else {
                    val msg = resp.message.ifEmpty { "登录失败，请核对手机号或验证码" }
                    toastMessage = msg
                    onComplete(false, msg)
                }
            } catch (e: Exception) {
                val msg = "网络异常：${e.message ?: "连接失败"}"
                toastMessage = msg
                onComplete(false, msg)
            }
        }
    }

    fun sendQzhqSmsCode(phone: String, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val resp = cn.mudlife.app.api.QzhqNetwork.sendVerifyCode(phone)
                if (resp.isSuccess) {
                    toastMessage = "验证码已发送"
                    onComplete(true, "验证码已发送")
                } else {
                    val msg = resp.message.ifEmpty { "发送失败，请稍后重试" }
                    toastMessage = msg
                    onComplete(false, msg)
                }
            } catch (e: Exception) {
                val msg = "网络异常：${e.message ?: "连接失败"}"
                toastMessage = msg
                onComplete(false, msg)
            }
        }
    }

    fun pullRefresh() { 
        refreshWallet()
        loadBills()
        loadUseCode()
        loadQzhqUseCode()
        cleanInactiveOrders()
        if (activeOrders.isNotEmpty()) {
            activeOrders.forEach { order ->
                viewModelScope.launch { refreshDeviceStatus(order.snCode) }
            }
        }
    }

    // ── 设备发现 ──
    fun addDevice(device: NearbyDevice) {
        val idx = nearbyDevices.indexOfFirst { it.mac == device.mac }
        if (idx >= 0) {
            val e = nearbyDevices[idx]
            if (abs(e.rssi - device.rssi) > 5 || e.deviceInfo == null) {
                nearbyDevices[idx] = e.copy(rssi = device.rssi)
                if (e.deviceInfo == null && fetchingMacs.add(device.mac)) fetchInfo(device.mac)
            }
        } else {
            nearbyDevices.add(device); if (fetchingMacs.add(device.mac)) fetchInfo(device.mac)
        }
    }

    private fun fetchInfo(mac: String) {
        viewModelScope.launch {
            try {
                val resp = NetworkModule.apiService.getDeviceInfoSafe(mac)
                if (resp.success && resp.data != null) {
                    val info = resp.data; val i = nearbyDevices.indexOfFirst { it.mac == mac }
                    if (i >= 0) nearbyDevices[i] = nearbyDevices[i].copy(deviceInfo = info)
                    try {
                        val pid = info.projectId?.toString()
                        val q = NetworkModule.apiService.queryUsingSafe(snCode = info.snCode, auth = NetworkModule.authFields(overrideProjectId = pid))
                        if (q.errorCode == 307 || (q.success && q.data?.orderNo != null)) {
                            activeDeviceSnCodes.add(info.snCode)
                            val owner = q.data?.isOwner ?: true
                            if (owner && activeOrders.none { it.snCode == info.snCode }) {
                                activeOrders.add(ActiveOrder(info.snCode, q.data?.orderNo ?: "", info.displayName, info.macAddress, info.typeEmoji, info.withholdMoney, projectId = pid))
                                saveOrders()
                            }
                        }
                    } catch (_: Exception) {}
                } else checkKick(resp.displayMessage)
            } catch (e: Exception) { checkKickEx(e) }
            finally { fetchingMacs.remove(mac) }
        }
    }

    // ── 账单 ──
    fun loadBills() {
        viewModelScope.launch {
            if (billList.isEmpty()) {
                isLoadingBills = true
            }
            try {
                // 1. 获取当前用户绑定的全部项目与子账户（如直饮水 3255、洗浴吹风 4253、其他宿舍区等）
                val userProjectsResp = try {
                    NetworkModule.apiService.getUserProjectsSafe()
                } catch (_: Exception) { null }

                if (userProjectsResp?.success == true && !userProjectsResp.data.isNullOrEmpty()) {
                    val pMap = userProjectsResp.data.associate { it.projectId.toString() to it.accountId.toString() }
                    NetworkModule.setProjectAccounts(pMap)
                    PrefsHelper.saveProjectAccounts(pMap)
                }

                val targets = if (userProjectsResp?.success == true && !userProjectsResp.data.isNullOrEmpty()) {
                    userProjectsResp.data.map { it.projectId.toString() to it.accountId.toString() }.distinct()
                } else {
                    listOf(NetworkModule.currentProjectId to PrefsHelper.accountId)
                }

                val fmt = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.getDefault())

                // 2. 并发拉取近 6 个月在各个项目下的云端账单 (async + awaitAll)
                val cloudJobs = mutableListOf<kotlinx.coroutines.Deferred<List<BillItem>>>()
                for (monthOffset in 0..5) {
                    val cal = java.util.Calendar.getInstance()
                    cal.add(java.util.Calendar.MONTH, -monthOffset)
                    val month = fmt.format(cal.time)

                    for ((pid, aid) in targets) {
                        cloudJobs.add(async(Dispatchers.IO) {
                            try {
                                val resp = NetworkModule.apiService.getBillListSafe(
                                    month = month,
                                    billRequestType = 0,
                                    projectId = pid,
                                    accountId = aid
                                )
                                if (resp.success && !resp.data.isNullOrEmpty()) {
                                    resp.data.filter { it.billType != 1 }
                                } else emptyList()
                            } catch (e: Exception) {
                                cn.mudlife.app.utils.AppLogger.w("BillLoad", "查询项目 $pid ($month) 异常: ${e.message}")
                                emptyList()
                            }
                        })
                    }
                }

                // 2.5 并发拉取江大后勤专区热水洗浴全量账单
                val qzhqJob = async(Dispatchers.IO) {
                    val qzhqPhone = PrefsHelper.qzhqPhone.ifEmpty { PrefsHelper.telephone }
                    val qzhqLoginCode = PrefsHelper.qzhqLoginCode
                    if (qzhqPhone.isNotEmpty() && qzhqLoginCode.isNotEmpty()) {
                        try {
                            val qzhqResp = cn.mudlife.app.api.QzhqNetwork.getWalletBillList(phone = qzhqPhone, loginCode = qzhqLoginCode)
                            if (qzhqResp.isSuccess && !qzhqResp.data.isNullOrEmpty()) {
                                cn.mudlife.app.utils.AppLogger.i("BillLoad", "成功获取后勤洗浴账单共 ${qzhqResp.data.size} 笔")
                                
                                // 同步最新校园卡余额（仅当确实存在严格大于 0 的有效结存流水时才同步，彻底拦截 -5 等状态码）
                                val latestWithBalance = qzhqResp.data.firstOrNull { (it.afterMoney ?: 0.0) > 0.0 }
                                latestWithBalance?.afterMoney?.let { bal ->
                                    val balStr = String.format(java.util.Locale.US, "%.2f", bal)
                                    campusCardBalance = balStr
                                    campusCardBalanceTime = System.currentTimeMillis()
                                    PrefsHelper.campusCardBalance = balStr
                                    PrefsHelper.campusCardBalanceTime = campusCardBalanceTime
                                    cn.mudlife.app.utils.AppLogger.i("BillLoad", "已同步最新洗浴结存余额: ¥$balStr")
                                }

                                qzhqResp.data.mapNotNull { qb ->
                                    val date = qb.dealDate ?: return@mapNotNull null
                                    val xfVal = if ((qb.xfMoney ?: 0.0) > 0.0) qb.xfMoney ?: 0.0 else (qb.upMoney ?: 0.0) + (qb.upLeadMoney ?: 0.0)
                                    val money = String.format(java.util.Locale.US, "%.2f", xfVal)
                                    val afterVal = qb.afterMoney
                                    val beforeVal = qb.beforeMoney ?: if (afterVal != null && afterVal > 0.0 && xfVal > 0.0) afterVal + xfVal else null
                                    val op = qb.opName ?: qb.consumeType ?: ""
                                    val devTypeName = when {
                                        op.contains("开水") || op.contains("饮水") -> "开水器"
                                        op.contains("吹风") -> "共享吹风机"
                                        else -> "宿舍浴室热水器"
                                    }
                                    val desc = qb.description ?: if (!qb.areaName.isNullOrEmpty()) "$devTypeName: ${qb.areaName}" else devTypeName
                                    val dto = BillDTO(
                                        orderId = qb.time ?: "${date}_${qb.accountId}",
                                        dealDate = date,
                                        consumeDate = date,
                                        consumeMoney = money,
                                        deviceTypeName = devTypeName,
                                        description = desc,
                                        deductTypeName = "校园卡扣费",
                                        beforeMoney = if (beforeVal != null && beforeVal > 0.0) String.format(java.util.Locale.US, "%.2f", beforeVal) else null,
                                        afterMoney = if (afterVal != null && afterVal > 0.0) String.format(java.util.Locale.US, "%.2f", afterVal) else null
                                    )
                                    BillItem(
                                        billType = 2,
                                        consumeBillDTO = dto
                                    )
                                }
                            } else {
                                emptyList()
                            }
                        } catch (e: Exception) {
                            emptyList()
                        }
                    } else emptyList()
                }

                // 汇总所有并发结果
                val all = mutableListOf<BillItem>()
                val cloudResults = cloudJobs.awaitAll()
                val qzhqBills = qzhqJob.await()
                cloudResults.forEach { all.addAll(it) }
                all.addAll(qzhqBills)

                // 3. 全局去重（按订单号与消费时间组合）并按时间倒序排列
                billList = all.distinctBy {
                    val dto = it.safeConsumeBillDTO
                    "${dto.safeOrderId}_${dto.safeConsumeDate}_${dto.safeConsumeMoney}"
                }.sortedByDescending { it.safeConsumeBillDTO.safeConsumeDate }

                cn.mudlife.app.utils.AppLogger.i("BillLoad", "成功汇总全区全品类账单共 ${billList.size} 笔 (包含后勤洗浴 ${qzhqBills.size} 笔)")
            } catch (e: Exception) { 
                cn.mudlife.app.utils.AppLogger.e("BillLoad", "加载账单异常: ${e.message}", e)
                checkKickEx(e)
                val msg = e.message ?: ""
                if (msg.contains("Unable to resolve host", ignoreCase = true) ||
                    msg.contains("No address associated", ignoreCase = true) ||
                    msg.contains("Network is unreachable", ignoreCase = true) ||
                    msg.contains("Failed to connect", ignoreCase = true)) {
                    toastMessage = "网络连接失败，请检查网络设置"
                }
            }
            finally { isLoadingBills = false }
        }
    }
}
