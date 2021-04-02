package com.rubayet.hrv

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.fragment.app.FragmentActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataSource
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.request.DataSourcesRequest
import com.google.android.gms.fitness.request.OnDataPointListener
import com.google.android.gms.fitness.request.SensorRequest

class MainActivity : FragmentActivity() {


    companion object {
        const val TAG = "BasicSensorsApi"
        private const val REQUEST_OAUTH_REQUEST_CODE = 1
        private const val REQUEST_PERMISSIONS_REQUEST_CODE = 34
    }

    // [START mListener_variable_reference]
    // Need to hold a reference to this listener, as it's passed into the "unregister"
    // method in order to stop all sensors from sending data to this listener.
    private var mListener: OnDataPointListener? = null

    // [END mListener_variable_reference]
    // [START auth_oncreate_setup]
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Put application specific code here.
        setContentView(R.layout.activity_main)
        // This method sets up our custom logger, which will print all log messages to the device
        // screen, as well as to adb logcat.


        // When permissions are revoked the app is restarted so onCreate is sufficient to check for
        // permissions core to the Activity's functionality.
        val  mSensorManager =  getSystemService(Context.SENSOR_SERVICE) as SensorManager

        val sensorList: List<Sensor> = mSensorManager.getSensorList(Sensor.TYPE_ALL)
        for (currentSensor in sensorList) {
            Log.d(
                TAG,
                "Name: " + currentSensor.getName()
                    .toString() + " /Type_String: " + currentSensor.getStringType()
                    .toString() + " /Type_number: " + currentSensor.getType()
            )
        }

        if (hasRuntimePermissions()) {
            findFitnessDataSourcesWrapper()
        } else {
            requestRuntimePermissions()
        }
    }

    /**
     * A wrapper for [.findFitnessDataSources]. If the user account has OAuth permission,
     * continue to [.findFitnessDataSources], else request OAuth permission for the account.
     */
    private fun findFitnessDataSourcesWrapper() {
        if (hasOAuthPermission()) {
            findFitnessDataSources()
        } else {
            requestOAuthPermission()
        }
    }

    /** Gets the [FitnessOptions] in order to check or request OAuth permission for the user.  */
    private val fitnessSignInOptions: FitnessOptions
        get() = FitnessOptions.builder().addDataType(DataType.TYPE_HEART_RATE_BPM).build()

    /** Checks if user's account has OAuth permission to Fitness API.  */
    private fun hasOAuthPermission(): Boolean {
        val fitnessOptions = fitnessSignInOptions
        return GoogleSignIn.hasPermissions(
            GoogleSignIn.getLastSignedInAccount(this),
            fitnessOptions
        )
    }

    /** Launches the Google SignIn activity to request OAuth permission for the user.  */
    private fun requestOAuthPermission() {
        val fitnessOptions = fitnessSignInOptions
        GoogleSignIn.requestPermissions(
            this,
            REQUEST_OAUTH_REQUEST_CODE,
            GoogleSignIn.getLastSignedInAccount(this),
            fitnessOptions
        )
    }

    override fun onResume() {
        super.onResume()

        // This ensures that if the user denies the permissions then uses Settings to re-enable
        // them, the app will start working.
        findFitnessDataSourcesWrapper()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_OAUTH_REQUEST_CODE) {
                findFitnessDataSources()
            }
        }
    }
    // [END auth_oncreate_setup]
    /** Finds available data sources and attempts to register on a specific [DataType].  */
    private fun findFitnessDataSources() {
        // [START find_data_sources]
        // Note: Fitness.SensorsApi.findDataSources() requires the BODY_SENSORS permission.
        GoogleSignIn.getLastSignedInAccount(this)?.let {
            Fitness.getSensorsClient(this, it)
                .findDataSources(
                    DataSourcesRequest.Builder()
                        .setDataTypes(DataType.TYPE_HEART_RATE_BPM)
                        .setDataSourceTypes(DataSource.TYPE_RAW)
                        .build()
                )
                .addOnSuccessListener { dataSources ->
                    for (dataSource in dataSources) {
                        Log.i(TAG, "Data source found: $dataSource")
                        Log.i(TAG, "Data Source type: " + dataSource.dataType.name)

                        // Let's register a listener to receive Activity data!
                        if (dataSource.dataType == DataType.TYPE_HEART_RATE_BPM && mListener == null) {
                            Log.i(TAG, "Data source for TYPE_HEART_RATE_BPM found!  Registering.")
                            registerFitnessDataListener(dataSource, DataType.TYPE_HEART_RATE_BPM)
                        }
                    }
                }
                .addOnFailureListener { e -> Log.e(TAG, "failed", e) }
            // [END find_data_sources]
        }
    }

    /**
     * Registers a listener with the Sensors API for the provided [DataSource] and [ ] combo.
     */
    private fun registerFitnessDataListener(dataSource: DataSource, dataType: DataType) {
        // [START register_data_listener]
        mListener = OnDataPointListener { dataPoint ->
            for (field in dataPoint.dataType.fields) {
                val `val` = dataPoint.getValue(field)
                Log.i(TAG, "Detected DataPoint field: " + field.name)
                Log.i(TAG, "Detected DataPoint value: $`val`")
            }
        }

        mListener?.let { mlstnr ->
            GoogleSignIn.getLastSignedInAccount(this)?.let { sgninacnt ->
                Fitness.getSensorsClient(this, sgninacnt)
                    .add(
                        SensorRequest.Builder()
                            .setDataSource(dataSource) // Optional but recommended for custom data sets.
                            .setDataType(dataType) // Can't be omitted.
                            //.setSamplingRate(10, TimeUnit.SECONDS)
                            .build(),
                        mlstnr
                    )
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Log.i(TAG, "Listener registered!")
                        } else {
                            Log.e(TAG, "Listener not registered.", task.exception)
                        }
                    }
            }
            // [END register_data_listener]
        }
    }

    /** Unregisters the listener with the Sensors API.  */
    private fun unregisterFitnessDataListener() {
        if (mListener == null) {
            // This code only activates one listener at a time.  If there's no listener, there's
            // nothing to unregister.
            return
        }

        // [START unregister_data_listener]
        // Waiting isn't actually necessary as the unregister call will complete regardless,
        // even if called from within onStop, but a callback can still be added in order to
        // inspect the results.
        mListener?.let { mlstnr ->
            GoogleSignIn.getLastSignedInAccount(this)?.let { accnt ->
                Fitness.getSensorsClient(this, accnt)
                    .remove(mlstnr)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful && task.result!!) {
                            Log.i(TAG, "Listener was removed!")
                        } else {
                            Log.i(TAG, "Listener was not removed.")
                        }
                    }
            }
        }
        // [END unregister_data_listener]
    }

    /** Returns the current state of the permissions needed.  */
    private fun hasRuntimePermissions(): Boolean {
        val permissionState =
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
        return permissionState == PackageManager.PERMISSION_GRANTED
    }

    private fun requestRuntimePermissions() {
        val shouldProvideRationale =
            shouldShowRequestPermissionRationale(Manifest.permission.BODY_SENSORS)

        // Provide an additional rationale to the user. This would happen if the user denied the
        // request previously, but didn't check the "Don't ask again" checkbox.
        if (shouldProvideRationale) {
            Log.i(TAG, "Displaying permission rationale to provide additional context.")
        } else {
            Log.i(TAG, "Requesting permission")
            // Request permission. It's possible this can be auto answered if device policy
            // sets the permission in a given state or the user denied the permission
            // previously and checked "Never ask again".
            requestPermissions(
                arrayOf(Manifest.permission.BODY_SENSORS),
                REQUEST_PERMISSIONS_REQUEST_CODE
            )
        }
    }

    /** Callback received when a permissions request has been completed.  */
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        Log.i(TAG, "onRequestPermissionResult")
        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE) {
            when {
                grantResults.isEmpty() -> {
                    // If user interaction was interrupted, the permission request is cancelled and you
                    // receive empty arrays.
                    Log.i(TAG, "User interaction was cancelled.")
                }
                grantResults[0] == PackageManager.PERMISSION_GRANTED -> {
                    // Permission was granted.
                    findFitnessDataSourcesWrapper()
                }
                else -> {
                    // Permission denied.

                    // In this Activity we've chosen to notify the user that they
                    // have rejected a core permission for the app since it makes the Activity useless.
                    // We're communicating this message in a Snackbar since this is a sample app, but
                    // core permissions would typically be best requested during a welcome-screen flow.

                    // Additionally, it is important to remember that a permission might have been
                    // rejected without asking the user for permission (device policy or "Never ask
                    // again" prompts). Therefore, a user interface affordance is typically implemented
                    // when permissions are denied. Otherwise, your app could appear unresponsive to
                    // touches or interactions which have required permissions.
                }
            }
        }
    }

    override fun onDestroy() {
        unregisterFitnessDataListener()
        super.onDestroy()
    }
}