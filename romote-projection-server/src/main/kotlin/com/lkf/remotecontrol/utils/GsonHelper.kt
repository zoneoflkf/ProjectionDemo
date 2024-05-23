package com.lkf.remotecontrol.utils

import com.google.gson.Gson


object GsonHelper {
    val GSON: Gson by lazy { Gson() }

    fun <T> jsonToList(json: String?, clazz: Class<Array<T>>): List<T> {
        if (json.isNullOrEmpty()) {
            return emptyList()
        }
        try {
            val array = GSON.fromJson(json, clazz)
            if (array.isEmpty()) {
                return emptyList()
            }
            return listOf(*array)
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }
}