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
    phoneSuffix: String = "",
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var newCode by remember { mutableStateOf("") }
    var confirmCode by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = AppColors.SolidSurface,
        title = {
            Column {
                Text(
                    text = "修改浴室洗澡码",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = AppColors.TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "设置前 5 位自定义码，后 3 位由系统动态随机分配",
                    fontSize = 12.sp,
                    color = AppColors.TextSecondary
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = newCode,
                    onValueChange = {
                        if (it.length <= 5 && it.all { c -> c.isDigit() }) {
                            newCode = it
                            errorMsg = ""
                        }
                    },
                    label = { Text("新 5 位使用码") },
                    placeholder = { Text("例如：58291") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = confirmCode,
                    onValueChange = {
                        if (it.length <= 5 && it.all { c -> c.isDigit() }) {
                            confirmCode = it
                            errorMsg = ""
                        }
                    },
                    label = { Text("再次输入确认") },
                    singleLine = true,
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

                Spacer(Modifier.height(10.dp))
                Text(
                    text = "• 必须为 5 位纯数字\n• 不能有 3 位连续相同（如 111）\n• 不能有 3 位连续递增/递减（如 123、321）",
                    color = AppColors.TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val validationError = validateCode(newCode, confirmCode)
                    if (validationError != null) {
                        errorMsg = validationError
                    } else {
                        onSubmit(newCode)
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent)
            ) {
                Text("提交修改")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
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
