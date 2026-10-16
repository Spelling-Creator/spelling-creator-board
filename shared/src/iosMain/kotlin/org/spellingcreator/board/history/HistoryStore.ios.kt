package org.spellingcreator.board.history

import platform.Foundation.NSDate
import platform.Foundation.NSUserDefaults
import platform.Foundation.timeIntervalSince1970

actual class PreferencesHistoryStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : HistoryStore {
    actual override fun load(): List<HistoryEntry> = HistoryCodec.decode(defaults.stringForKey(HistoryCodec.KEY))

    actual override fun save(entries: List<HistoryEntry>) {
        defaults.setObject(HistoryCodec.encode(entries), forKey = HistoryCodec.KEY)
    }
}

actual fun epochMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()
