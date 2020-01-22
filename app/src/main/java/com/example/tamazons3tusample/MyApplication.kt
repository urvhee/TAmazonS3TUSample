package com.example.tamazons3tusample

import android.app.Application
import android.content.Intent
import com.amazonaws.mobileconnectors.s3.transferutility.TransferService

class MyApplication: Application() {

    override fun onCreate() {
        super.onCreate()

        // Network Service
        applicationContext.startService(Intent (applicationContext, TransferService::class.java))
    }
}