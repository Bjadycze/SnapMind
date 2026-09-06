package com.app.snapmind.domain.model

/**
 * Where a captured item came from.
 *
 * Persisted by NAME, never by ordinal -- inserting a value later would silently reassign
 * every stored row. See Converters.
 */
enum class CaptureSource {
    SCREENSHOT,
    SHARE_SHEET
}
