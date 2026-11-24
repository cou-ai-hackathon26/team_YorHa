package com.medidiary.app.ai

object NativeBridge {
    init {
        System.loadLibrary("medidiary_native")
    }

    external fun testJni(): String
}
