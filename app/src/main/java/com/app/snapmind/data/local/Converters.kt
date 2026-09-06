package com.app.snapmind.data.local

import androidx.room.TypeConverter
import com.app.snapmind.domain.model.CaptureSource
import com.app.snapmind.domain.model.Resolution

class Converters {

    /** Stored by name. Ordinals break the moment a value is inserted into the enum. */
    @TypeConverter
    fun fromCaptureSource(value: CaptureSource): String = value.name

    @TypeConverter
    fun toCaptureSource(value: String): CaptureSource =
        runCatching { CaptureSource.valueOf(value) }.getOrDefault(CaptureSource.SCREENSHOT)

    /** Null means unresolved -- must round-trip as NULL, not a sentinel string. */
    @TypeConverter
    fun fromResolution(value: Resolution?): String? = value?.name

    @TypeConverter
    fun toResolution(value: String?): Resolution? =
        value?.let { runCatching { Resolution.valueOf(it) }.getOrNull() }
}
