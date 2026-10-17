package com.wuliao.huanye.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wuliao.huanye.data.Card
import com.wuliao.huanye.data.CardDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 发现页 · 三分区互斥 + 触底加载
 * @author 无聊
 */
@Composable
fun DiscoverScreen(onCard: (Card) -> Unit) {
    val ctx = LocalContext.current
    val db = remember { CardDb.get(ctx) }

    var section by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var sec0 by remember { mutableStateOf<List<Card>>(emptyList()) }
    var sec1 by remember { mutableStateOf<List<Card>>(emptyList()) }
    var sec2 by remember { mutableStateOf<List<Card>>(emptyList()) }
    var extra by remember { mutableStateOf<List<Card>>(emptyList()) }

    LaunchedEffect(Unit) {
        val r = withContext(Dispatchers.IO) { db.discoverSections(24) }
        sec0 = r.first; sec1 = r.second; sec2 = r.third
        loading = false
    }

    LaunchedEffect(section) { extra = emptyList() }

    val base = when (section) {
        0 -> sec0
        1 -> sec1
        else -> sec2
    }
    val list = remember(base, extra) { base + extra }
    val mode = when (section) { 1 -> 1; 2 -> 2; else -> 0 }

    Column(Modifier.fillMaxSize().background(Theme.bg)) {
        SectionTabs(section) { section = it }

        if (loading) {
            LoadingBox("正在装载卡库…")
        } else {
            if (list.isEmpty()) {
                LoadingBox("暂无数据")
            } else {
                CardGrid(list, Modifier.fillMaxSize()) { onCard(it) }
            }
            // 触底加载
            LaunchedEffect(section, list.size) {
                if (list.size > 0 && list.size < 60000) {
                    val more = withContext(Dispatchers.IO) {
                        db.page(null, null, mode, list.size, 24)
                    }
                    val exist = list.map { it.id }.toHashSet()
                    val add = more.filter { it.id !in exist }
                    if (add.isNotEmpty()) extra = extra + add
                }
            }
        }
    }
}

/** 卡库页 · 源切换 + 分类筛选 */
@Composable
fun LibraryScreen(onCard: (Card) -> Unit) {
    val ctx = LocalContext.current
    val db = remember { CardDb.get(ctx) }

    var src by remember { mutableStateOf<String?>(null) }
    var cat by remember { mutableStateOf<String?>(null) }
    var cards by remember { mutableStateOf<List<Card>>(emptyList()) }
    var end by remember { mutableStateOf(false) }
    val pageSize = 40

    LaunchedEffect(src, cat) {
        cards = withContext(Dispatchers.IO) { db.page(src, cat, 0, 0, pageSize) }
        end = cards.isEmpty()
    }

    Column(Modifier.fillMaxSize().background(Theme.bg)) {
        LazyRow(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Chip("全部源", src == null) { src = null } }
            items(CardDb.SRC_NAMES.keys.toList()) { k ->
                Chip(CardDb.SRC_NAMES[k] ?: k, src == k) { src = if (src == k) null else k }
            }
        }
        LazyRow(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Chip("全部分类", cat == null) { cat = null } }
            items(CardDb.CATS) { c ->
                Chip(c, cat == c) { cat = if (cat == c) null else c }
            }
        }

        CardGrid(cards, Modifier.weight(1f)) { onCard(it) }

        LaunchedEffect(src, cat, cards.size) {
            if (!end && cards.isNotEmpty()) {
                val more = withContext(Dispatchers.IO) { db.page(src, cat, 0, cards.size, pageSize) }
                if (more.isEmpty()) {
                    end = true
                } else {
                    val exist = cards.map { it.id }.toHashSet()
                    cards = cards + more.filter { it.id !in exist }
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String, on: Boolean, click: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (on) Theme.grad
                else Brush.horizontalGradient(listOf(Color(0x22FFFFFF), Color(0x22FFFFFF)))
            )
            .clickable { click() }
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text, color = Color.White, fontSize = 12.sp,
            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal
        )
    }
}
