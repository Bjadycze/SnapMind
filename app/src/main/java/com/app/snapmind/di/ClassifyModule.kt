package com.app.snapmind.di

import com.app.snapmind.domain.classify.ChainedContentClassifier
import com.app.snapmind.domain.classify.ContentClassifier
import com.app.snapmind.domain.classify.Tier0RegexClassifier
import com.app.snapmind.domain.classify.Tier1KeywordClassifier
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ClassifyModule {

    @Provides
    @Singleton
    fun provideTier0(): Tier0RegexClassifier = Tier0RegexClassifier()

    @Provides
    @Singleton
    fun provideTier1(): Tier1KeywordClassifier = Tier1KeywordClassifier()

    /** Zbytek aplikace zná jen tohle rozhraní, nikdy konkrétní tier. */
    @Provides
    @Singleton
    fun provideContentClassifier(
        tier0: Tier0RegexClassifier,
        tier1: Tier1KeywordClassifier
    ): ContentClassifier = ChainedContentClassifier(tier0, tier1)
}
