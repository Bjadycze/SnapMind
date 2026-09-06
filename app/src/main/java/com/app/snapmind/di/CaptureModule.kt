package com.app.snapmind.di

import com.app.snapmind.data.ocr.MlKitOcrAnalyzer
import com.app.snapmind.domain.service.OcrAnalyzer
import com.app.snapmind.domain.service.QuickCapturePresenter
import com.app.snapmind.service.capture.NotificationQuickCapture
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CaptureModule {

    /** Notification only. Overlay was cut after Task 0 -- see spec.md 11.1. */
    @Binds
    @Singleton
    abstract fun bindQuickCapturePresenter(
        impl: NotificationQuickCapture
    ): QuickCapturePresenter

    @Binds
    @Singleton
    abstract fun bindOcrAnalyzer(impl: MlKitOcrAnalyzer): OcrAnalyzer
}
