package com.lianwenqi.app.data

import androidx.room.TypeConverter
import com.lianwenqi.app.data.entity.ApiType
import com.lianwenqi.app.data.entity.ChatRole

class Converters {
    @TypeConverter
    fun apiToStr(v: ApiType): String = v.name

    @TypeConverter
    fun strToApi(v: String): ApiType =
        runCatching { ApiType.valueOf(v) }.getOrDefault(ApiType.CHAT_COMPLETIONS)

    @TypeConverter
    fun roleToStr(v: ChatRole): String = v.name

    @TypeConverter
    fun strToRole(v: String): ChatRole =
        runCatching { ChatRole.valueOf(v) }.getOrDefault(ChatRole.USER)
}