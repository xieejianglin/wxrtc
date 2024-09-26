package com.wx.rtc.utils

import android.app.Activity
import android.os.Build
import android.util.Log
import java.util.LinkedList

internal object ActivityUtils {
    @JvmStatic
    fun getTopActivity(): Activity? {
        val activityList = getActivityList()
        for (activity in activityList) {
            if (!isActivityAlive(activity)) {
                continue
            }
            return activity
        }
        return null
    }

    @JvmStatic
    fun isActivityAlive(activity: Activity?): Boolean {
        return (activity != null && !activity.isFinishing && (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1 || !activity.isDestroyed))
    }

    private fun getActivityList(): List<Activity> {
        val reflectActivities = getActivitiesByReflect()
        return LinkedList(reflectActivities)
    }

    private fun getActivitiesByReflect(): List<Activity>  {
        val list = LinkedList<Activity>()
        var topActivity: Activity? = null
        try {
            val activityThread = getActivityThread() ?: return list
            val mActivitiesField = activityThread.javaClass.getDeclaredField("mActivities")
            mActivitiesField.isAccessible = true
            val mActivities =
                mActivitiesField[activityThread] as? Map<*, *> ?: return list
            val binder_activityClientRecord_map = mActivities as Map<Any, Any>
            for (activityRecord in binder_activityClientRecord_map.values) {
                val activityClientRecordClass: Class<*> = activityRecord.javaClass
                val activityField = activityClientRecordClass.getDeclaredField("activity")
                activityField.isAccessible = true
                val activity = activityField[activityRecord] as Activity
                if (topActivity == null) {
                    val pausedField = activityClientRecordClass.getDeclaredField("paused")
                    pausedField.isAccessible = true
                    if (!pausedField.getBoolean(activityRecord)) {
                        topActivity = activity
                    } else {
                        list.addFirst(activity)
                    }
                } else {
                    list.addFirst(activity)
                }
            }
        } catch (e: Exception) {
            Log.e("UtilsActivityLifecycle", "getActivitiesByReflect: " + e.message);
        }
        if (topActivity != null) {
            list.addFirst(topActivity)
        }
        return list
    }

    private fun getActivityThread(): Any? {
        val activityThread = getActivityThreadInActivityThreadStaticField()
        if (activityThread != null) return activityThread
        return getActivityThreadInActivityThreadStaticMethod()
    }

    private fun getActivityThreadInActivityThreadStaticField(): Any? {
        try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val sCurrentActivityThreadField =
                activityThreadClass.getDeclaredField("sCurrentActivityThread")
            sCurrentActivityThreadField.isAccessible = true
            return sCurrentActivityThreadField[null]
        } catch (e: Exception) {
            Log.e(
                "UtilsActivityLifecycle",
                "getActivityThreadInActivityThreadStaticField: " + e.message
            )
            return null
        }
    }

    private fun getActivityThreadInActivityThreadStaticMethod(): Any? {
        try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            return activityThreadClass.getMethod("currentActivityThread").invoke(null)
        } catch (e: Exception) {
            Log.e(
                "UtilsActivityLifecycle",
                "getActivityThreadInActivityThreadStaticMethod: " + e.message
            )
            return null
        }
    }
}