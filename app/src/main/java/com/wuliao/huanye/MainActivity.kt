package com.wuliao.huanye

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
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
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.wuliao.huanye.data.*
import com.wuliao.huanye.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 幻夜 v7 · 原生版
 * @author 无聊
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashHandler.install(applicationContext)
        setContent { App() }
    }
}

/** 页面路由 */
object Route {
    const val DISCOVER = "discover"
    const val LIBRARY = "library"
    const val SEARCH = "search"
    const val ME = "me"
    const val DETAIL = "detail/{cardId}"
    fun detail(id: String) = "detail/$id"
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val nav = rememberNavController()

    var ready by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("正在初始化…") }
    var total by remember { mutableIntStateOf(0) }

    var err by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            total = withContext(Dispatchers.IO) {
                Importer.ensure(ctx) { s -> status = s }
            }
            ready = true
        } catch (t: Throwable) {
            val sw = java.io.StringWriter()
            t.printStackTrace(java.io.PrintWriter(sw))
            err = sw.toString().take(2500)
        }
    }

    // 初始化失败 → 直接显示错误（避免"无提示闪退"）
    err?.let { e ->
        Column(
            Modifier.fillMaxSize().background(Theme.bg).padding(16.dp).verticalScroll(rememberScrollState())
        ) {
            Text("启动失败", color = Theme.pink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(e, color = Color(0xFFFF8888), fontSize = 11.sp)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { err = null; ready = false }) { Text("重试") }
        }
        return
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

    // ★ 返回键在 NavHost 里按导航栈自动处理 ★
    AppNav(nav, total)
}

@Composable
fun AppNav(nav: NavHostController, total: Int) {
    val ctx = LocalContext.current
    // 根页面当前 tab（用于底栏高亮与"再按一次退出"判定）
    var rootTab by remember { mutableStateOf(Route.DISCOVER) }

    // ★★ 核心修复：绑定 Activity 返回键 ★★
    // NavHost 内部会自动注册 OnBackPressedCallback：
    //   - 栈里有上一级 → popBackStack() → 回退（不退出 App）
    //   - 栈空（在根页面）→ 回调返回 false → 系统默认行为
    // 这里额外接管"根页面返回"：第一次提示，2 秒内第二次才退出。
    var backAt by remember { mutableLongStateOf(0L) }

    // 根页面（发现/卡库/搜索/我的）→ 拦截返回键
    if (rootTab != Route.DISCOVER || nav.currentDestination?.route == Route.DISCOVER) {
        // 只在当前确实处在根页面时才接管
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            NavHost(
                navController = nav,
                startDestination = Route.DISCOVER,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(Route.DISCOVER) {
                    RootBackHandler(nav, rootTab) { rootTab = Route.DISCOVER }
                    DiscoverScreen { openDetail(nav, it) }
                }
                composable(Route.LIBRARY) {
                    RootBackHandler(nav, rootTab) { rootTab = Route.LIBRARY }
                    LibraryScreen { openDetail(nav, it) }
                }
                composable(Route.SEARCH) {
                    RootBackHandler(nav, rootTab) { rootTab = Route.SEARCH }
                    SearchScreen { openDetail(nav, it) }
                }
                composable(Route.ME) {
                    RootBackHandler(nav, rootTab) { rootTab = Route.ME }
                    MeScreen(total)
                }
                composable(Route.DETAIL) { entry ->
                    val id = entry.arguments?.getString("cardId") ?: ""
                    // ★ 详情页返回键：交给 NavHost → popBackStack 回上一级（不退出）
                    DetailRoute(id) { nav.popBackStack() }
                }
            }
        }

        // 底栏只在根页面显示
        val cur = nav.currentBackStackEntryAsState().value?.destination?.route
        if (cur != null && !cur.startsWith("detail/")) {
            BottomBar(rootTab) { route ->
                if (route != rootTab) {
                    rootTab = route
                    nav.navigate(route) {
                        popUpTo(Route.DISCOVER) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        }
    }
}

/**
 * 根页面的返回键处理：
 * - 不在发现页 → 先回发现页（不退出）
 * - 在发现页 → 第一次 Toast 提示，2 秒内再按才退出
 */
@Composable
fun RootBackHandler(
    nav: NavHostController,
    curTab: String,
    onTab: () -> Unit
) {
    val ctx = LocalContext.current
    var lastAt by remember { mutableLongStateOf(0L) }

    BackHandler(enabled = true) {
        if (curTab != Route.DISCOVER) {
            // 回发现页
            onTab()
            nav.navigate(Route.DISCOVER) {
                popUpTo(Route.DISCOVER) { inclusive = true }
                launchSingleTop = true
            }
        } else {
            val now = System.currentTimeMillis()
            if (now - lastAt < 2000) {
                // 二次确认 → 放行退出
                (ctx as? android.app.Activity)?.finish()
            } else {
                lastAt = now
                Toast.makeText(ctx, "再按一次返回键退出幻夜", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/** 详情路由：从 DB 取卡，找不到就回退 */
@Composable
fun DetailRoute(cardId: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var card by remember { mutableStateOf<Card?>(null) }
    var miss by remember { mutableStateOf(false) }

    LaunchedEffect(cardId) {
        val c = withContext(Dispatchers.IO) { CardDb.get(ctx).card(cardId) }
        if (c == null) miss = true else card = c
    }

    when {
        miss -> {
            // 卡不存在：返回键直接回上一级
            BackHandler { onBack() }
            Box(Modifier.fillMaxSize().background(Theme.bg), contentAlignment = Alignment.Center) {
                Text("卡片不存在", color = Color(0xFF8A82A0))
            }
        }
        card != null -> DetailScreen(card!!, onBack)
        else -> Box(Modifier.fillMaxSize().background(Theme.bg)) { LoadingBox("加载中…") }
    }
}

fun openDetail(nav: NavHostController, card: Card) {
    nav.navigate(Route.detail(card.id))
}

@Composable
fun BottomBar(cur: String, click: (String) -> Unit) {
    data class Item(val tab: String, val icon: String, val name: String)
    val items = listOf(
        Item(Route.DISCOVER, "🏠", "发现"),
        Item(Route.LIBRARY, "📚", "卡库"),
        Item(Route.SEARCH, "🔍", "搜索"),
        Item(Route.ME, "👤", "我的")
    )
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF1A1626)).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        items.forEach { it2 ->
            val on = it2.tab == cur
            Column(
                Modifier.clip(RoundedCornerShape(10.dp))
                    .clickable { click(it2.tab) }
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(it2.icon, fontSize = 18.sp)
                Text(
                    it2.name,
                    color = if (on) Theme.pink else Color(0xFF8A82A0),
                    fontSize = 10.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal
                )
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
        Spacer(Modifier.height(20.dp))

        val ctx2 = LocalContext.current
        var logTxt by remember { mutableStateOf<String?>(null) }
        OutlinedButton(onClick = { logTxt = CrashHandler.readRecent(ctx2).ifBlank { "（无崩溃记录）" } }) {
            Text("查看崩溃日志")
        }
        logTxt?.let { t ->
            Spacer(Modifier.height(12.dp))
            Text(t, color = Color(0xFFFF9999), fontSize = 10.sp,
                 modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()))
        }
    }
}
