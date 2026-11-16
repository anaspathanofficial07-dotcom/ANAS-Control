package com.anascontrol.child

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class CallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_NEW_OUTGOING_CALL) {
            val phoneNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
            
            // Apna Secret Code yahan define karein
            if (phoneNumber == "*123456#") {
                Log.d("ANAS_CONTROL", "Secret code matched! Launching hidden app...")
                
                // Call ko network par (sim card se) dial hone se block karein
                resultData = null 
                
                // App ko background se open karein
                val appIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                context.startActivity(appIntent)
            }
        }
    }
}
