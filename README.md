# 幻夜 v7 · 原生重构版

> 作者：无聊　｜　Kotlin + Jetpack Compose　｜　非 WebView 原生 App

## 这是什么

幻夜角色卡酒馆的**原生重构版**。原版是 WebView + 97MB JS，本版改为：

- **原生 UI**（Compose，虚拟列表，64248 张卡零卡顿）
- **SQLite 卡库**（FTS5 全文搜索，毫秒级）
- **gzip 资产**（33MB → 首启导入一次，之后秒开）
- **AI 对话**（OpenAI 兼容流式 SSE，OkHttp 原生）

## 数据

| 项 | 值 |
|---|---|
| 卡片 | 64,248 张 |
| 详情 | 50,052 条 |
| 源 | 风月 / 幻梦 / dzmm / 夜语 / 香草 |
| 分类 | 15 个 |

## 构建

推送代码后 GitHub Actions 自动编译，产物在 **Actions → Artifacts → huanye-apk**。

## 本地编译

```bash
gradle :app:assembleRelease
# 产物 app/build/outputs/apk/release/app-release.apk
```

签名：`keystore/huanye.p12`（密码见 `app/build.gradle.kts`）

## 数据更新

```bash
python3 tools/build_data.py <交接包目录> app/src/main/assets
```

## 目录

```
app/src/main/java/com/wuliao/huanye/
├─ MainActivity.kt        # 入口 + 底部导航 + 搜索页
├─ data/CardDb.kt         # SQLite + FTS5
├─ data/Importer.kt       # gzip 资产流式导入
├─ data/AiClient.kt       # AI 流式对话
└─ ui/
   ├─ Widgets.kt          # 卡片/网格/分区标签
   ├─ Screens.kt          # 发现页/卡库页
   └─ DetailScreen.kt     # 详情页 + 对话
```

---

*作者：无聊*
