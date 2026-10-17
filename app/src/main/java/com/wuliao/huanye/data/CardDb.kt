package com.wuliao.huanye.data

import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Card(
    val id: String,
    val name: String,
    val cover: String,
    val summary: String,
    val tags: String,
    val src: String,
    val cat: String
) {
    val srcName: String get() = CardDb.SRC_NAMES[src] ?: src
}

data class Detail(
    val id: String,
    val opening: String,
    val setting: String,
    val generic: String,
    val creator: String,
    val media: String
)

/**
 * 卡库 SQLite（不依赖 FTS5 —— 许多 Android 系统 SQLite 未编译该模块）
 * @author 无聊
 */
class CardDb private constructor(ctx: Context) :
    SQLiteOpenHelper(ctx, DB_NAME, null, DB_VER) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS cards(
              rowid INTEGER PRIMARY KEY AUTOINCREMENT,
              id TEXT UNIQUE, name TEXT, cover TEXT, summary TEXT,
              tags TEXT, src TEXT, cat TEXT, hot INTEGER DEFAULT 0)
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS i_src ON cards(src)")
        db.execSQL("CREATE INDEX IF NOT EXISTS i_cat ON cards(cat)")
        db.execSQL("CREATE INDEX IF NOT EXISTS i_s ON cards(src,cat)")
        db.execSQL("CREATE INDEX IF NOT EXISTS i_hot ON cards(hot DESC)")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS details(
              id TEXT PRIMARY KEY, opening TEXT, setting TEXT,
              generic TEXT, creator TEXT, media TEXT)
        """)
        // 普通 B-Tree 索引（FTS5 在多数 Android 系统 SQLite 中不可用）
        db.execSQL("CREATE INDEX IF NOT EXISTS i_name ON cards(name)")
        db.execSQL("CREATE TABLE IF NOT EXISTS meta(k TEXT PRIMARY KEY, v TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) {
        // 结构变更：清空重建（数据由 assets 重新导入）
        db.execSQL("DROP TABLE IF EXISTS cards")
        db.execSQL("DROP TABLE IF EXISTS details")
        db.execSQL("DROP TABLE IF EXISTS meta")
        onCreate(db)
    }

    // ───────── 写入 ─────────

    fun importBatch(cards: List<ContentValues>, details: List<ContentValues>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            cards.forEach { db.insertWithOnConflict("cards", null, it, SQLiteDatabase.CONFLICT_IGNORE) }
            details.forEach { db.insertWithOnConflict("details", null, it, SQLiteDatabase.CONFLICT_REPLACE) }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun markImported() {
        writableDatabase.execSQL("INSERT OR REPLACE INTO meta(k,v) VALUES('imported','1')")
    }

    fun isImported(): Boolean = try {
        readableDatabase.rawQuery("SELECT v FROM meta WHERE k='imported'", null).use {
            it.moveToFirst() && it.getString(0) == "1"
        }
    } catch (e: Exception) { false }

    // ───────── 查询 ─────────

    fun count(src: String?, cat: String?): Int {
        val (w, a) = where(src, cat)
        readableDatabase.rawQuery("SELECT COUNT(*) FROM cards $w", a).use {
            return if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    private fun where(src: String?, cat: String?): Pair<String, Array<String>> {
        val cond = ArrayList<String>()
        val args = ArrayList<String>()
        if (src != null) { cond.add("src=?"); args.add(src) }
        if (cat != null) { cond.add("cat=?"); args.add(cat) }
        val w = if (cond.isEmpty()) "" else "WHERE " + cond.joinToString(" AND ")
        return w to args.toTypedArray()
    }

    /**
     * 分页查询
     * @param mode 0=正常  1=按序反转（给第二分区不同顺序用）  2=随机种子
     */
    fun page(src: String?, cat: String?, mode: Int, offset: Int, limit: Int): List<Card> {
        val (w, a) = where(src, cat)
        val order = when (mode) {
            1 -> "rowid ASC"
            2 -> "(rowid * 2654435761) % 100000 ASC"
            else -> "hot DESC, rowid ASC"
        }
        val sql = "SELECT id,name,cover,summary,tags,src,cat FROM cards $w ORDER BY $order LIMIT ? OFFSET ?"
        return readableDatabase.rawQuery(sql, a + arrayOf(limit.toString(), offset.toString())).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    Card(c.getString(0), c.getString(1), c.getString(2),
                        c.getString(3) ?: "", c.getString(4) ?: "[]",
                        c.getString(5), c.getString(6) ?: "")
                )
            }
        }
    }

    /** 发现页三分区：互斥分配，三个分区不重复 */
    fun discoverSections(n: Int): Triple<List<Card>, List<Card>, List<Card>> {
        val a = page(null, null, 0, 0, n)
        val b = page(null, null, 1, n, n)
        val c = page(null, null, 2, n * 2, n)
        val used = HashSet<String>()
        fun uniq(l: List<Card>) = l.filter { used.add(it.id) }
        return Triple(uniq(a), uniq(b), uniq(c))
    }

    fun search(q: String, limit: Int): List<Card> {
        val kw = q.replace("%", "").replace("_", "").trim()
        if (kw.isEmpty()) return emptyList()
        return searchLike(kw, limit)
    }

    private fun searchLike(kw: String, limit: Int): List<Card> {
        val sql = "SELECT id,name,cover,summary,tags,src,cat FROM cards WHERE name LIKE ? OR summary LIKE ? ORDER BY hot DESC LIMIT ?"
        val p = "%$kw%"
        return readableDatabase.rawQuery(sql, arrayOf(p, p, limit.toString())).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    Card(c.getString(0), c.getString(1), c.getString(2),
                        c.getString(3) ?: "", c.getString(4) ?: "[]",
                        c.getString(5), c.getString(6) ?: "")
                )
            }
        }
    }

    /** 单张卡（详情页用） */
    fun card(id: String): Card? =
        readableDatabase.rawQuery(
            "SELECT id,name,cover,summary,tags,src,cat FROM cards WHERE id=?", arrayOf(id)
        ).use {
            if (it.moveToFirst())
                Card(it.getString(0), it.getString(1), it.getString(2),
                    it.getString(3) ?: "", it.getString(4) ?: "[]",
                    it.getString(5), it.getString(6) ?: "")
            else null
        }

    fun detail(id: String): Detail? =
        readableDatabase.rawQuery("SELECT id,opening,setting,generic,creator,media FROM details WHERE id=?", arrayOf(id)).use {
            if (it.moveToFirst())
                Detail(it.getString(0), it.getString(1) ?: "", it.getString(2) ?: "",
                    it.getString(3) ?: "", it.getString(4) ?: "", it.getString(5) ?: "")
            else null
        }

    companion object {
        private const val DB_NAME = "huanye.db"
        private const val DB_VER = 2

        @Volatile private var inst: CardDb? = null

        fun get(ctx: Context): CardDb = inst ?: synchronized(this) {
            inst ?: CardDb(ctx.applicationContext).also { inst = it }
        }

        val SRC_NAMES = linkedMapOf(
            "fy" to "风月", "hm" to "幻梦", "dz" to "dzmm",
            "yy" to "夜语", "xc" to "香草"
        )

        val CATS = listOf(
            "💔 NTR出轨", "🏠 熟女人妻", "🧚 萝莉少女", "🔥 调教支配",
            "👨‍👩‍👧 乱伦禁忌", "🎓 校园青春", "🎮 系统游戏", "✨ 更多精选",
            "👠 都市职场", "🐺 奇幻种族", "🍈 身材特化", "🏯 古风修仙"
        )
    }
}
