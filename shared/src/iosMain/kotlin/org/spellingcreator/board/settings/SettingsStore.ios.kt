package org.spellingcreator.board.settings

import platform.Foundation.NSUserDefaults

actual class SettingsStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) {
    actual fun load(): BoardSettings = SettingsCodec.decode(defaults.stringForKey(SettingsCodec.KEY))

    actual fun save(settings: BoardSettings) {
        defaults.setObject(SettingsCodec.encode(settings), forKey = SettingsCodec.KEY)
    }
}
