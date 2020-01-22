package com.example.tamazons3tutorial

import android.app.Activity
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.graphics.PathUtils
import com.amazonaws.auth.CognitoCachingCredentialsProvider
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3Client
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.toast
import java.io.File
import java.lang.Exception
import java.net.URI
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity() {

    val bucket = "inr-test-kompas"
    var fileName = ""

    lateinit var listing: List<String>
    lateinit var s3Client: AmazonS3
    lateinit var transferUtility: TransferUtility

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val day = SimpleDateFormat("dd").format(Date())
        val month = SimpleDateFormat("M").format(Date())
        val year = SimpleDateFormat("yyyy").format(Date())

        Log.v("MADate", "Date is $day, Month is $month, Year is $year")
        
        // Callback method to call credentialsProvider method
        s3CredentialProvider()

        // Callback method to call the setTransferUtility method
        setTransferUtility()

        // Button action
        btn_file.setOnClickListener {
            val intent = Intent()
                .setType("*/*")
                .setAction(Intent.ACTION_GET_CONTENT)

            startActivityForResult(Intent.createChooser(intent, "Select a file"), 101)
        }

        uploadSd3.setOnClickListener {
            uploadFileToS3()
        }

        downloadSd3.setOnClickListener {
            downloadFileFromS3()
        }

        fetchFileToS3.setOnClickListener {
            fetchFileFromS3()
        }
    }

    fun s3CredentialProvider() {
        // Init the AWS Credential
            val c3p = CognitoCachingCredentialsProvider(
            applicationContext, //app context
            "ap-southeast-1:dcae8665-fddd-4658-bade-0a70b06039ce", //identity pool ID
            Regions.AP_SOUTHEAST_1 //region
        )
        createAmazonS3Client(c3p)
    }

    /**
     * Create the Amazon S3 Client constructor and pass the credentialProvider
     * @param credentialProvider
     */
    fun createAmazonS3Client(c3p: CognitoCachingCredentialsProvider) {
        // Create an S3 client
        s3Client = AmazonS3Client(c3p)

        // Set region of S3 bucket
        s3Client.setRegion(Region.getRegion(Regions.AP_SOUTHEAST_1))
    }

    fun setTransferUtility() {

        transferUtility = TransferUtility(s3Client, applicationContext)

    }

    /**
     * This method is used to upload the file to S3 by using TransferUtility class
     * @param view
     */
    fun uploadFileToS3() {

        if (tv_file.text == "File chosen: ") {
            toast("Could not process, you must pick a file first")
        } else {
            val transferObserver = transferUtility.upload(
                bucket, // the bucket to upload to
                fileName, //the key for uploaded object
                File(tv_file.text.toString()) // the file where the data to upload exists
            )

            transferObserverListener(transferObserver)
        }
    }

    /**
     * This method is used to Download the file to S3 by using transferUtility class
     * @param view
     */
    fun downloadFileFromS3() {

        if (tv_file.text == "File chosen: ") {
            toast("Could not process, you must pick a file first")
        } else {
            val transferObserver = transferUtility.download(
                bucket, // the bucket to upload to
                fileName, //the key for uploaded object ("filename.png")
                File(tv_file.text.toString()) // the file where the data to upload exists
            )
            transferObserverListener(transferObserver)
        }
    }

    fun fetchFileFromS3() {

        val thread = Thread(object: Runnable {

            override fun run() {
                try {
                    Looper.prepare()
                    listing = getObjectNamesForBucket(bucket, s3Client)

                    for (element in listing) {
                        toast(element)
                    }

                    Looper.loop()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Log.e("TAG", "Exception found while listing " + e)
                }
            }
        })

        thread.start()
    }

    /**
     * @desc This method is used to return list of files name from S3 bucket
     * @param bucket
     * @param s3Client
     * @return object with list of files
     */
    fun getObjectNamesForBucket(bucket: String, s3Client: AmazonS3): List<String> {

        var objects = s3Client.listObjects(bucket)
        val objectNames = ArrayList<String>(objects.objectSummaries.size)
        var iterator = objects.objectSummaries.iterator()

        while (iterator.hasNext()) {
            objectNames.add(iterator.next().key)
        }
        while (objects.isTruncated) {
            objects = s3Client.listNextBatchOfObjects(objects)
            iterator = objects.objectSummaries.iterator()
            while (iterator.hasNext()) {
                objectNames.add(iterator.next().key)
            }
        }

        return objectNames
    }

    /**
     * This is listener method of the TransferObserver
     * Within this listener method, we get status of uploading and downloading file,
     * to display percentage of the part of file to be uploaded or downloaded to S3
     * It displays an error, when there is a problem in uploading or downloading file
     * @param transferObserver
     */
    private fun transferObserverListener(transferObserver: TransferObserver) {

        transferObserver.setTransferListener(object: TransferListener{
            override fun onStateChanged(id: Int, state: TransferState?) {
                toast("State change to: $state")
            }

            override fun onProgressChanged(id: Int, bytesCurrent: Long, bytesTotal: Long) {
                val percentage: Int = (bytesCurrent/bytesTotal * 100).toInt()
                toast("Progress in $percentage%")
            }

            override fun onError(id: Int, ex: Exception?) {
                Log.e("TAG Error", "Error is: $ex")
            }

        })
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 101 && resultCode == Activity.RESULT_OK) {
            val selectedFile = data?.data // The uri within the location of file

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                fileName = getFileName(selectedFile!!)
            }

            //tv_file.text = selectedFile?.path
            val file = FilePickUtils.getPath(applicationContext, selectedFile!!)

            tv_file.text = file.toString()
            val fileText = tv_file.text.toString()

            val uuid = UUID.randomUUID()
            tv_uuid.text = "UUID is: $uuid"

            Log.v("TAG File", fileText)
            Log.v("TAG File Name", fileName)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun getFileName(uri: Uri): String {
        var result: String = ""

        if (uri.scheme.equals("content")) {
            val cursor = contentResolver.query(uri, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path!!
            val cut = result.lastIndexOf('/')
            if (cut != -1) {
                result = result.substring(cut + 1)
            }
        }
        return result
    }
}
