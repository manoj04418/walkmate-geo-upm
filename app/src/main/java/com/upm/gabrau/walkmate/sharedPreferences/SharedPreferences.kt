package com.upm.gabrau.walkmate.sharedPreferences

import android.app.Activity
import android.content.Context

object SharedPreferences {
    var userId: String? = null

    fun getUserId(activity: Activity) {
        val sharedPref = activity.getSharedPreferences("USER_ID", Context.MODE_PRIVATE)
        sharedPref.getString("USER_ID", "")?.let { userId = it }
    }

    fun putUserId(activity: Activity, code: String) {
        val sharedPref = activity.getSharedPreferences("USER_ID", Context.MODE_PRIVATE) ?: return
        with (sharedPref.edit()) {
            putString("USER_ID", code)
            commit()
        }
    }
}
