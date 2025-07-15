package com.wx.rtc.socket

import com.wx.rtc.utils.JsonUtils.JSON
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.pingInterval
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialFormat
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

object NetworkUtils {
    val httpClient = HttpClient(){
        install(ContentNegotiation) {
            json(JSON)
        }
        install(WebSockets) {
//            contentConverter = KotlinxWebsocketSerializationConverter(Json)
            pingInterval = 10.seconds
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60000
            connectTimeoutMillis = 30000
            socketTimeoutMillis = 30000
        }
    }
}