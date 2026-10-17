package com.wuliao.huanye

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wuliao.huanye.data.*
import com.wuliao.huanye.ui.*
import com.wuliao.huanye.ui.Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 幻夜 v7 · 原生版
 * @author 无聊
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

enum class Tab { DISCOVER, LIBRARY, SEARCH, ME }

@Composable
fun App() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var ready by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("正在初始化…") }
    var total by remember { mutableIntStateOf(0) }
    var tab by remember { mutableStateOf(Tab.DISCOVER) }
    var opened by remember { mutableStateOf<Card?>(null) }

    LaunchedEffect(Unit) {
        total = withContext(Dispatchers.IO) {
            Importer.ensure(ctx) { s -> status = s }
        }
        ready = true
    }

    if (!ready) {
        Box(Modifier.fillMaxSize().background(Theme.bg), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Theme.pink, strokeWidth = 3.dp)
                Spacer(Modifier.height(14.dp))
                Text(status, color = Color.White, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Text("首次启动需要建卡库，请稍候", color = Color(0xFF8A82A0), fontSize = 11.sp)
            }
        }
        return
    }

    // 返回键：详情页 → 回列表；列表 → 首页；首页 → 再按退出
    BackHandler(enabled = opened != null) { opened = null }
    var lastBack by remember { mutableLongStateOf(0L) }
    BackHandler(enabled = opened == null && tab != Tab.DISCOVER) { tab = Tab.DISCOVER }

    // 详情
    opened?.let { c ->
        DetailScreen(c) { opened = null }
        return
    }

    Scaffold(
        containerColor = Theme.bg,
        bottomBar = { BottomBar(tab) { tab = it } }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                Tab.DISCOVER -> DiscoverScreen { opened = it }
                Tab.LIBRARY -> LibraryScreen { opened = it }
                Tab.SEARCH -> SearchScreen { opened = it }
                Tab.ME -> MeScreen(total)
            }
        }
    }
}

@Composable
fun BottomBar(cur: Tab, click: (Tab) -> Unit) {
    data class Item(val tab: Tab, val icon: String, val name: String)
    val items = listOf(
        Item(Tab.DISCOVER, "🏠", "发现"),
        Item(Tab.LIBRARY, "📚", "卡库"),
        Item(Tab.SEARCH, "🔍", "搜索"),
        Item(Tab.ME, "👤", "我的")
    )
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF1A1626)).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        items.forEach { it2 ->
            val t = it2.tab
            val icon = it2.icon
            val name = it2.name
            val on = t == cur
            Column(
                Modifier.clip(RoundedCornerShape(10.dp))
                    .clickable { click(t) }
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(icon, fontSize = 18.sp)
                Text(name, color = if (on) Theme.pink else Color(0xFF8A82A0),
                    fontSize = 10.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

/** 搜索页（FTS5 全文） */
@Composable
fun SearchScreen(onCard: (Card) -> Unit) {
    val ctx = LocalContext.current
    val db = remember { CardDb.get(ctx) }
    var q by remember { mutableStateOf("") }
    var res by remember { mutableStateOf<List<Card>>(emptyList()) }

    LaunchedEffect(q) {
        if (q.isBlank()) { res = emptyList(); return@LaunchedEffect }
        kotlinx.coroutines.delay(220)
        res = withContext(Dispatchers.IO) { db.search(q, 100) }
    }

    Column(Modifier.fillMaxSize().background(Theme.bg)) {
        OutlinedTextField(
            value = q, onValueChange = { q = it },
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            placeholder = { Text("搜角色 / 关键词…", color = Color(0xFF6A6280)) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                focusedBorderColor = Theme.pink, unfocusedBorderColor = Color(0xFF3A3450)
            ),
            shape = RoundedCornerShape(22.dp)
        )
        if (res.isEmpty() && q.isNotBlank())
            LoadingBox("没有找到，换个词试试")
        else
            CardGrid(res, Modifier.fillMaxSize()) { onCard(it) }
    }
}

/** 我的 */
@Composable
fun MeScreen(total: Int) {
    Column(Modifier.fillMaxSize().background(Theme.bg).padding(16.dp)) {
        Text("幻夜 v7 · 原生版", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("卡库：$total 张", color = Theme.pink, fontSize = 13.sp)
        Spacer(Modifier.height(2.dp))
        Text("作者：无聊", color = Color(0xFF8A82A0), fontSize = 12.sp)
    }
}
