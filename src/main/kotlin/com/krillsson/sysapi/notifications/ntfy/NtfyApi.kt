package com.krillsson.sysapi.notifications.ntfy

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

interface NtfyApi {
    /**
     * Send a notification to a topic
     */
    @POST("{topic}")
    fun sendNotification(
        @Path("topic") topic: String,
        @Body notification: Notification
    ): Call<ResponseBody>

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class Notification(
        val title: String? = null,
        val message: String,
        val priority: Int? = null, // 1-5, 1=min, 3=default, 5=max
        val tags: List<String>? = null,
        @JsonProperty("click")
        val clickUrl: String? = null,
        @JsonProperty("icon")
        val iconUrl: String? = null,
        @JsonProperty("actions")
        val actions: List<Action>? = null
    ) {
        data class Action(
            val action: String, // "view", "http", etc.
            val label: String,
            val url: String? = null,
            val method: String? = null, // "GET", "POST", etc.
            val body: String? = null
        )
    }
}


