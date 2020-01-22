package com.example.tamazons3tusample

import android.app.Activity
import android.app.ListActivity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.util.TimingLogger
import android.view.View
import android.widget.AdapterView.OnItemClickListener
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.SimpleAdapter
import android.widget.TextView
import androidx.annotation.RequiresApi
import com.amazonaws.mobileconnectors.s3.transferutility.*
import kotlinx.android.synthetic.main.activity_upload.*
import kotlinx.android.synthetic.main.record_item.*
import org.jetbrains.anko.toast
import java.io.File
import java.net.URISyntaxException

class UploadActivity : ListActivity() {

    //indicates that no upload is currently selected
    private val INDEX_NOT_CHECKED = -1

    //TAG for logging
    private val TAG = "UploadActivity"

    private val UPLOAD_REQUEST_CODE = 0
    private val UPLOAD_IN_BACKGROUND_REQUEST_CODE = 1

    // Primary class for managing transfers to S3
    lateinit var transferUtility: TransferUtility
    // Adapts the data about transfers to rows in UI
    lateinit var simpleAdapter: SimpleAdapter
    // List of all transfers
    private var observers : MutableList<TransferObserver> = ArrayList()

    /**
     * This map is used to provide data to the SimpleAdapter above. See the fillMap() function
     * for how it relates observers to rows in the displayed activity
     */
    var transferRecordMaps = ArrayList<HashMap<String, Any>>()

    // Which row in the UI is currently checked (if any)
    private var checkedIndex = 0

    // Reference to the utility class
    private val util = UtilJ()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_upload)

        // Initialize TransferUtility, always do this before using it
        transferUtility = util.getTransferUtility(this)
        checkedIndex = INDEX_NOT_CHECKED

        initUI()
    }

    override fun onPause() {
        super.onPause()
        // Clear the transfer listeners to prevent memory leak, or else this activity
        // won't be garbage collected
        if (observers.isNotEmpty()) {
            var observer: TransferObserver
            for (observer in observers) {
                observer.cleanTransferListener()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Get the data from any transfer's that have already happened
        initData()
    }

    /**
     * Gets all relevant transfers from the Transfer Service for populating the UI
     */
    private fun initData() {

        transferRecordMaps.clear()
        // Use the TransferUtility to get all the upload transfers
        observers = transferUtility.getTransfersWithType(TransferType.UPLOAD)
        var listener: TransferListener = UploadListener()
        var observer: TransferObserver

        for (observer in observers) {

            observer.refresh()

            // For each transfer we will create an entry in transferRecordMaps which will display
            // as a single row in the UI
            val map = java.util.HashMap<String, Any>()
            util.fillMap(map, observer, false)
            transferRecordMaps.add(map)

            // Sets listener to inProgressTransfers
            if (TransferState.WAITING == observer.state
                || TransferState.WAITING_FOR_NETWORK == observer.state
                || TransferState.IN_PROGRESS == observer.state) {

                observer.setTransferListener(listener)
            }
        }
        simpleAdapter.notifyDataSetChanged()
    }

    /**
     * This adapter takes the data in the transferRecordMaps and displays it,
     * with the keys of the map being related to the columns in the adapter
     */
    private fun initUI() {

        simpleAdapter = SimpleAdapter(this, transferRecordMaps,
            R.layout.record_item, arrayOf<String>(
                "checked", "fileName", "progress", "bytes", "state","percentage"
            ),
            intArrayOf(
                R.id.radioButton1, R.id.textFileName, R.id.progressBar1, R.id.textBytes,
                R.id.textState, R.id.textPercentage
            )
        )

        simpleAdapter.viewBinder = object : SimpleAdapter.ViewBinder {
            override fun setViewValue(
                view: View?,
                data: Any?,
                textRepresentation: String?
            ): Boolean {

                Log.d(TAG, "Data is: $data \n" +
                        "Id is: ${view?.id}")
                when (view?.id) {

                    R.id.radioButton1 -> {
                        val radio = view as RadioButton
                        radio.isChecked = data as Boolean
                        return true
                    }
                    R.id.textFileName -> {
                        val fileName = view as TextView
                        fileName.text = data as String
                        return true
                    }
                    R.id.progressBar1 -> {
                        val progress = view as ProgressBar
                        progress.progress = data as Int
                        return true
                    }
                    R.id.textBytes -> {
                        val bytes = view as TextView
                        bytes.text = data as String
                        return true
                    }
                    R.id.textState -> {
                        val state = view as TextView
                        state.text = (data as TransferState).toString()
                        return true
                    }
                    R.id.textPercentage -> {
                        val percentage = view as TextView
                        percentage.text = data as String
                        return true
                    }
                }
                return false
            }
        }
        listAdapter = simpleAdapter

        // Update checked index when an item is clicked

        // Updates checked index when an item is clicked
        listView.onItemClickListener =
            OnItemClickListener { adapterView, view, pos, id ->
                if (checkedIndex != pos) {
                    transferRecordMaps[pos]["checked"] = true
                    if (checkedIndex >= 0) {
                        transferRecordMaps[checkedIndex]["checked"] = false
                    }
                    checkedIndex = pos
                    updateButtonAvailability()
                    simpleAdapter.notifyDataSetChanged()
                }
            }

        buttonUploadFile.setOnClickListener {
            intentFile(1, 0)
        }

        buttonUploadFileInBackground.setOnClickListener {
            intentFile(2, 0)
        }

        buttonUploadImage.setOnClickListener {
            intentFile(1, 1)
        }

        buttonPause.setOnClickListener {
            // Make sure that the user has selected a transfer
            if (checkedIndex >= 0 && checkedIndex < observers.size) {

                val paused: Boolean = transferUtility.pause(observers.get(checkedIndex).id)
                /**
                 * If paused does not return true, it is likely because the user is trying
                 * to pause an upload that is not in a pause-able state
                 * (for instance it is already paused, or canceled)
                 */
                if (!paused) {
                    toast("Cannot pause transfer. You can only pause transfers in a IN_PROGRESS" +
                            "or WAITING state.")
                }
            }
        }

        buttonResume.setOnClickListener {
            // Make sure that the user has selected a transfer
            if (checkedIndex >= 0 && checkedIndex < observers.size) {

                val resumed: TransferObserver = transferUtility.resume(observers[checkedIndex].id)
                // Set a new transfer listener to the original observer.
                // This will overwrite existing listener
                observers.get(checkedIndex).setTransferListener(UploadListener())

                /**
                 * If resume returns null, it is likely because the transfer
                 * is not in a resume-able state(for instance it is already running).
                 */
                if (resumed == null) {
                    toast("Cannot resume transfer. You can only resume transfers in a PAUSED state")
                }
            }
        }

        buttonCancel.setOnClickListener {
            // Make sure that the user has selected a transfer
            if (checkedIndex >= 0 && checkedIndex < observers.size) {

                val canceled: Boolean = transferUtility.cancel(observers[checkedIndex].id)
                /**
                 * If cancel returns false, it is likely because the transfer is already canceled
                 */
                if (!canceled) {
                    toast("Cannot cancele transfer. You can only resume transfers in a PAUSED, " +
                            "WAITING, or IN_PROGRESS state.")
                }
            }
        }

        buttonDelete.setOnClickListener {
            // Make sure that the user has selected a transfer
            if (checkedIndex >= 0 && checkedIndex < observers.size) {

                transferUtility.deleteTransferRecord(observers[checkedIndex].id)
                observers.removeAt(checkedIndex)
                transferRecordMaps.removeAt(checkedIndex)
                checkedIndex = INDEX_NOT_CHECKED

                updateButtonAvailability()
                updateList()
            }
        }

        buttonPauseAll.setOnClickListener {
            transferUtility.pauseAllWithType(TransferType.UPLOAD)
        }

        buttonCancelAll.setOnClickListener {
            transferUtility.cancelAllWithType(TransferType.UPLOAD)
        }
    }

    fun updateButtonAvailability() {
        val availability: Boolean = checkedIndex >= 0

        buttonPause.isEnabled = availability
        buttonResume.isEnabled = availability
        buttonCancel.isEnabled = availability
        buttonDelete.isEnabled = availability
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {

            UPLOAD_REQUEST_CODE -> {
                if (resultCode == Activity.RESULT_OK) {
                    val uri : Uri? = data?.data
                    Log.d(TAG, "Uri is: $uri")

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        var fileName = getFileName(uri!!)
                    }

                    try {
                        val path = FilePickUtils.getPath(applicationContext, uri!!)

                        Log.d(TAG, "Path is $path")

                        measuringTimeMillis({time -> Log.d(TAG, "Uploading took $time second(s)")}) {
                            beginUpload(path.toString())
                        }

                    } catch (e: URISyntaxException) {
                        toast("Unable to get the file from the given URI. See error log for details")
                        Log.e(TAG, "Unable to upload file from the given uri", e)
                    }
                }
            }

            UPLOAD_IN_BACKGROUND_REQUEST_CODE -> {
                if (resultCode == Activity.RESULT_OK) {
                    val uri: Uri? = data?.data
                    Log.d(TAG, "Uri is: $uri")

                    try {
                        val path: String = util.getPath(uri, applicationContext)
                        beginUploadInBackground(path)
                    } catch (e: URISyntaxException) {
                        toast("Unable to get the file from the given URI. See error log for details")
                        Log.e(TAG, "Unable to upload file from the given uri", e)
                    }
                }
            }
        }
    }

    /*
     * Begins to upload the file specified by the file path
     */
    private fun beginUpload(filePath: String) {

        if (filePath != null) {
            toast("Could not find the filepath of the selected file")
        }

        val file = File(filePath)

        val observer: TransferObserver = transferUtility.upload(
            file.name,
            file
        )

        /*
         * Note that usually we set the TransferListener after initializing the transfer.
         * However, it is not required in the sample app.
         * The flow is click upload button -> start an activity for image selection
         * startActivityForResult -> onActivityResult -> beginUpload -> onResume
         * -> set listener to in progress transfers
         *
         * But here, we will try to set the TransferListener and see where it goes
         */
        observer.setTransferListener(UploadListener())
    }

    /*
     * Begins to upload the file specified by the file path in background
     */
    private fun beginUploadInBackground(filePath: String) {

        if (filePath == null) {
            toast("Could not find the filepath of the selected file")
        }

        val file = File(filePath)
        val observer: TransferObserver = transferUtility.upload(
            file.name,
            file
        )

        /*
         * Wrap the upload call from a background service to support long-running downloads.
         * Uncomment the following code in order to start an upload from the background service.
         */
        val context = applicationContext
        val intent = Intent(context, MyServiceJ::class.java)
        intent.putExtra(MyServiceJ.INTENT_KEY_NAME, file.name)
        intent.putExtra(MyServiceJ.INTENT_TRANSFER_OPERATION, MyServiceJ.TRANSFER_OPERATION_UPLOAD)
        intent.putExtra(MyServiceJ.INTENT_FILE, file)
        context.startService(intent)

        /*
         * Note that usually we set the transfer listener after initializing the
         * transfer. However it isn't required in this sample app. The flow is
         * click upload button -> start an activity for image selection
         * startActivityForResult -> onActivityResult -> beginUpload -> onResume
         * -> set listeners to in progress transfers.
         */
        observer.setTransferListener(UploadListener())
    }

    /*
     * Updates the ListView according to the observers.
     */
    fun updateList() {
        var observer: TransferObserver
        var map: java.util.HashMap<String, Any>

        for (i in observers.indices) {
            observer = observers[i]
            map = transferRecordMaps[i]
            util.fillMap(map, observer, i == checkedIndex)
        }

        simpleAdapter.notifyDataSetChanged()
    }

    private fun intentFile(requestCode: Int, btnId: Int) {
        val intent = Intent()

        if (Build.VERSION.SDK_INT >= 22) {
            /**
             * For Android Lollipop and above we use different Intent to ensure that we can
             * get the file path from the returned intent URI
             */
            intent.action = Intent.ACTION_OPEN_DOCUMENT
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true)
            intent.type = "*/*"
        } else if (btnId == 0) {
            intent.action = Intent.ACTION_GET_CONTENT
            intent.type = "*/*"
        } else if (btnId == 1) {
            intent.action = Intent.ACTION_GET_CONTENT
            intent.type = "image/*"
        }

        when (requestCode) {
            1 -> startActivityForResult(intent, UPLOAD_REQUEST_CODE)
            2 -> startActivityForResult(intent, UPLOAD_IN_BACKGROUND_REQUEST_CODE)
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

    /**
     * A TransferListener class that can listen to an upload task and be notified
     * when the status changed.
     */
    inner class UploadListener: TransferListener {

        override fun onProgressChanged(id: Int, bytesCurrent: Long, bytesTotal: Long) {

            Log.d("UploadActivity", String.format(("onProgressChanged: %d, total: %d, current: %d"),
                id, bytesTotal, bytesCurrent))
            updateList()
        }

        override fun onStateChanged(id: Int, state: TransferState?) {
            Log.d("UploadActivity", "onStateChanged: $id, $state")
            updateList()
        }

        override fun onError(id: Int, ex: Exception?) {
            Log.e("UploadActivity", "Error during upload: $id", ex)
            updateList()
        }
    }

    private inline fun <T> measuringTimeMillis(loggingFunction: (Long) -> Unit,
                                               function: () -> T): T {

        val startTime = System.currentTimeMillis()
        val result: T = function.invoke()
        loggingFunction.invoke(System.currentTimeMillis() - startTime)

        return result
    }
}
