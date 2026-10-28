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
import cn.mudlife.app.api.setUseCodeSafe
import cn.mudlife.app.api.updateUseCodeStatusSafe
import cn.mudlife.app.api.generateUseCodeSafe
import cn.mudlife.app.api.getWalletSafe
import cn.mudlife.app.api.queryUsingSafe
import cn.mudlife.app.model.ActiveOrder
import cn.mudlife.app.model.UseCodeData
import cn.mudlife.app.model.DeviceInfo
import cn.mudlife.app.model.MqttOrderMsg
import cn.mudlife.app.model.WalletData
import cn.mudlife.app.utils.MqttManager
import cn.mudlife.app.utils.PrefsHelper
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import cn.mudlife.app.utils.AccountRequests
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlin.math.abs

class MainViewModel(
    private val queryOrderStatus: suspend (ActiveOrder) -> cn.mudlife.app.model.BaseResponse<cn.mudlife.app.model.OrderStatus> = { order ->
        NetworkModule.apiService.queryUsingSafe(snCode = order.snCode,
            auth = NetworkModule.authFields(overrideProjectId = order.projectId))
    }
) : ViewModel() {

    var walletInfo by mutableStateOf<WalletData?>(null)

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
    val unverifiedOrders = mutableStateListOf<ActiveOrder>()
    var isVerifyingOrders by mutableStateOf(false)
        private set
    private var managersInitialized = false
    private var orderVerificationJob: Job? = null
    private var accountGeneration = 0L
    private val lifeRequests = AccountRequests(viewModelScope) { listOf(PrefsHelper.loginCode, PrefsHelper.userId) }
    private val bathRequests = AccountRequests(viewModelScope) { listOf(PrefsHelper.qzhqLoginCode, PrefsHelper.qzhqPhone.ifEmpty { PrefsHelper.telephone }) }
    private var bathLoginJob: Job? = null
    private var refreshJob: Job? = null
    private var refreshGeneration = 0L
    var isRefreshing by mutableStateOf(false); private set
    private var activeDeviceSnCodes = mutableSetOf<String>()
    private val gson = Gson()
    private var mqttManager: MqttManager? = null
    private var timerJob: Job? = null
    private var orderPollJob: Job? = null
    private var probeJob: Job? = null

    var useCodeData by mutableStateOf<UseCodeData?>(
        if (PrefsHelper.useCode.isNotEmpty()) {
            UseCodeData(
                useCode = PrefsHelper.useCode,
                useCodeStatus = if (PrefsHelper.useCodeStatus) 1 else 0
            )
        } else null
    )
    // ── 吹风机使用码 20 次抽选架构 ──
    var candidateUseCode by mutableStateOf("")
    var remainUseCodeTimes by mutableStateOf(20)
    var isRollingUseCode by mutableStateOf(false)
    var showDryerCodeModal by mutableStateOf(false)

    // ── 国内免翻墙检查更新架构 ──
    var isCheckingUpdate by mutableStateOf(false)
    var updateInfo by mutableStateOf<cn.mudlife.app.utils.UpdateInfo?>(null)
    var showUpdateDialog by mutableStateOf(false)
    var isDownloadingUpdate by mutableStateOf(false)
    var updateDownload by mutableStateOf(cn.mudlife.app.utils.UpdateDownloadProgress())
        private set
    var updateError by mutableStateOf<String?>(null); private set

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
    var qzhqPrefix by mutableStateOf(PrefsHelper.qzhqCustomCode)
        private set

    fun rememberQzhqPrefix(value: String) {
        if (!cn.mudlife.app.model.BathCode.isPrefix(value)) return
        PrefsHelper.qzhqCustomCode = value
        qzhqPrefix = PrefsHelper.qzhqCustomCode
        toastMessage = if (qzhqPrefix == value) "已记住前五位，关闭应用后仍保留" else "请先登录后勤账号后再保存"
    }

    fun initManagers(context: Context) {
        if (managersInitialized) return
        managersInitialized = true
        syncProjectAccounts()
        if (mqttManager == null) mqttManager = MqttManager(context, { handleMqttMessage(it) })

        lastDeviceName = PrefsHelper.lastDeviceName
        lastDeviceMac = PrefsHelper.lastDeviceMac
        lastDeviceSnCode = PrefsHelper.lastDeviceSnCode
        lastDeviceEmoji = PrefsHelper.lastDeviceEmoji

        // Cached orders stay unverified until the server confirms their current state.
        val saved = PrefsHelper.getActiveOrders()
        activeOrders.clear()
        unverifiedOrders.clear()
        unverifiedOrders.addAll(saved)
        recentDevices = PrefsHelper.getRecentDevices()

        if (saved.isNotEmpty()) {
            cleanInactiveOrders()
        }
    }

    fun cleanInactiveOrders() {
        if (orderVerificationJob?.isActive == true) return
        val generation = accountGeneration
        orderVerificationJob = viewModelScope.launch {
            isVerifyingOrders = true
            val current = (activeOrders.toList() + unverifiedOrders.toList()).distinctBy { it.snCode }
            try {
                current.forEach { order ->
                try {
                    val q = queryOrderStatus(order)
                    // Ignore results from a previous login or an order replaced while awaiting HTTP.
                    if (generation != accountGeneration) return@launch
                    if (order !in activeOrders && order !in unverifiedOrders) return@forEach
                    when (cn.mudlife.app.model.verifyOrder(q)) {
                        cn.mudlife.app.model.OrderVerification.ACTIVE -> {
                            unverifiedOrders.remove(order)
                            if (activeOrders.none { it.snCode == order.snCode }) {
                                activeOrders.add(order.copy(orderNo = q.data?.orderNo ?: order.orderNo))
                            }
                            activeDeviceSnCodes.add(order.snCode)
                            saveOrders()
                        }
                        cn.mudlife.app.model.OrderVerification.ENDED -> clearActiveRecord(order.snCode)
                        cn.mudlife.app.model.OrderVerification.UNKNOWN -> Unit
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {}
                }
            } finally {
                if (generation == accountGeneration) isVerifyingOrders = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        orderVerificationJob?.cancel()
        timerJob?.cancel(); orderPollJob?.cancel(); probeJob?.cancel(); probeJob?.cancel()
        mqttManager?.disconnect()
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
        val owner = PrefsHelper.loginCode
        val generation = accountGeneration
        try {
            val pid = overrideProjectId ?: selectedDevice?.projectId?.toString()?.takeIf { it.isNotBlank() && it != "0" && it != "null" }
            val q = NetworkModule.apiService.queryUsingSafe(snCode = snCode, auth = NetworkModule.authFields(overrideProjectId = pid))
            if (owner != PrefsHelper.loginCode || generation != accountGeneration) return
            if (q.errorCode == 307 || (q.success && q.data?.orderNo != null)) {
                activeDeviceSnCodes.add(snCode)
                isOwner = q.data?.isOwner ?: true
                // 只有自己的订单才加入 activeOrders
                if (isOwner && activeOrders.none { it.snCode == snCode }) {
                    val orderNo = q.data?.orderNo ?: ""
                    val deviceInfo = selectedDevice
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
            waterType = waterType,
            projectId = device.projectId?.toString()
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
            }
        }

        // 0.2 秒极速探活协程：实时检查饮水机是否已被机身按键关闭（200ms）
        probeJob?.cancel()
        probeJob = viewModelScope.launch(Dispatchers.IO) {
            val overridePid = device.projectId?.toString() ?: selectedDevice?.projectId?.toString()
            while (isShowering) {
                delay(200L)
                try {
                    val q = NetworkModule.apiService.queryUsingSafe(snCode = snCode, auth = NetworkModule.authFields(overrideProjectId = overridePid))
                    if (q.success && q.data?.orderNo == null && q.errorCode != 307) {
                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                            onAutoClose(snCode)
                        }
                        return@launch
                    }
                    if (currentOrderNo == null && q.success && !q.data?.orderNo.isNullOrEmpty()) {
                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                            val oNo = q.data!!.orderNo!!
                            currentOrderNo = oNo
                            updateOrderNo(snCode, oNo)
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun handleMqttMessage(message: String) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            try {
                val msg = gson.fromJson(message, MqttOrderMsg::class.java)
                msg.orderNo?.let { orderNo -> if (currentOrderNo == null) { currentOrderNo = orderNo; showerSnCode?.let { sn -> updateOrderNo(sn, orderNo) } } }
                msg.consumeMoney?.let {
                    showerConsumed = it
                    showerRemaining = "%.2f".format(if (showerPreDeduct - it < 0) 0.0 else showerPreDeduct - it)
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * 设备自动关闭：停止计时，查询消费金额，弹出确认框。
     * 用户点确认后才真正退出（finishShower）。
     */
    private fun onAutoClose(snCode: String) {
        if (!isShowering) return
        // 停止计时（弹窗期间洗澡界面不再走秒）
        timerJob?.cancel(); orderPollJob?.cancel(); probeJob?.cancel()

        autoCloseDeviceName = selectedDevice?.displayName ?: lastDeviceName.ifEmpty { "热水器" }
        autoCloseElapsed = showerElapsedSec
        autoCloseConsumed = 0.0
        autoCloseLoading = false
        showAutoCloseDialog = true

        val startTime = PrefsHelper.getStartedAt(snCode)
        val closingOrderNo = currentOrderNo ?: activeOrders.find { it.snCode == snCode }?.orderNo.orEmpty()
        // The device is already stopped. Dismissing the receipt must not own cleanup.
        clearActiveRecord(snCode)
        // 立即清除持久化的开阀时间戳与倒计时，防止用户不点弹窗确认直接退出时残留脏时间戳
        PrefsHelper.setStartedAt(snCode, 0L)
        PrefsHelper.clearAutoDiscon(snCode)

        // 后台异步静默等账单结算，若拿到金额则更新弹窗显示，不阻塞用户点击确认退出
        viewModelScope.launch {
            val orderNo = closingOrderNo
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

                // 兜底探活：若 5 次轮询仍未返回关阀状态，调用 queryUsingSafe 核验水机是否已停
                if (!closedOk) {
                    try {
                        val q = NetworkModule.apiService.queryUsingSafe(snCode = snCode, auth = devAuth)
                        if (q.success && q.data?.orderNo == null && q.errorCode != 307) {
                            closedOk = true
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
                        val devTitle = if (isWater) "饮水机" else "出水设备"
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
                    showerError = "关水未确认，请检查水机或重试"
                    toastMessage = "关水未确认成功，请再次点击或检查水机"
                    cn.mudlife.app.utils.AppLogger.e("Shower", "关水未确认: 5次轮询与兜底queryUsing均未确认关闭 | sn: $snCode, 订单: $orderNo")
                    isStopping = false
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
        clearActiveRecord(snCode)

        if (consumed != null) {
            toastMessage = "已停止，本次消费 ¥%.2f".format(consumed)
        } else {
            toastMessage = "饮水机已关闭"
        }

        currentOrderNo = null; showerConsumed = 0.0; showerPreDeduct = 0.0
        showerRemaining = "0.00"; showerElapsedSec = 0
        autoDisConSec = 0
        PrefsHelper.setStartedAt(snCode, 0L) // 重置该设备计时器
        PrefsHelper.clearAutoDiscon(snCode)  // 清除自动关停倒计时
        showerSnCode = null; timerJob?.cancel(); orderPollJob?.cancel(); probeJob?.cancel(); probeJob?.cancel()
        try { mqttManager?.disconnect() } catch (_: Exception) {}
    }

    fun logout() {
        accountGeneration++
        lifeRequests.invalidate()
        refreshGeneration++; refreshJob?.cancel(); isRefreshing = false
        probeJob?.cancel()
        candidateUseCode = ""; showDryerCodeModal = false; isRollingUseCode = false; remainUseCodeTimes = 20
        orderVerificationJob?.cancel()
        isVerifyingOrders = false
        managersInitialized = false
        unverifiedOrders.clear()
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
        recentDevices = emptyList()
        activeOrders.clear()
        activeDeviceSnCodes.clear()
        useCodeData = null
        walletInfo = null
    }

    private fun clearActiveRecord(snCode: String) {
        activeOrders.removeAll { it.snCode == snCode }
        unverifiedOrders.removeAll { it.snCode == snCode }
        activeDeviceSnCodes.remove(snCode)
        PrefsHelper.setStartedAt(snCode, 0L)
        PrefsHelper.clearAutoDiscon(snCode)
        saveOrders()
    }

    private fun saveOrders() {
        val confirmedDevices = activeOrders.map { it.snCode }.toSet()
        unverifiedOrders.removeAll { it.snCode in confirmedDevices }
        PrefsHelper.saveActiveOrders((activeOrders.toList() + unverifiedOrders.toList()).distinctBy { it.snCode })
    }

    fun isDeviceActive(snCode: String) = snCode in activeDeviceSnCodes



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
            PrefsHelper.clearLifeAccount()
        }
    }
    private fun checkKickEx(e: Exception) {
        val m = e.message ?: return
        if (m.contains("401") || m.contains("403") || m.contains("Unauthorized") || m.contains("Forbidden")) {
            cn.mudlife.app.utils.AppLogger.e("Auth", "触发网络鉴权失效下线: $m")
            kickedOut = true
            PrefsHelper.clearLifeAccount()
        }
    }

    fun lifeAccountAuthenticated() {
        lifeRequests.invalidate()
        walletInfo = null; useCodeData = null
        candidateUseCode = ""; showDryerCodeModal = false; isRollingUseCode = false
        refreshWallet(); loadUseCode()
    }

    fun refreshWallet(): Job {
        return lifeRequests.launch {
            try {
                val resp = request { NetworkModule.apiService.getWalletSafe() }
                if (resp.success) walletInfo = resp.data else checkKick(resp.displayMessage)
            } catch (cancelled: CancellationException) { throw cancelled } catch (e: Exception) {
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

    fun loadUseCode(): Job {
        return lifeRequests.launch {
            try {
                val resp = request { NetworkModule.apiService.getUseCodeSafe() }
                if (resp.success && resp.data != null) {
                    val remoteCode = resp.data.useCode
                    // 服务端是使用码的唯一真实来源。旧版本曾用本地保护标记
                    // 覆盖服务端返回值，会把“看起来成功”的旧码一直留在界面上。
                    useCodeData = resp.data
                    if (remoteCode.isNotEmpty()) {
                        PrefsHelper.useCode = remoteCode
                        PrefsHelper.useCodeStatus = resp.data.useCodeStatus == 1
                    }
                    // 清理旧版本用于掩盖同步失败的本地保护标记。
                    if (PrefsHelper.useCodeLastConfirmedAt != 0L) {
                        PrefsHelper.useCodeLastConfirmedAt = 0L
                    }
                    if (resp.data.remainTimes in 0..20) {
                        remainUseCodeTimes = resp.data.remainTimes
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) {}
        }
    }

    fun toggleUseCodeStatus(enabled: Boolean) {
        lifeRequests.launch {
            try {
                val newStatus = if (enabled) 1 else 0
                val resp = request { NetworkModule.apiService.updateUseCodeStatusSafe(newStatus, NetworkModule.authFields()) }
                if (resp.success) {
                    useCodeData = useCodeData?.copy(useCodeStatus = newStatus)
                        ?: UseCodeData(useCode = PrefsHelper.useCode, useCodeStatus = newStatus)
                    PrefsHelper.useCodeStatus = enabled
                    toastMessage = if (enabled) "吹风机使用码已开启" else "吹风机使用码已关闭"
                } else {
                    toastMessage = resp.displayMessage.orEmpty().ifEmpty { "操作失败" }
                }
            } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) {
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
        lifeRequests.launch {
            isRollingUseCode = true
            try {
                val resp = request { NetworkModule.apiService.generateUseCodeSafe(NetworkModule.authFields()) }
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
            } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) {
                toastMessage = "网络请求失败，请检查网络"
            } finally {
                if (isCurrent) isRollingUseCode = false
            }
        }
    }

    fun confirmUseCandidateCode() {
        val codeToUse = candidateUseCode.trim().ifEmpty { return }
        lifeRequests.launch {
            try {
                // generate 只生成候选码；必须调用 set 才会把候选码写入当前账户。
                val auth = NetworkModule.authFields()
                val setResp = request { NetworkModule.apiService.setUseCodeSafe(
                    useCode = codeToUse,
                    auth = auth
                ) }
                if (!setResp.success) {
                    toastMessage = setResp.displayMessage.orEmpty().ifEmpty { "设置新码失败" }
                    return@launch
                }

                // “确认使用”同时保证使用码处于开启状态。
                val statusResp = request { NetworkModule.apiService.updateUseCodeStatusSafe(1, auth) }
                if (!statusResp.success) {
                    toastMessage = statusResp.displayMessage.orEmpty().ifEmpty { "新码已设置，但启用失败" }
                    return@launch
                }

                // 回读服务端，只有当前生效码与候选码一致时才更新本地界面。
                var confirmedData: UseCodeData? = null
                for (attempt in 0 until 3) {
                    val latestResp = request { NetworkModule.apiService.getUseCodeSafe() }
                    if (latestResp.success && latestResp.data?.useCode == codeToUse) {
                        confirmedData = latestResp.data
                        break
                    }
                    if (attempt < 2) delay(400L * (attempt + 1))
                }
                val serverData = confirmedData
                if (serverData == null) {
                    cn.mudlife.app.utils.AppLogger.w(
                        "UseCode",
                        "服务端未回读到新使用码，保留候选码等待重试"
                    )
                    toastMessage = "服务器尚未同步新码，请稍后重试"
                    return@launch
                }

                // 只有服务端回读到候选码后，才更新本地显示并关闭弹窗。
                useCodeData = serverData
                PrefsHelper.useCode = codeToUse
                PrefsHelper.useCodeStatus = serverData.useCodeStatus == 1
                PrefsHelper.useCodeLastConfirmedAt = 0L
                if (serverData.remainTimes in 0..20) {
                    remainUseCodeTimes = serverData.remainTimes
                }
                showDryerCodeModal = false
                candidateUseCode = ""
                toastMessage = "已成功更换并激活吹风机码"
            } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) {
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
                        toastMessage = if (info != null && info.versionCode < cn.mudlife.app.BuildConfig.VERSION_CODE) "当前安装版高于已发布版本" else "当前已是最新版本"
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
            updateDownload = cn.mudlife.app.utils.UpdateDownloadProgress()
            updateError = null
            try {
                val result = cn.mudlife.app.utils.UpdateManager.downloadApk(context, info) { progress ->
                    updateDownload = progress
                }
                if (result.isSuccess) {
                    val file = result.getOrNull()
                    if (file != null) {
                        cn.mudlife.app.utils.UpdateManager.installApk(context, file).fold(
                            onSuccess = { showUpdateDialog = false },
                            onFailure = { updateError = it.message ?: "无法打开安装器，请稍后重试" }
                        )
                    } else {
                        updateError = "安装包解析异常"
                    }
                } else {
                    updateError = result.exceptionOrNull()?.message ?: "安装包下载失败，请稍后重试"
                }
            } catch (_: Exception) {
                updateError = "下载更新异常，请稍后重试"
            } finally {
                isDownloadingUpdate = false
            }
        }
    }

    // ── 后勤洗浴使用码控制 (江大专区) ──
    fun logoutQzhq() {
        bathRequests.invalidate(); bathLoginJob?.cancel(); qzhqLoading = false
        PrefsHelper.qzhqLoginCode = ""
        PrefsHelper.qzhqRandomCode = ""
        isQzhqLoggedIn = false
        qzhqUseCodeData = null
        qzhqPrefix = ""
    }

    fun loadQzhqUseCode(): Job {
        qzhqPrefix = PrefsHelper.qzhqCustomCode
        val phone = PrefsHelper.qzhqPhone.ifEmpty { PrefsHelper.telephone }
        val loginCode = PrefsHelper.qzhqLoginCode

        return bathRequests.launch {
            qzhqLoading = true
            try {
                val resp = request { cn.mudlife.app.api.QzhqNetwork.getUseCode(phone, loginCode) }
                if (PrefsHelper.qzhqLoginCode != loginCode) return@launch
                if (resp.isSuccess && resp.data != null) {
                    saveBathCode(resp.data)
                    qzhqUseCodeData = resp.data
                    qzhqPrefix = PrefsHelper.qzhqCustomCode
                } else if (resp.errorCode == 8 || resp.errorCode == 401) {
                    qzhqLoading = false
                    isQzhqLoggedIn = false
                    PrefsHelper.qzhqLoginCode = ""
                    toastMessage = "后勤账号登录失效，请重新登录"
                }
            } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) {
            } finally {
                if (isCurrent) qzhqLoading = false
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

        bathRequests.launch {
            try {
                val resp = request { cn.mudlife.app.api.QzhqNetwork.updateUseCodeStatus(newStatus, phone, loginCode) }
                if (resp.isSuccess) {
                    PrefsHelper.qzhqStatus = newStatus
                    qzhqUseCodeData = qzhqUseCodeData?.copy(useCodeStatus = newStatus)
                        ?: cn.mudlife.app.model.QzhqUseCodeData(randomCode = PrefsHelper.qzhqRandomCode, useCodeStatus = newStatus)
                    toastMessage = if (newStatus == 1) "洗澡码已开启" else "洗澡码已关闭"
                } else {
                    toastMessage = resp.message.ifEmpty { "操作失败" }
                }
            } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) {
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

        bathRequests.launch {
            try {
                val resp = request { cn.mudlife.app.api.QzhqNetwork.setUseCode(newCode5, phone, loginCode) }
                if (resp.isSuccess) {
                    PrefsHelper.qzhqCustomCode = newCode5
                    qzhqPrefix = newCode5
                    val codeResp = request { cn.mudlife.app.api.QzhqNetwork.getUseCode(phone, loginCode) }
                    if (codeResp.isSuccess && codeResp.data != null) saveBathCode(codeResp.data)
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
            } catch (cancelled: CancellationException) { throw cancelled } catch (e: Exception) {
                val msg = "网络连接异常，请重试"
                toastMessage = msg
                onComplete(false, msg)
            }
        }
    }

    fun loginQzhq(phone: String, passwordRaw: String, onComplete: (Boolean, String) -> Unit) {
        bathLoginJob?.cancel()
        bathLoginJob = viewModelScope.launch {
            try {
                val resp = cn.mudlife.app.api.QzhqNetwork.login(phone, passwordRaw)
                if (resp.isSuccess && resp.data != null) {
                    bathRequests.invalidate()
                    isQzhqLoggedIn = true
                    loadQzhqUseCode()
                    toastMessage = "后勤专区登录成功，已同步洗澡码"
                    onComplete(true, "登录成功")
                } else {
                    val msg = resp.message.ifEmpty { "登录失败，请核对手机号或密码" }
                    toastMessage = msg
                    onComplete(false, msg)
                }
            } catch (cancelled: CancellationException) { throw cancelled } catch (e: Exception) {
                val msg = "网络异常：${e.message ?: "连接失败"}"
                toastMessage = msg
                onComplete(false, msg)
            }
        }
    }

    fun smsLoginQzhq(phone: String, smsCode: String, onComplete: (Boolean, String) -> Unit) {
        bathLoginJob?.cancel()
        bathLoginJob = viewModelScope.launch {
            try {
                val resp = cn.mudlife.app.api.QzhqNetwork.smsLogin(phone, smsCode)
                if (resp.isSuccess && resp.data != null) {
                    bathRequests.invalidate()
                    isQzhqLoggedIn = true
                    loadQzhqUseCode()
                    toastMessage = "后勤专区登录成功，已同步洗澡码"
                    onComplete(true, "登录成功")
                } else {
                    val msg = resp.message.ifEmpty { "登录失败，请核对手机号或验证码" }
                    toastMessage = msg
                    onComplete(false, msg)
                }
            } catch (cancelled: CancellationException) { throw cancelled } catch (e: Exception) {
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
        if (refreshJob?.isActive == true) return
        val generation = ++refreshGeneration
        isRefreshing = true
        refreshJob = viewModelScope.launch {
            try {
                val work = mutableListOf(syncProjectAccounts(), refreshWallet(), loadUseCode(), loadQzhqUseCode())
                cleanInactiveOrders()
                orderVerificationJob?.let(work::add)
                activeOrders.toList().forEach { order ->
                    work += launch { refreshDeviceStatus(order.snCode) }
                }
                work.joinAll()
            } finally { if (generation == refreshGeneration) isRefreshing = false }
        }
    }

    private fun saveBathCode(data: cn.mudlife.app.model.QzhqUseCodeData) {
        data.randomCode?.let { PrefsHelper.qzhqRandomCode = it }
        data.randomCode?.takeIf { it.matches(Regex("[0-9]{8}")) }?.let { PrefsHelper.qzhqCustomCode = it.take(5) }
        PrefsHelper.qzhqStatus = data.useCodeStatus
    }

    private fun syncProjectAccounts(): Job = lifeRequests.launch {
        try {
            val response = request { NetworkModule.apiService.getUserProjectsSafe() }
            if (response.success && !response.data.isNullOrEmpty()) {
                val accounts = response.data.associate { it.projectId.toString() to it.accountId.toString() }
                NetworkModule.setProjectAccounts(accounts)
                PrefsHelper.saveProjectAccounts(accounts)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
          catch (_: Exception) { /* Keep the existing account mapping on a transient error. */ }
    }
}
