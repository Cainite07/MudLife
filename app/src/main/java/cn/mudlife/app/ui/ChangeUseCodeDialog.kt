package cn.mudlife.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mudlife.app.ui.theme.AppColors

@Composable
fun ChangeUseCodeDialog(
    firstSetup: Boolean,
    projectLabel: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String, (Boolean, String) -> Unit) -> Unit
) {
    var newCode by remember { mutableStateOf("") }
    var confirmCode by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        shape = RoundedCornerShape(22.dp),
        containerColor = AppColors.SolidSurface,
        title = {
            Column {
                Text(
                    text = if (firstSetup) "设置洗浴使用码" else "修改洗浴使用码",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = AppColors.TextPrimary
                )
                if (projectLabel.isNotBlank()) Text(projectLabel, fontSize = 13.sp,
                    color = AppColors.TextSecondary, modifier = Modifier.padding(top = 4.dp))
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = newCode,
                    onValueChange = {
                        if (it.length <= 5 && it.all { c -> c in '0'..'9' }) {
                            newCode = it
                            errorMsg = ""
                        }
                    },
                    label = { Text("新 5 位使用码") },
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = confirmCode,
                    onValueChange = {
                        if (it.length <= 5 && it.all { c -> c in '0'..'9' }) {
                            confirmCode = it
                            errorMsg = ""
                        }
                    },
                    label = { Text("再次输入确认") },
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMsg.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = errorMsg,
                        color = AppColors.Danger,
                        fontSize = 12.sp
                    )
                }


            }
        },
        confirmButton = {
            Button(
                enabled = !busy,
                onClick = {
                    val validationError = validateCode(newCode, confirmCode)
                    if (validationError != null) {
                        errorMsg = validationError
                    } else {
                        onSubmit(newCode) { success, message ->
                            if (success) onDismiss() else errorMsg = message
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent)
            ) {
                Text(if (busy) "正在提交…" else "保存")
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) {
                Text("取消", color = AppColors.TextSecondary)
            }
        }
    )
}

/**
 * 校验规则（严格对齐微信小程序前端逻辑）
 */
private fun validateCode(newCode: String, confirmCode: String): String? {
    if (newCode.length < 5) return "使用码请设置为 5 位数"
    if (newCode != confirmCode) return "两次输入的使用码不一致"
    
    // 3 位连续相同（如 111, 222）
    if (Regex("""(\d)\1{2}""").containsMatchIn(newCode)) {
        return "使用码不能 3 位连续相同"
    }

    // 3 位连续递增或递减（如 123, 789, 321）
    for (i in 0 until newCode.length - 2) {
        val a = newCode[i].code
        val b = newCode[i + 1].code
        val c = newCode[i + 2].code
        if ((b - a == 1 && c - b == 1) || (a - b == 1 && b - c == 1)) {
            return "使用码不能 3 位连续顺号或倒序"
        }
    }

    return null
}
