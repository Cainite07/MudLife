package cn.mudlife.app.model

/** Never manufacture missing digits. Some service versions return only a suffix. */
object BathCode {
    fun isPrefix(value: String) = value.matches(Regex("[0-9]{5}"))

    fun complete(serverCode: String?, savedPrefix: String): String? = when {
        serverCode?.matches(Regex("[0-9]{8}")) == true -> serverCode
        serverCode?.matches(Regex("[0-9]{3}")) == true && isPrefix(savedPrefix) -> savedPrefix + serverCode
        else -> null
    }

    fun storageKey(phone: String, projectId: String): String? =
        if (phone.matches(Regex("[0-9]{11}")) && projectId.matches(Regex("[0-9]{1,12}"))) {
            "bathPrefix_${phone}_$projectId"
        } else null
}
