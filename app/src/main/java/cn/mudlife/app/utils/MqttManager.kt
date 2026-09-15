package cn.mudlife.app.utils

import android.content.Context
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*

class MqttManager(
    private val context: Context,
    private val onMessageReceived: (String) -> Unit,
    private val onConnected: (() -> Unit)? = null
) {
    private var mqttClient: MqttAndroidClient? = null
    private val serverUri = "tcp://47.107.37.60:1883"
    private var isConnected = false

    val connected: Boolean get() = isConnected

    fun connect(phone: String) {
        if (isConnected) {
            onConnected?.invoke()
            return
        }

        val clientId = MqttClient.generateClientId()
        mqttClient = MqttAndroidClient(context, serverUri, clientId)

        val options = MqttConnectOptions().apply {
            isAutomaticReconnect = false
            isCleanSession = true
            connectionTimeout = 5
            keepAliveInterval = 10
        }

        try {
            mqttClient?.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    isConnected = true
                    subscribe("app_downRate_$phone")
                    subscribe("app_shutdownOrder_$phone")
                    subscribe("app_uploadData_$phone")
                    onConnected?.invoke()
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    isConnected = false
                    onConnected?.invoke() // still proceed — HTTP polling fallback
                }
            })

            mqttClient?.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) { isConnected = false }
                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    message?.let { onMessageReceived(String(it.payload)) }
                }
                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })
        } catch (e: MqttException) {
            isConnected = false
            onConnected?.invoke()
        }
    }

    private fun subscribe(topic: String) {
        try { mqttClient?.subscribe(topic, 0) } catch (_: MqttException) {}
    }

    fun disconnect() {
        try {
            mqttClient?.disconnect()
            mqttClient?.close()
            mqttClient = null
            isConnected = false
        } catch (_: Exception) {}
    }
}
