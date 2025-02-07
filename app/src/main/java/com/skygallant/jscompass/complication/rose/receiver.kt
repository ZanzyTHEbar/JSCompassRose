package com.skygallant.jscompass.complication.rose

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.SensorManager
import android.location.Location
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.datastore.preferences.core.edit
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.skygallant.jscompass.complication.rose.data.HEADING_KEY
import com.skygallant.jscompass.complication.rose.data.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class Receiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private fun piFun(x: Float): Float {
        val magic: Float = 180f / kotlin.math.PI.toFloat()
        return x * magic
    }
    private fun doCompass(gotCon: Context): Int {
        var heading: Float
        val rotationMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)

        SensorManager.getRotationMatrix(rotationMatrix, null, Service.accelerometerReading, Service.magnetometerReading)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)


        heading = orientationAngles[0]
        Log.d(TAG, "calculate: $heading")
        heading = piFun(heading)
        Log.d(TAG, "convert: $heading")
        //heading = if (heading > 0) heading + 180 else -heading
        //heading = if (heading < 0) {-heading} else {360 - heading}
        heading = (360 - heading) % 360
        Log.d(TAG, "map: $heading")

        /*
        heading = CompassHelper.calculateHeading(accelerometerReading, magnetometerReading)
        Log.d(TAG, "calculate: $heading")
        heading = CompassHelper.convertRadToDeg(heading)
        Log.d(TAG, "convert: $heading")
        heading = CompassHelper.map180to360(heading)
        Log.d(TAG, "map: $heading")
         */

        if (checkPermission(gotCon)) {
            if (location != null) {
                val geoField = GeomagneticField(
                    location!!.latitude.toFloat(),
                    location!!.longitude.toFloat(),
                    location!!.altitude.toFloat(),
                    System.currentTimeMillis()
                )
                heading += geoField.declination
                if (heading > 360f) {
                    heading -= 360f
                }
                Log.d(TAG, "mag: ${geoField.declination}")
            } else {
                val text = "Rose Pos"
                val duration = Toast.LENGTH_SHORT
                val toast = Toast.makeText(gotCon, text, duration)
                toast.show()
            }
        } else {
            val text = "Rose Perms"
            val duration = Toast.LENGTH_SHORT
            val toast = Toast.makeText(gotCon, text, duration)
            toast.show()
        }


        Log.d(TAG, "heading: $heading")
        return heading.toInt()
    }


    override fun onReceive(context: Context, intent: Intent) {

        // Retrieve complication values from Intent's extras.
        val extras = intent.extras ?: return
        val dataSource = extras.getParcelable<ComponentName>(EXTRA_DATA_SOURCE_COMPONENT) ?: return
        val complicationId = extras.getInt(EXTRA_COMPLICATION_ID)

        // Required when using async code in onReceive().
        val result = goAsync()

        // Launches coroutine to update the DataStore counter value.
        scope.launch {
            try {
                context.dataStore.edit { preferences ->



                    preferences[HEADING_KEY] = doCompass(context)



                }

                // Request an update for the complication that has just been tapped, that is,
                // the system call onComplicationUpdate on the specified complication data
                // source.
                val complicationDataSourceUpdateRequester =
                    ComplicationDataSourceUpdateRequester.create(
                        context = context,
                        complicationDataSourceComponent = dataSource
                    )
                complicationDataSourceUpdateRequester.requestUpdate(complicationId)



            } finally {
                // Always call finish, even if cancelled
                result.finish()
            }
        }
    }

    companion object {
        var location: Location? = null
        fun checkPermission(thisContext: Context): Boolean {
            return ActivityCompat.checkSelfPermission(
                thisContext,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }

        private const val EXTRA_DATA_SOURCE_COMPONENT =
            "com.skygallant.jscompass.complication.rose.action.DATA_SOURCE_COMPONENT"
        private const val EXTRA_COMPLICATION_ID =
            "com.skygallant.jscompass.complication.rose.action.COMPLICATION_ID"

        /**
         * Returns a pending intent, suitable for use as a tap intent, that causes a complication to be
         * toggled and updated.
         */
        fun getToggleIntent(
            context: Context,
            dataSource: ComponentName,
            complicationId: Int
        ): PendingIntent {
            val intent = Intent(context, Receiver::class.java)
            intent.putExtra(EXTRA_DATA_SOURCE_COMPONENT, dataSource)
            intent.putExtra(EXTRA_COMPLICATION_ID, complicationId)

            // Pass complicationId as the requestCode to ensure that different complications get
            // different intents.
            return PendingIntent.getBroadcast(
                context,
                complicationId,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }
}