package com.example.tamazons3tusample

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility
import java.io.File
import java.lang.Exception

class MyService: Service() {

    private lateinit var transferUtility: TransferUtility

    val INTENT_KEY_NAME = "key"
    val INTENT_FILE = "file"
    val INTENT_TRANSFER_OPERATION = "transferOperation"

    val TRANSFER_OPERATION_UPLOAD = "upload"
    val TRANSFER_OPERATION_DOWNLOAD = "download"

    var TAG = MyService::class.simpleName

    override fun onCreate() {
        super.onCreate()

        val util: Util = Util()
        transferUtility = util.getTransferUtility(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        val key = intent?.getStringExtra(INTENT_KEY_NAME)
        val file: File = intent?.getSerializableExtra(INTENT_FILE) as File
        val transferOperation = intent.getStringExtra(INTENT_TRANSFER_OPERATION)
        val transferObserver: TransferObserver

        when (transferOperation) {
            TRANSFER_OPERATION_UPLOAD -> {
                Log.d(TAG, "Uploading: $key")
                transferObserver = transferUtility.upload(key, file)
                transferObserver.setTransferListener(UploadListener())
            }

            TRANSFER_OPERATION_DOWNLOAD -> {
                Log.d(TAG, "Downloading: $key")
                transferObserver = transferUtility.download(key, file)
                transferObserver.setTransferListener(DownloadListener())
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private class DownloadListener: TransferListener {

        var notifyDownloadActivityNeeded = true

        override fun onProgressChanged(id: Int, bytesCurrent: Long, bytesTotal: Long) {
            Log.d("MyService", String.format("onProgressChanged: %d, total: %d, current %d",
                id, bytesTotal, bytesCurrent))
            if (notifyDownloadActivityNeeded) {
                // DownloadActivity.initData()
                notifyDownloadActivityNeeded = false
            }
        }

        override fun onStateChanged(id: Int, state: TransferState?) {
            Log.d("MyService", "onStateChanged: $id $state")
            if (notifyDownloadActivityNeeded) {
                // DownloadActivity.initData()
                notifyDownloadActivityNeeded = false
            }
        }

        override fun onError(id: Int, ex: Exception?) {
            Log.e("MyService", "OnError: $id", ex)
            if (notifyDownloadActivityNeeded) {
                // DownloadActivity.initData()
                notifyDownloadActivityNeeded = false
            }
        }
    }

    private class UploadListener: TransferListener {

        var notifyUploadActivityNeeded = true

        override fun onProgressChanged(id: Int, bytesCurrent: Long, bytesTotal: Long) {
            Log.d("MyService", String.format("onProgressChanged: %d, total: %d, current: %d",
                id, bytesTotal, bytesCurrent))
            if (notifyUploadActivityNeeded) {
                // DownloadActivity.initData()
                notifyUploadActivityNeeded = false
            }
        }

        override fun onStateChanged(id: Int, state: TransferState?) {
            Log.d("MyService", "onStateChanged: $id $state")
            if (notifyUploadActivityNeeded) {
                // DownloadActivity.initData()
                notifyUploadActivityNeeded = false
            }
        }

        override fun onError(id: Int, ex: Exception?) {
            Log.e("MyService", "OnError: $id", ex)
            if (notifyUploadActivityNeeded) {
                // DownloadActivity.initData()
                notifyUploadActivityNeeded = false
            }
        }

    }
}