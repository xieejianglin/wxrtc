package com.wx.rtc.rtc

import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi


internal class WXScreenCaptureAssistantService : Service() {
    companion object {
        private const val TAG: String = "WXScreenCaptureAssistantService"
        private const val CHANNEL_ID: String = "WXScreenCapture"
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        return START_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val builder = Notification.Builder(this.applicationContext, CHANNEL_ID) //获取一个Notification构造器

        builder
//            .setLargeIcon(
//                BitmapFactory.decodeResource(
//                    this.getResources(), R.mipmap.
//                )
//            ) // 设置下拉列表中的图标(大图标)
            .setContentTitle("共享屏幕中") // 设置上下文内容
            .setContentText("共享屏幕中") // 设置上下文内容
            .setSmallIcon(R.drawable.ic_lock_idle_alarm)
            .setWhen(System.currentTimeMillis()) // 设置该通知发生的时间

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID, "wx_notification_name", NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)

        val notification = builder.build() // 获取构建好的Notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1101, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(1101, notification)
        }
    }

}