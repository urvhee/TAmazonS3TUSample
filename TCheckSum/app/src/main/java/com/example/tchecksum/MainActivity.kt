package com.example.tchecksum

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Message
import java.math.BigInteger
import java.security.MessageDigest

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val input = "this is a dummy string"
        val md5 = input.md5()
        println("computed md5 value is $md5")
    }

    fun String.md5(): String {
        val md = MessageDigest.getInstance("MD5")
        return BigInteger(1, md.digest(toByteArray())).toString(16).padStart(32, '0')
    }
}
