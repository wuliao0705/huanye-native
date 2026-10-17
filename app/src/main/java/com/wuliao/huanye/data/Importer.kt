package com.wuliao.huanye.data

import android.content.ContentValues
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.io.readText
import org.json.JSONObject
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream

/**
 * 从 assets 里的 gzip JSON 灌库。
 * 卡表 ~10MB / 详情 ~23MB（压缩后），首启 3~8 秒。
 * @author 无聊
 */
object Importer {

    private const val CARDS = "cards.json.gz"
    private const val DETAILS = "details.json.gz"

    fun open(ctx: Context, name: String) =
        InputStreamReader(GZIPInputStream(ctx.assets.open(name), 65536), Charsets.UTF_8)

    suspend fun ensure(ctx: Context, onProgress: (String) -> Unit = {}): Int =
        withContext(Dispatchers.IO) {
            val db = CardDb.get(ctx)
            if (db.isImported()) {
                onProgress("卡库已就绪")
                return@withContext db.count(null, null)
            }

            var done = 0

            // ───── 卡表：{v, srcs:{src:{cat:[[id,name,cover,summary,tags]]}}, total} ─────
            onProgress("正在导入卡表…")
            try {
                val txt = open(ctx, CARDS).use { it.readText() }
                val root = JSONObject(txt)
                val srcs = root.optJSONObject("srcs")
                if (srcs != null) {
                    var buf = ArrayList<ContentValues>(500)
                    val skeys = srcs.keys()
                    while (skeys.hasNext()) {
                        val src = skeys.next()
                        val cats = srcs.optJSONObject(src) ?: continue
                        val ckeys = cats.keys()
                        while (ckeys.hasNext()) {
                            val cat = ckeys.next()
                            val arr = cats.optJSONArray(cat) ?: continue
                            for (i in 0 until arr.length()) {
                                val a = arr.optJSONArray(i) ?: continue
                                val id = a.optString(0)
                                if (id.isEmpty()) continue
                                val name = a.optString(1)
                                val summary = a.optString(3)
                                buf.add(ContentValues().apply {
                                    put("id", id)
                                    put("name", name)
                                    put("cover", a.optString(2))
                                    put("summary", summary)
                                    put("tags", a.optJSONArray(4)?.toString() ?: "[]")
                                    put("src", src)
                                    put("cat", cat)
                                    put("hot", name.length * 3 + summary.length)
                                })
                                done++
                                if (buf.size >= 500) {
                                    db.importBatch(buf, emptyList())
                                    onProgress("卡表 $done / 64248")
                                    buf = ArrayList(500)
                                }
                            }
                        }
                    }
                    if (buf.isNotEmpty()) db.importBatch(buf, emptyList())
                }
            } catch (e: Exception) {
                onProgress("卡表导入失败：${e.message}")
            }

            // ───── 详情：{id:[opening,setting,intro,author]} ─────
            onProgress("正在导入详情…")
            try {
                val txt = open(ctx, DETAILS).use { it.readText() }
                val root = JSONObject(txt)
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
                        onProgress("详情 $dd / 50052")
                        buf = ArrayList(400)
                    }
                }
                if (buf.isNotEmpty()) db.importBatch(emptyList(), buf)
            } catch (e: Exception) {
                onProgress("详情导入失败（不影响卡库）：${e.message}")
            }

            onProgress("建立搜索索引…")
            try { db.rebuildFts() } catch (e: Exception) { }

            onProgress("完成！共 $done 张卡")
            db.count(null, null)
        }
}
