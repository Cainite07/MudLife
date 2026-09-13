package com.hualala.linyu.model

data class MqttOrderMsg(
    val orderNo: String? = null,
    val consumeMoney: Double? = null,
    val state: Int? = null,
    val result: Int? = null
)
