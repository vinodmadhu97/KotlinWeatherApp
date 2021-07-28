package com.example.weatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.weatherapp.models.WeatherModel
import com.example.weatherapp.network.WeatherService
import com.google.android.gms.location.*
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kotlinx.android.synthetic.main.activity_main.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var mFusedLocationClient : FusedLocationProviderClient
    private var mProgressDialog : Dialog? = null
    private lateinit var mSharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)

        if (!isLocationEnabled()){
            Toast.makeText(this,"please on the GPS",Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }else{
            Dexter.withActivity(this).withPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ).withListener(object:MultiplePermissionsListener{
                override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                    if (report!!.areAllPermissionsGranted()){
                        requestLocationData()
                    }
                    if (report.isAnyPermissionPermanentlyDenied){
                        Toast.makeText(this@MainActivity,"Permission is permanently denied please give the permission",Toast.LENGTH_LONG).show()
                    }
                }

                override fun onPermissionRationaleShouldBeShown(
                    permissions: MutableList<PermissionRequest>?,
                    token: PermissionToken?
                ) {
                    showRationalDialogPermission()
                }

            }).onSameThread().check()
        }
    }

    private fun showCustomProgressDialog(){
        mProgressDialog = Dialog(this)
        mProgressDialog!!.setContentView(R.layout.custom_progress_dialog)
        mProgressDialog!!.show()
    }

    private fun dismissCustomProgressDialog(){
        if (mProgressDialog != null){
            mProgressDialog!!.dismiss()
        }
    }


    private fun isLocationEnabled():Boolean{
        val locationManger:LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (locationManger.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManger.isProviderEnabled(LocationManager.NETWORK_PROVIDER)){
            return true
        }
        return false
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData(){
        val locationRequest = LocationRequest()
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        mFusedLocationClient.requestLocationUpdates(locationRequest,mLocationCallBack, Looper.myLooper())
    }

    val mLocationCallBack = object : LocationCallback(){
        override fun onLocationResult(p0: LocationResult?) {
            super.onLocationResult(p0)
            val mLastLocation : Location = p0!!.lastLocation
            val latitude = mLastLocation.latitude
            val longitude = mLastLocation.longitude

            Log.i("data","lat $latitude")
            Log.i("data","longi $longitude")
            getLocationWeatherDetails(latitude,longitude)
        }
    }


    private fun getLocationWeatherDetails(latitude:Double, longitude:Double){
        if (Constants.isNetworkAvailable(this)){
            val retrofit:Retrofit = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val service : WeatherService = retrofit.create(WeatherService::class.java)

            val listCall : Call<WeatherModel> = service.getWeather(latitude,longitude,Constants.METRIC_UNIT,Constants.APP_ID)
            showCustomProgressDialog()
            listCall.enqueue(object : Callback<WeatherModel>{
                override fun onResponse(
                    call: Call<WeatherModel>,
                    response: Response<WeatherModel>
                ) {
                    if (response.isSuccessful){
                        val weatherList : WeatherModel? = response.body()
                        Log.i("data","$weatherList")


                        val weatherresponseJsonString = Gson().toJson(weatherList)
                        var editor = mSharedPreferences.edit()
                        editor.putString(Constants.WEATHER_DATA,weatherresponseJsonString)
                        editor.apply()

                        setupUI(weatherList)
                        dismissCustomProgressDialog()

                    }else{
                        Log.i("data","${response.code()}")
                    }
                }

                override fun onFailure(call: Call<WeatherModel>, t: Throwable) {
                    Log.e("data","${t.message.toString()}")
                    dismissCustomProgressDialog()
                }

            })
        }else{
            Toast.makeText(this,"internet connection failed",Toast.LENGTH_LONG).show()
        }
    }


    private fun showRationalDialogPermission(){
        AlertDialog.Builder(this).setMessage("Turned off GPS permission required for this feature")
            .setPositiveButton("GO TO SETTING"){
                _,_ -> try{
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package",packageName,null)
                intent.data = uri
                startActivity(intent)
                }catch (e:ActivityNotFoundException){
                    e.printStackTrace()
                }
            }.setNegativeButton("cancel"){dialog,_ ->
                dialog.dismiss()
            }.show()
    }

    private fun setupUI(weatherModel: WeatherModel?){

        val sharedPreferencesweatherData = mSharedPreferences.getString(Constants.WEATHER_DATA,"")

        if (!sharedPreferencesweatherData.isNullOrEmpty()){
            val weatherModel = Gson().fromJson(sharedPreferencesweatherData,WeatherModel::class.java)
            for (i in weatherModel.weather.indices){
                tv_weather.text = weatherModel.weather[i].main
                tv_weather_description.text = weatherModel.weather[i].description
                tv_temp.text = weatherModel.main.temp.toString() + "°C"
                tv_humidity.text = weatherModel.main.humidity.toString() + " per cents"
                tv_temp_max.text = weatherModel.main.temp_max.toString() + "°C"
                tv_temp_min.text = weatherModel.main.temp_min.toString() + "°C"
                tv_wind_speed.text = weatherModel.wind.speed.toString() + " Kmh"
                tv_district_name.text = weatherModel.name
                tv_country_name.text = weatherModel.sys.country

                tv_sunrise_time.text = unixTime(weatherModel.sys.sunrise)
                tv_sunset_time.text = unixTime(weatherModel.sys.sunset)

                when(weatherModel.weather[i].icon){
                    "01d" -> iv_weather.setImageResource(R.drawable.sunny)
                    "02d" -> iv_weather.setImageResource(R.drawable.cloud)
                    "03d" -> iv_weather.setImageResource(R.drawable.cloud)
                    "04d" -> iv_weather.setImageResource(R.drawable.cloud)
                    "04n" -> iv_weather.setImageResource(R.drawable.cloud)
                    "10d" -> iv_weather.setImageResource(R.drawable.rain)
                    "11d" -> iv_weather.setImageResource(R.drawable.storm)
                    "13d" -> iv_weather.setImageResource(R.drawable.snowflake)
                    "01n" -> iv_weather.setImageResource(R.drawable.cloud)
                    "02n" -> iv_weather.setImageResource(R.drawable.cloud)
                    "03n" -> iv_weather.setImageResource(R.drawable.cloud)
                    "010n" -> iv_weather.setImageResource(R.drawable.cloud)
                    "011n" -> iv_weather.setImageResource(R.drawable.rain)
                    "013n" -> iv_weather.setImageResource(R.drawable.snowflake)

                }

            }
        }



    }

    private fun unixTime(timex :Long):String{
        val date = Date(timex*1000L)
        val sdf = SimpleDateFormat("HH:mm",Locale.UK)
        sdf.timeZone = TimeZone.getDefault()

        return sdf.format(date)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu,menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            R.id.main_menu -> {
                requestLocationData()
                true
            }else -> super.onOptionsItemSelected(item)

        }

    }
}