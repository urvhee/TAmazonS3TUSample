package com.example.tamazons3tusample

import android.content.Context
import android.net.Uri
import android.util.Log
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.mobile.client.AWSMobileClient
import com.amazonaws.mobile.client.Callback
import com.amazonaws.mobile.client.UserStateDetails
import com.amazonaws.mobile.config.AWSConfiguration
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility
import com.amazonaws.regions.Region
import com.amazonaws.services.s3.AmazonS3Client
import org.json.JSONException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.lang.Exception
import java.util.*
import java.util.concurrent.CountDownLatch

class Util {

    val TAG = Util::class.java.simpleName

    lateinit var sS3Client: AmazonS3Client
    lateinit var sMobileClient: AWSCredentialsProvider
    lateinit var sTransferUtility: TransferUtility

    /**
     * Gets an instance of AWSMobileClient which is
     * constructed using the given context
     *
     * @param context An Context instance
     * @return AWSMobileClient which is a credentials provider
     */
    private fun getCredProvider(context: Context): AWSCredentialsProvider {

        val latch: CountDownLatch = CountDownLatch(1)
        AWSMobileClient.getInstance().initialize(context, object:Callback<UserStateDetails> {

            override fun onResult(result: UserStateDetails?) {
                latch.countDown()
            }

            override fun onError(e: Exception?) {
                Log.e(TAG, "onError: $e")
                latch.countDown()
            }
        })

        try {
            latch.await()
            sMobileClient = AWSMobileClient.getInstance()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

        return sMobileClient
    }

    /**
     * Gets an instance of an S3 client which is constructed using the given context.
     *
     * @param context An context instance.
     * @return A default s3 client
     */
    fun getS3Client(context: Context): AmazonS3Client {

        sS3Client = AmazonS3Client(getCredProvider(context))
        try {
            val regionString = AWSConfiguration(context)
                .optJsonObject("S3TransferUtility")
                .getString("Region")
            sS3Client.setRegion(Region.getRegion(regionString))
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        return sS3Client
    }

    /**
     * Gets an instance of the TransferUtility which is constructed using the given context.
     *
     * @param context
     * @return a TransferUtility instance
     */
    fun getTransferUtility(context: Context): TransferUtility {

        sTransferUtility = TransferUtility.builder()
            .context(context)
            .s3Client(getS3Client(context))
            .awsConfiguration(AWSConfiguration(context))
            .build()

        return sTransferUtility
    }

    /**
     * Converts number of bytes into proper scale.
     *
     * @param bytes Number of bytes to be converted.
     * @return A string that represents the bytes in a proper scale
     */
    fun getBytesString(bytes: Long): String {

        val quantifiers = arrayOf<String>("KB", "MB", "GB", "TB")
        var speedNum: Double = bytes.toDouble()

        var i = 0
        while (true) {

            if (i >= quantifiers.size) {
                return ""
            }
            speedNum /= 1024
            if (speedNum < 512) {
                return String.format("%.2f", speedNum) + " " + quantifiers[i]
            }
            i++
        }
    }

    /**
     * Copies the data from the passed in Uri, to a new file for use with the Transfer Service
     *
     * @param context
     * @param uri
     * @return
     * @throws IOException
     */
    @Throws(IOException::class)
    fun copyContentUriToFile(context: Context, uri: Uri): File {

        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        val copiedData: File = File(context.getDir("SampleImagesDir", Context.MODE_PRIVATE),
            UUID.randomUUID().toString())
        copiedData.createNewFile()

        val fos: FileOutputStream = FileOutputStream(copiedData)
        val buf: ByteArray = ByteArray(2046)

        var read = -1
        read = inputStream?.read(buf)!!

        while (read != -1) {
            fos.write(buf, 0, read)
        }

        fos.close()
        fos.flush()

        return copiedData
    }

    /**
     * Fills in the map with information in the observer so that it can be used
     * with a SimpleAdapter to populate the UI
     */
    fun fillMap(map: HashMap<String, Any>, observer: TransferObserver, isChecked: Boolean) {

        val progress: Int = ((observer.bytesTransferred.toDouble() * 100 / observer
            .bytesTotal)).toInt()

        map.put("id", observer.id)
        map.put("checked", isChecked)
        map.put("fileName", observer.absoluteFilePath)
        map.put("progress", progress)
        map.put("bytes",
            getBytesString(observer.bytesTransferred) + "/"
        + getBytesString(observer.bytesTotal))
        map.put("state", observer.state)
        map.put("percentage", "$progress %")
    }
}