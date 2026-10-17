package com.wuliao.huanye.data

import android.content.ContentValues
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStreamReader

/**
 * assets 灌库。采用「大块 readText + JSONObject」但分批落库，控制峰值内存。
 * 首次启动 3~8 秒，之后秒开。
 * @author 无聊
 */
object Importer {

    private const val CARDS = "cards.json"
    private const val DETAILS = "details.json"

    suspend fun ensure(ctx: Context, onProgress: (String) -> Unit = {}): Int =
        withContext(Dispatchers.IO) {
            val db = CardDb.get(ctx)
            // 建表本身也可能失败（老机型/畸形 ROM）→ 捕获后返回 0，让界面能进
            try {
                if (db.isImported()) {
                    onProgress("卡库已就绪")
                    return@withContext db.count(null, null)
                }
            } catch (t: Throwable) {
                onProgress("数据库不可用: ${t.javaClass.simpleName}")
                throw t
            }

            var done = 0

            // ═══ 卡表 ═══
            onProgress("正在导入卡表…")
            try {
                val rootTxt = InputStreamReader(ctx.assets.open(CARDS), Charsets.UTF_8)
                    .use { it.readText() }
                val root = JSONObject(rootTxt)
                val srcs = root.optJSONObject("srcs")

                if (srcs != null) {
                    var buf = ArrayList<ContentValues>(500)
                    val srcKeys = srcs.keys()
                    while (srcKeys.hasNext()) {
                        val src = srcKeys.next()
                        val cats = srcs.optJSONObject(src) ?: continue
                        val catKeys = cats.keys()
                        while (catKeys.hasNext()) {
                            val cat = catKeys.next()
                            val arr = cats.optJSONArray(cat) ?: continue
                            for (i in 0 until arr.length()) {
                                val row = arr.optJSONArray(i) ?: continue
                                val id = row.optString(0)
                                if (id.isEmpty()) continue
                                val name = row.optString(1)
                                val summary = row.optString(3)
                                buf.add(ContentValues().apply {
                                    put("id", id)
                                    put("name", name)
                                    put("cover", row.optString(2))
                                    put("summary", summary)
                                    put("tags", row.optJSONArray(4)?.toString() ?: "[]")
                                    put("src", src)
                                    put("cat", cat)
                                    put("hot", name.length * 3 + summary.length)
                                })
                                done++
                                if (buf.size >= 500) {
                                    db.importBatch(buf, emptyList())
                                    onProgress("卡表 $done")
                                    buf = ArrayList(500)
                                }
                            }
                        }
                    }
                    if (buf.isNotEmpty()) db.importBatch(buf, emptyList())
                    onProgress("卡表完成 $done 张")
                } else {
                    onProgress("卡表结构异常：缺 srcs")
                }
            } catch (e: Throwable) {
                onProgress("卡表导入失败：${e.message}")
            }

            // ═══ 详情 ═══
            onProgress("正在导入详情…")
            try {
                val dTxt = InputStreamReader(ctx.assets.open(DETAILS), Charsets.UTF_8)
                    .use { it.readText() }
                val root = JSONObject(dTxt)
                var buf = ArrayList<ContentValues>(400)
                var dd = 0
                val keys = root.keys()
                while (keys.hasNext()) {
                    val id = keys.next()
                    val a = root.optJSONArray(id) ?: continue
                    buf.add(ContentValues().apply {
                        put("id", id)
                        put("opening", a.optString(0))
                        put("setting", a.optString(1))
                        put("generic", a.optString(2))
                        put("creator", a.optString(3))
                        put("media", "")
                    })
                    dd++
                    if (buf.size >= 400) {
                        db.importBatch(emptyList(), buf)
                        onProgress("详情 $dd")
                        buf = ArrayList(400)
                    }
                }
                if (buf.isNotEmpty()) db.importBatch(emptyList(), buf)
                onProgress("详情完成 $dd 条")
            } catch (e: Throwable) {
                onProgress("详情导入失败（不影响卡库）：${e.message}")
            }

            onProgress("建立搜索索引…")
            db.markImported()

            onProgress("完成！共 $done 张卡")
            db.count(null, null)
        }
}
