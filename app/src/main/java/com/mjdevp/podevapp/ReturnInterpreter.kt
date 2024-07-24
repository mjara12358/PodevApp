package com.mjdevp.podevapp

interface ReturnInterpreter {

    // Retorno del reconocimiento para pruebas
    fun classify(confidence:FloatArray,maxConfidence:Int)

}