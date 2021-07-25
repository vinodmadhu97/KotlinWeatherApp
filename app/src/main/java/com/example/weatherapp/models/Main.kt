package com.example.weatherapp.models

import java.io.Serializable

data class Main (
    val temp : Double,
    val eels_like : Double,
    val temp_min : Double,
    val temp_max : Double,
    val pressure : Int,
    val humidity : Int,
    val sea_level : Int,
    val grnd_level : Int
        ) : Serializable