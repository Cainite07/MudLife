package cn.mudlife.campuscard

import cn.mudlife.app.BuildConfig

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.WindowManager
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import org.json.JSONObject
import org.json.JSONTokener
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Authentication is the only school page shown. Wallet, code and amount UI are native.
 * Official payment scripts remain the transaction transport; their keys are not copied. */
class SchoolActivity : ComponentActivity() {
    private lateinit var session: SessionStore
    private lateinit var browser: WebView
    private val handler = Handler(Looper.getMainLooper())
    private var stage by mutableStateOf("正在连接学校认证…")
    private var failure by mutableStateOf(false)
    private var payment = false
    private var creationStarted = false
    private var payStarted = false
    private var handedToWechat = false
    private var paymentConfirmed = false
    private var reconciling by mutableStateOf(false)
    private var reconciliationJob: Job? = null
    private var authFinished = false
    private var checks = 0
    private var referer = SchoolApi.ORIGIN + "/"
    private var active = false
    private var fen = 0L
    private var account = ""
    private var target = ""
    private var electric = false
    private var electricDorm: Dorm? = null
    private var quantity = ""

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        session = SessionStore(this)
        electric = intent.getStringExtra("mode") == "electric"
        payment = electric || intent.getStringExtra("mode") == "payment"
        electricDorm = runCatching { Dorm.parse(JSONObject(intent.getStringExtra("dorm").orEmpty())) }.getOrNull()
        quantity = intent.getStringExtra("quantity").orEmpty()
        fen = intent.getLongExtra("fen", 0)
        if (fen <= 0 && session.pendingCheckout.isNotBlank()) fen = session.pendingFen
        account = intent.getStringExtra("account").orEmpty()
        target = intent.getStringExtra("target").orEmpty()
        creationStarted = state?.getBoolean("creationStarted") ?: false
        payStarted = state?.getBoolean("payStarted") ?: false
        handedToWechat = state?.getBoolean("handedToWechat") ?: false
        paymentConfirmed = state?.getBoolean("paymentConfirmed") ?: false
        browser = createBrowser()
        setContent { CampusTheme {
            androidx.activity.compose.BackHandler { close() }
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().systemBarsPadding()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                        TextButton(onClick = ::close) { Text("返回") }
                        Text(if (electric) "电费充值" else if (payment) "校园卡充值" else "学校认证", fontFamily = FontFamily.Serif, fontSize = 19.sp, modifier = Modifier.weight(1f).padding(top = 11.dp))
                    }
                    if (!payment) {
                        Text("认证成功后会安全记住登录", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 24.dp, bottom = 12.dp))
                        if (failure) { Text(stage, Modifier.padding(24.dp)); TextButton(onClick = { failure = false; browser.loadUrl(loginUrl()) }) { Text("重新连接") } }
                        else AndroidView(factory = { browser }, modifier = Modifier.fillMaxWidth().weight(1f))
                    } else {
                        AndroidView(factory = { browser }, modifier = Modifier.size(1.dp).alpha(0f))
                        Column(Modifier.fillMaxWidth().padding(24.dp)) {
                            if (fen > 0) Text("¥ ${Money.yuan(fen)}", fontSize = 32.sp, modifier = Modifier.padding(top = 30.dp, bottom = 20.dp))
                            Text(stage, fontSize = 15.sp, lineHeight = 25.sp)
                            if (!failure && (!handedToWechat || reconciling)) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 22.dp))
                            if (failure || handedToWechat) Button(onClick = ::close, modifier = Modifier.padding(top = 24.dp)) { Text("返回卡包核对余额") }
                            if (handedToWechat && !paymentConfirmed && !reconciling) TextButton(onClick = ::reconcilePayment) { Text("再次核对支付结果") }
                            Text("跳转不代表到账，最终付款由你在微信中确认。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 21.sp, modifier = Modifier.padding(top = 24.dp))
                        }
                    }
                }
            }
        } }
        if (!payment) browser.loadUrl(loginUrl())
        else when {
            paymentConfirmed -> { stage = PaymentPolicy.message(PaymentState.PAID); handedToWechat = true }
            electric && session.pendingKind == "electric" && session.pendingOrderId.isNotBlank() -> { creationStarted = true; prepareElectric() }
            Navigation.checkout(session.pendingCheckout) -> { creationStarted = true; browser.loadUrl(session.pendingCheckout) }
            session.pendingCheckout.isNotBlank() || creationStarted -> fail("上次充值的状态尚不确定，已停止重复创建订单。请先核对学校充值记录。")
            electric && electricDorm != null && fen > 0 && session.hasSession && quantity.matches(Regex("[0-9]{1,5}(\\.[0-9]{1,2})?")) -> prepareElectric()
            fen <= 0 || account.isBlank() || !session.hasSession -> fail("充值信息不完整，请返回卡包重新确认")
            else -> prepareRecharge()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createBrowser() = WebView(this).apply {
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.setSupportMultipleWindows(false)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
        webChromeClient = object : WebChromeClient() {
            // Official payment scripts log request objects; do not send them to logcat.
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?) = true
            override fun onPermissionRequest(request: PermissionRequest) = request.deny()
        }
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (payment && Navigation.wechat(url)) { openWechat(url); return true }
                if (!Navigation.web(url)) { if (request.isForMainFrame) fail("学校跳转到了尚未支持的服务，请返回后重试"); return true }
                if (payment && request.isForMainFrame && request.url.host == "wx.tenpay.com") {
                    view.loadUrl(url, mapOf("Referer" to referer)); return true
                }
                return false
            }
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                if (payment && Navigation.wechat(url)) { view.stopLoading(); openWechat(url!!); return }
                if (!Navigation.web(url)) { view.stopLoading(); fail("学校页面暂时无法打开"); return }
                val uri = Uri.parse(url)
                if (uri.host in setOf("ykt.ujs.edu.cn", "ecardpay.ujs.edu.cn")) referer = "https://${uri.host}/"
                if (payment && Navigation.checkout(url)) { session.pendingCheckout = url!!; session.pendingOrderId = Navigation.checkoutReference(url)?.orderId.orEmpty(); creationStarted = true; checks = 0 }
                handler.removeCallbacksAndMessages(null)
            }
            override fun onPageFinished(view: WebView, url: String?) {
                if (failure || isFinishing || isDestroyed || !Navigation.web(url)) return
                val u = Uri.parse(url)
                if (!payment && u.host == "ykt.ujs.edu.cn" && u.path?.startsWith("/plat/") == true && u.path != "/plat/login") collectSession()
                else if (payment && u.host == "ykt.ujs.edu.cn" && u.path?.startsWith("/campus-card/cardRecharge") == true) submitWhenReady()
                else if (payment && Navigation.checkout(url)) payWhenReady()
                else if (electric && u.host == "ykt.ujs.edu.cn" && u.path?.startsWith("/charge-app") == true) electricTick()
                else if (payment && u.host == "pass.ujs.edu.cn") fail("学校要求更新认证，请返回账户页更新登录后再继续")
            }
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) { handler.cancel(); fail("学校的安全连接未能确认，请稍后重试") }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) { if (request.isForMainFrame) fail("学校连接暂时不可用，已保留本机登录状态") }
            override fun onFormResubmission(view: WebView, dontResend: Message, resend: Message) { dontResend.sendToTarget(); fail("已阻止重复提交，请先核对充值记录") }
        }
    }

    private fun collectSession() {
        if (authFinished || failure || !active || Uri.parse(browser.url).host != "ykt.ujs.edu.cn") return
        browser.evaluateJavascript("JSON.stringify({token:sessionStorage.getItem('access_token')||''})") { raw ->
            if (authFinished || isFinishing || isDestroyed) return@evaluateJavascript
            val value = decodeResult(raw)?.optString("token").orEmpty()
            if (value.isNotBlank()) {
                try {
                    session.save(value); CookieManager.getInstance().flush(); authFinished = true
                    setResult(RESULT_OK); finish()
                } catch (_: Exception) { fail("登录成功，但无法安全保存到本机。请稍后重试，未保存账号密码。") }
            } else if (++checks < 40) handler.postDelayed(::collectSession, 500)
        }
    }

    private fun prepareRecharge() {
        stage = "正在准备学校支付服务…"
        val destination = SchoolApi.ORIGIN + "/campus-card/cardRecharge/"
        val token = quote(session.token)
        val html = "<!doctype html><meta charset=utf-8><script>sessionStorage.setItem('access_token',$token);sessionStorage.setItem('token_type','bearer');sessionStorage.setItem('agentType','h5');location.replace(${quote(destination)});</script>"
        browser.loadDataWithBaseURL(SchoolApi.ORIGIN + "/", html, "text/html", "UTF-8", null)
    }

    private fun submitWhenReady() {
        if (creationStarted || failure || !active) return
        if (++checks > 60) { fail("学校充值服务准备超时，尚未提交订单"); return }
        val expression = """
            (()=>{if(location.origin!==${quote(SchoolApi.ORIGIN)})return 'wrong_origin';
            const nodes=[document.querySelector('#app')?.__vue__];let vm;
            while(nodes.length){const x=nodes.shift();if(!x)continue;if(typeof x.confirm==='function'&&x.amountOptions&&Array.isArray(x.accountList)){vm=x;break}nodes.push(...(x.${'$'}children||[]))}
            if(!vm||!vm.accountList.length||vm.feeitemErrorMsg===null)return 'wait';
            const matches=vm.accountList.filter(x=>String(x.account)===${quote(account)}&&String(x.myID)===${quote(target)});
            if(matches.length!==1)return 'account_unavailable';
            const entry=matches[0];if(!entry.myIsRecharge)return 'account_blocked';
            vm.accountValue=entry.myID;vm.accountObj={...entry};
            vm.amountOptions.amount=${quote(Money.yuan(fen))};vm.amountValue={number:${Money.yuan(fen)},money:${quote(Money.yuan(fen))}};
            const options=vm.amountOptions,amount=vm.amountValue.number;
            if((options.minAmount&&amount<Number(options.minAmount))||(options.maxAmount&&amount>Number(options.maxAmount))||(options.onlyInt&&!Number.isInteger(amount)))return 'amount_rejected';
            if(!options.isEnter&&!(options.selectLayout||[]).some(x=>Number(x)===amount))return 'amount_rejected';
            if(!vm.allowRecharge||vm.btnLoad)return 'not_ready';
            return 'ready';})()
        """.trimIndent()
        browser.evaluateJavascript(expression) { raw ->
            if (creationStarted || failure || isFinishing || isDestroyed || !active) return@evaluateJavascript
            val result = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull()
            when (result) {
                "wait", "not_ready" -> handler.postDelayed(::submitWhenReady, 500)
                "ready" -> {
                    // Reserve before calling the official function. Rotation/process death cannot replay it.
                    creationStarted = true; session.pendingKind = "card"; session.pendingOrderId = ""; session.pendingFen = fen; session.pendingCheckout = "creating"
                    stage = "正在创建学校充值订单，请勿重复操作…"
                    browser.evaluateJavascript("(()=>{const q=[document.querySelector('#app')?.__vue__];while(q.length){const v=q.shift();if(!v)continue;if(typeof v.confirm==='function'&&v.amountOptions){if(v.btnLoad)return false;v.confirm();return true}q.push(...(v.${'$'}children||[]))}return false})()", null)
                }
                else -> fail("学校未允许这个账户或金额充值，尚未创建订单。请返回核对。")
            }
        }
    }

    private fun prepareElectric() {
        stage = if (creationStarted) "正在恢复原电费订单…" else "正在核对宿舍和电费金额…"
        val destination = SchoolApi.ORIGIN + "/charge/feeitem/toAppitem?feeitemid=408&synAccessSource=h5&synjones-auth=" + Uri.encode("bearer " + session.token)
        val html = "<!doctype html><meta charset=utf-8><script>sessionStorage.setItem('access_token',${quote(session.token)});sessionStorage.setItem('token_type','bearer');sessionStorage.setItem('agentType','h5');sessionStorage.removeItem('orderid');location.replace(${quote(destination)});</script>"
        browser.loadDataWithBaseURL(SchoolApi.ORIGIN + "/", html, "text/html", "UTF-8", null)
    }
    private fun electricTick() {
        if (failure || handedToWechat || !active || isFinishing || isDestroyed || payStarted) return
        if (Uri.parse(browser.url).host != "ykt.ujs.edu.cn" || Uri.parse(browser.url).path?.startsWith("/charge-app") != true) return
        if (++checks > 120) { fail(if (creationStarted) "电费订单状态待核对，已保留记录，请勿重复下单" else "电费服务准备超时，尚未创建订单"); return }
        val d = electricDorm
        val savedOrder = session.pendingOrderId
        val fields = JSONObject(d?.fields() ?: emptyMap<String, String>()).put("feeitemid", "408").put("type", "IEC").put("level", 4)
        val options = d?.let { listOf(it.campus, it.region, it.building, it.room).map { o -> JSONObject().put("name", o.name).put("value", o.id) } }.orEmpty()
        val expression = """
            (()=>{const q=[document.querySelector('#app')?.__vue__];let p,c;while(q.length){const v=q.shift();if(!v)continue;if(typeof v.placeOrder==='function'&&v.batteryParams)p=v;if(typeof v.toPay==='function'&&Array.isArray(v.pay)&&v.orders)c=v;q.push(...(v.${'$'}children||[]))}
            if(c){const id=String(c.orderid||sessionStorage.getItem('orderid')||'');if(!id)return {state:'wait'};
              if(${quote(savedOrder)}&&id!==${quote(savedOrder)})return {state:'mismatch'};
              if(!c.orders||!c.orders.orderid&&!c.orders.tranamt)return {state:'wait'};
              if(Number(c.orders.status)===1)return {state:'paid',id};
              if(Number(c.orders.tranamt).toFixed(2)!==${quote(Money.yuan(fen))})return {state:'mismatch'};
              const methods=c.pay.filter(x=>String(x.remark||'').includes('weixin')||x.name==='微信');if(!methods.length)return {state:'wait'};if(methods.length!==1)return {state:'unsupported'};
              if(!c.payment||c.payment.payid!==methods[0].payid){c.change(methods[0].payid,methods[0].name);return {state:'wait',id}}return {state:'cashier',id};}
            if(${quote(savedOrder)}){const root=document.querySelector('#app')?.__vue__;if(!root?.${'$'}router)return {state:'wait'};if(!window.__mudResume){window.__mudResume=true;sessionStorage.setItem('orderid',${quote(savedOrder)});root.${'$'}router.replace({name:'confirmPay'})}return {state:'wait'}}
            if(${creationStarted}){const id=sessionStorage.getItem('orderid')||'';return {state:'wait',id}}
            if(!p||String(p.feeitem?.feeitemid)!=='408'||!p.isEleric?.default||p.isBtnLoading)return {state:'wait'};
            if(!window.__mudPrepare){window.__mudPrepare='waiting';const fields=$fields;const opts=${options.joinToString(prefix = "[", postfix = "]")};
              if(fields.campus!=='校本部')return {state:'mismatch'};
              p.batteryParams={...fields};p.aboutEleric.total=['campus','building','floor','room'].map((code,i)=>({code,level:i+1,name:['校区','区域','楼栋','房间'][i]}));
              opts.forEach((o,i)=>p.${'$'}set(p.aboutEleric,'data'+(i+1),[o]));
              p.getBatteryData(fields).then(()=>{if(p.notPay||!p.third_party||!p.aboutEleric.electricInfo){window.__mudPrepare='blocked';return}p.getEnterCount(${quote(quantity)});window.__mudPrepare='ready'}).catch(()=>window.__mudPrepare='blocked');return {state:'wait'}}
            if(window.__mudPrepare==='blocked')return {state:'blocked'};if(window.__mudPrepare!=='ready')return {state:'wait'};
            if(Number(p.paytotal.number).toFixed(2)!==${quote(Money.yuan(fen))}||p.notPay)return {state:'mismatch'};
            if((p.feeitem.maxmoney&&Number(p.paytotal.number)>Number(p.feeitem.maxmoney))||(p.aboutEleric.minMoney&&Number(p.paytotal.number)<Number(p.aboutEleric.minMoney)))return {state:'mismatch'};
            return {state:'ready'};})()
        """.trimIndent()
        browser.evaluateJavascript(expression) { raw ->
            if (failure || handedToWechat || !active || isFinishing || isDestroyed || payStarted) return@evaluateJavascript
            val r = runCatching { JSONObject(raw) }.getOrNull()
            val id = r?.optString("id").orEmpty()
            if (id.isNotBlank() && id.matches(Regex("[A-Za-z0-9_-]{1,200}"))) { session.pendingOrderId = id; session.pendingKind = "electric"; session.pendingCheckout = "electric" }
            when (r?.optString("state")) {
                "wait" -> handler.postDelayed(::electricTick, 500)
                "ready" -> {
                    creationStarted = true; session.pendingKind = "electric"; session.pendingOrderId = ""; session.pendingFen = fen; session.pendingCheckout = "creating"
                    stage = "正在创建电费订单，请勿重复操作…"
                    browser.evaluateJavascript("(()=>{const q=[document.querySelector('#app')?.__vue__];while(q.length){const v=q.shift();if(!v)continue;if(typeof v.placeOrder==='function'&&v.batteryParams){if(v.isBtnLoading)return false;v.isNotice=true;v.placeOrder();return true}q.push(...(v.${'$'}children||[]))}return false})()", null)
                    handler.postDelayed(::electricTick, 500)
                }
                "cashier" -> {
                    payStarted = true; stage = "正在打开微信，请核对宿舍和金额…"
                    browser.evaluateJavascript("(()=>{const q=[document.querySelector('#app')?.__vue__];while(q.length){const v=q.shift();if(!v)continue;if(typeof v.toPay==='function'&&Array.isArray(v.pay)&&v.orders){if(v.isLoading)return false;v.toPay();return true}q.push(...(v.${'$'}children||[]))}return false})()", null)
                }
                "paid" -> { handedToWechat = true; reconcilePayment() }
                else -> fail("学校返回的宿舍、金额或支付方式需要重新核对，已停止提交")
            }
        }
    }

    private fun payWhenReady() {
        if (payStarted || failure || handedToWechat || !active) return
        if (++checks > 60) { fail("订单已保留，但微信支付服务准备超时，请先核对订单状态"); return }
        val expected = if (fen > 0) quote(Money.yuan(fen)) else "null"
        val expression = """
            (()=>{const q=[document.querySelector('#app')?.__vue__];let vm;while(q.length){const v=q.shift();if(!v)continue;if(typeof v.toPay==='function'&&Array.isArray(v.payList)&&v.orderInfo){vm=v;break}q.push(...(v.${'$'}children||[]))}
            if(!vm||!vm.payList.length)return 'wait';
            if($expected!==null&&Number(vm.orderInfo.tranamt).toFixed(2)!==$expected)return 'amount_mismatch';
            const methods=vm.payList.filter(x=>x.remark==='weixin'||String(x.name||'')==='微信');
            if(methods.length!==1||vm.payid)return 'unsupported';
            vm.payMethod=methods[0];return 'ready';})()
        """.trimIndent()
        browser.evaluateJavascript(expression) { raw ->
            if (payStarted || failure || handedToWechat || isFinishing || isDestroyed || !active) return@evaluateJavascript
            val result = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull()
            when (result) {
                "wait" -> handler.postDelayed(::payWhenReady, 500)
                "ready" -> {
                    payStarted = true; stage = "正在打开微信，请在微信中核对金额…"
                    browser.evaluateJavascript("(()=>{const q=[document.querySelector('#app')?.__vue__];while(q.length){const v=q.shift();if(!v)continue;if(typeof v.toPay==='function'&&Array.isArray(v.payList)&&v.orderInfo){if(v.loading)return false;v.toPay();return true}q.push(...(v.${'$'}children||[]))}return false})()", null)
                }
                else -> fail("学校订单金额或支付方式需要重新核对，已停止自动跳转，订单已保留")
            }
        }
    }

    private fun openWechat(url: String) {
        if (handedToWechat || !active || !Navigation.wechat(url)) return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage("com.tencent.mm").addCategory(Intent.CATEGORY_BROWSABLE))
            handedToWechat = true; stage = "请在微信完成支付，返回后会自动核对。"
        } catch (_: ActivityNotFoundException) { fail("未找到可处理付款的微信，学校订单已保留") }
        catch (_: SecurityException) { fail("微信没有接受此次跳转，学校订单已保留") }
    }
    private fun reconcilePayment() {
        if (!active || paymentConfirmed || reconciliationJob?.isActive == true) return
        val record = PendingPayment.capture(session) ?: run { stage = "暂未取得可核对的原订单，充值记录已保留"; return }
        failure = false; reconciling = true; stage = "正在向学校核对支付结果…"
        reconciliationJob = lifecycleScope.launch {
            try {
                var result = PaymentState.UNKNOWN
                for (wait in listOf(0L, 1500L, 3000L, 5000L)) {
                    delay(wait)
                    if (!active || !record.matches(session)) return@launch
                    result = SchoolApi(session).pendingPaymentStatus(record)
                    if (result != PaymentState.AWAITING) break
                }
                if (!active || !record.matches(session)) return@launch
                stage = PaymentPolicy.message(result)
                if (result == PaymentState.PAID) { session.clearPending(); paymentConfirmed = true }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { stage = "暂时无法向学校确认支付，原订单已保留，可稍后再次核对" }
            finally { reconciling = false }
        }
    }
    private fun loginUrl(): String = SchoolApi.ORIGIN + "/berserker-auth/cas/login/wisedu?targetUrl=" + Uri.encode(SchoolApi.ORIGIN + "/plat/?name=loginTransit")
    private fun decodeResult(raw: String): JSONObject? = runCatching { JSONObject(JSONTokener(raw).nextValue() as String) }.getOrNull()
    private fun quote(s: String): String = JSONObject.quote(s).replace("<", "\\u003c")
    private fun fail(message: String) { failure = true; stage = message; handler.removeCallbacksAndMessages(null) }
    private fun close() {
        if (payment) setResult(RESULT_OK, Intent().putExtra("payment_confirmed", paymentConfirmed).putExtra("payment_kind", if (electric) "electric" else "card"))
        finish()
    }
    override fun onResume() {
        super.onResume(); active = true
        if (payment && handedToWechat) { reconcilePayment(); return }
        if (::browser.isInitialized) {
            browser.onResume()
            val uri = browser.url?.let(Uri::parse)
            if (!payment && uri?.host == "ykt.ujs.edu.cn" && uri.path?.startsWith("/plat/") == true && uri.path != "/plat/login") collectSession()
            else if (payment && Navigation.checkout(browser.url)) payWhenReady()
            else if (payment && uri?.path?.startsWith("/campus-card/cardRecharge") == true) submitWhenReady()
            else if (electric && uri?.host == "ykt.ujs.edu.cn" && uri.path?.startsWith("/charge-app") == true) electricTick()
        }
    }
    override fun onPause() { active = false; reconciliationJob?.cancel(); reconciling = false; if (::browser.isInitialized) { CookieManager.getInstance().flush(); browser.onPause() }; super.onPause() }
    override fun onSaveInstanceState(outState: Bundle) { outState.putBoolean("creationStarted", creationStarted); outState.putBoolean("payStarted", payStarted); outState.putBoolean("handedToWechat", handedToWechat); outState.putBoolean("paymentConfirmed", paymentConfirmed); super.onSaveInstanceState(outState) }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); if (::browser.isInitialized) { browser.stopLoading(); browser.destroy() }; super.onDestroy() }
}
