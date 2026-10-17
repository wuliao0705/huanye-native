package com.wuliao.huanye.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.wuliao.huanye.data.Card
import androidx.compose.ui.platform.LocalContext

/** 主题色 */
object Theme {
    val pink = Color(0xFFFF6B9D)
    val purple = Color(0xFFC44DFF)
    val bg = Color(0xFF12101A)
    val cardBg = Color(0xFF1C1826)
    val grad = Brush.horizontalGradient(listOf(pink, purple))
}

/**
 * 卡片：封面铺满 + 底部渐变遮罩 + 名字叠图底 + 左上来源角标
 * 图片失败 → 重试 2 次 → 占位图（永不永久透明）
 */
@Composable
fun CardItem(card: Card, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val ctx = LocalContext.current
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Theme.cardBg)
            .clickable { onClick() }
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(ctx)
                .data(card.cover)
                .crossfade(true)
                .setHeader("Referer", "")
                .build(),
            contentDescription = card.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            loading = {
                Box(Modifier.fillMaxSize().background(Theme.cardBg))
            },
            error = {
                Box(Modifier.fillMaxSize().background(Theme.cardBg),
                    contentAlignment = Alignment.Center) {
                    Text("🖼", fontSize = 28.sp)
                }
            }
        )

        // 底部渐变遮罩
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.42f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xE6000000))
                    )
                )
        )

        // 左上来源角标
        Text(
            card.srcName,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(8.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xB3000000))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )

        // 名字
        Text(
            card.name,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 8.dp, vertical = 8.dp)
        )
    }
}

/** 两卡一行零缝隙网格 */
@Composable
fun CardGrid(
    cards: List<Card>,
    modifier: Modifier = Modifier,
    onCard: (Card) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier,
        contentPadding = PaddingValues(0.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        items(cards, key = { it.id }) { c ->
            Box(Modifier.fillMaxWidth().aspectRatio(0.52f)) {
                CardItem(c, Modifier.fillMaxSize()) { onCard(c) }
            }
        }
    }
}

/** 发现页三分区按钮（吸顶） */
@Composable
fun SectionTabs(selected: Int, onSelect: (Int) -> Unit) {
    val names = listOf("🔥 精品推荐", "✨ 每日推荐", "💕 猜你喜欢")
    Row(
        Modifier
            .fillMaxWidth()
            .background(Theme.bg)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        names.forEachIndexed { i, n ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (on) Theme.grad else Brush.horizontalGradient(listOf(Color(0x33FFFFFF), Color(0x33FFFFFF))))
                    .clickable { onSelect(i) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                // 未选中：固定白字（浅色主题也看得清）
                Text(n, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** 加载占位 */
@Composable
fun LoadingBox(text: String = "加载中…") {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Theme.pink, strokeWidth = 3.dp)
            Spacer(Modifier.height(12.dp))
            Text(text, color = Color(0xFFB0A8C0), fontSize = 13.sp)
        }
    }
}
