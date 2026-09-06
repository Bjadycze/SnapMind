package com.app.snapmind.domain.service

import com.app.snapmind.domain.model.CaptureSource

/**
 * The note-taking affordance shown immediately after a capture.
 *
 * Task 0 settled the implementation: notification only. The interface remains as a seam for a
 * future overlay or Quick Settings tile (spec.md 11.1), but v1 binds exactly one implementation.
 */
interface QuickCapturePresenter {
    suspend fun present(itemId: Long, imageUri: String?, source: CaptureSource)
    fun dismiss(itemId: Long)
}
