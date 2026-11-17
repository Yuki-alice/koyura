package app.koyura.data.history

import app.cash.sqldelight.db.SqlDriver

/**
 * 历史可见性表的幂等保障（与 `5.sqm` 同 DDL）。
 *
 * 新库由 `Schema.create` 落地；已存在的老库（扩展表时代、阅读时代、书架时代）
 * 走结构探测（沿用 `ensureLibrarySchema` 范式），此处 `IF NOT EXISTS` 补齐。
 * 隐藏标记独立于 `reading_progress`：删除/清空只插标记不删进度行，
 * 重新阅读保存时删标记恢复可见，续读位置永不因历史操作丢失。
 */
internal fun ensureHistoryVisibilitySchema(driver: SqlDriver) {
    driver.execute(null, HistoryHiddenDdl, 0, null)
}

private const val HistoryHiddenDdl = """
CREATE TABLE IF NOT EXISTS history_hidden(
  source_key TEXT NOT NULL,
  work_id TEXT NOT NULL,
  PRIMARY KEY (source_key, work_id)
)
"""
