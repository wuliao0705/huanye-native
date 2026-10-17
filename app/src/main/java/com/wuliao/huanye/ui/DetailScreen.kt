package com.wuliao.huanye.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.wuliao.huanye.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 详情页 + AI 对话
 * @author 无聊
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(card: Card, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val db = remember { CardDb.get(ctx) }
    var detail by remember { mutableStateOf<Detail?>(null) }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var msgs by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()

    LaunchedEffect(card.id) {
        detail = withContext(Dispatchers.IO) { db.detail(card.id) }
    }

    Column(Modifier.fillMaxSize().background(Theme.bg)) {
        // 顶栏
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF1A1626)).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("←", color = Color.White, fontSize = 22.sp,
                modifier = Modifier.padding(end = 12.dp).clip(RoundedCornerShape(8.dp))
                    .background(Color(0x22FFFFFF)).padding(horizontal = 10.dp, vertical = 2.dp)
                    .clickable { onBack() })
            Text(card.name, color = Color.White, fontSize = 16.sp,
                fontWeight = FontWeight.Bold, maxLines = 1,
                modifier = Modifier.weight(1f))
        }

        Column(Modifier.weight(1f).verticalScroll(scroll).padding(12.dp)) {
            // 封面
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(ctx).data(card.cover).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(14.dp)),
                loading = { Box(Modifier.fillMaxSize().background(Theme.cardBg)) },
                error = { Box(Modifier.fillMaxSize().background(Theme.cardBg), contentAlignment = Alignment.Center) { Text("🖼", fontSize = 36.sp) } }
            )
            Spacer(Modifier.height(12.dp))
            Text(card.name, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("${card.srcName} · ${card.cat}", color = Theme.pink, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            if (card.summary.isNotBlank())
                Text(card.summary, color = Color(0xFFB0A8C0), fontSize = 13.sp)
            Spacer(Modifier.height(14.dp))

            detail?.let { d ->
                if (d.creator.isNotBlank()) {
                    Text("创作者：${d.creator}", color = Color(0xFF8A82A0), fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                }
                if (d.setting.isNotBlank()) Section("📖 角色设定", d.setting)
                if (d.generic.isNotBlank() && d.generic != d.setting) Section("🎭 场景", d.generic)
                if (d.opening.isNotBlank()) Section("💬 开场白", d.opening)
            }

            Spacer(Modifier.height(16.dp))
            Text("💬 对话", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (msgs.isEmpty()) {
                Text("在下方输入第一句话，开始和「${card.name}」对话",
                    color = Color(0xFF8A82A0), fontSize = 13.sp)
            }
            msgs.forEach { (role, text) ->
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (role == "user") Color(0xFF2A2038) else Color(0xFF1C1826))
                        .padding(10.dp)
                ) {
                    Text(if (role == "user") "你：$text" else "TA：$text",
                        color = if (role == "user") Color(0xFFD0C8E0) else Color.White,
                        fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(80.dp))
        }

        // 输入栏
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF1A1626)).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("说点什么…", color = Color(0xFF6A6280), fontSize = 13.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedBorderColor = Theme.pink, unfocusedBorderColor = Color(0xFF3A3450)
                ),
                shape = RoundedCornerShape(22.dp)
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.clip(RoundedCornerShape(22.dp)).background(Theme.grad)
                    .clickable(enabled = !sending && input.isNotBlank()) {
                        val q = input.trim()
                        if (q.isEmpty()) return@clickable
                        input = ""
                        sending = true
                        val history = msgs.toList()
                        msgs = msgs + ("user" to q) + ("assistant" to "")
                        scope.launch {
                            try {
                                val sys = buildString {
                                    append("你现在扮演角色「${card.name}」。\n")
                                    detail?.setting?.takeIf { it.isNotBlank() }?.let { append("角色设定：$it\n") }
                                    detail?.generic?.takeIf { it.isNotBlank() && it != detail?.setting }?.let { append("场景：$it\n") }
                                    append("请保持角色人设，用中文自然地回应，不要跳出角色。")
                                }
                                var acc = ""
                                AiClient.chatStream(sys, history, q) { delta ->
                                    acc += delta
                                    msgs = msgs.dropLast(1) + ("assistant" to acc)
                                }
                            } catch (e: Exception) {
                                msgs = msgs.dropLast(1) + ("assistant" to "⚠ ${e.message}\n（请到「我的」配置 AI 接口）")
                            }
                            sending = false
                        }
                    }
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(if (sending) "…" else "发送", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Section(title: String, body: String) {
    Column(Modifier.padding(bottom = 12.dp)) {
        Text(title, color = Theme.pink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(body, color = Color(0xFFC8C0D8), fontSize = 13.sp, lineHeight = 20.sp)
    }
}
