package com.example.tamazons3tusample

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.amazonaws.mobileconnectors.s3.transfermanager.Upload
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.startActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initUI()
    }

    private fun initUI() {

        buttonDownloadMain.setOnClickListener {
            //do something
        }

        buttonUploadMain.setOnClickListener {
            startActivity<UploadActivity>()
        }
    }
}
