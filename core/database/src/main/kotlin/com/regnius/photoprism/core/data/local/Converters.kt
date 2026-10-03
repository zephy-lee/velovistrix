package com.regnius.photoprism.core.data.local

import androidx.room.TypeConverter
import com.regnius.photoprism.core.model.MediaFile
import kotlinx.serialization.json.Json

/** [PhotoEntity.filesJson][com.regnius.photoprism.core.data.local.entity.PhotoEntity] 용. */
class MediaFileListConverter {
    @TypeConverter
    fun fromJson(json: String): List<MediaFile> = Json.decodeFromString(json)

    @TypeConverter
    fun toJson(files: List<MediaFile>): String = Json.encodeToString(files)
}
