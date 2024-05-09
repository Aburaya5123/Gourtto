package jp.gourtto.fragments

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import jp.gourtto.R
import jp.gourtto.layouts.CustomDialog


/**
 * アプリの実行に必要な権限の確認、リクエストを行う
 */
class PermissionsRequestFragment : Fragment() {

    /**
     * 権限の取得に成功,失敗した際のリスナー
     */
    interface PermissionRequestListener{
        fun onLocationPermissionGranted()
        fun onLocationPermissionDenied()
    }

    // シングルトンでのインスタンス作成, 現在の権限の確認を行う
    companion object {
        /**
         * インスタンスの作成、及び必要な権限の受け取り
         * [permissionType] Manifest.permission の権限名が格納されたArray
         */
        fun create(permissionType: Array<String>, listener: PermissionRequestListener)
        : PermissionsRequestFragment {
            return PermissionsRequestFragment().apply {
                val bundle = Bundle()
                bundle.putStringArray("permissionType", permissionType)
                arguments = bundle
                this.mListener = listener
                if (this.mListener == null){
                    Log.e(TAG, "PermissionRequestListener is not implemented.")
                }
            }
        }

        /**
         * 現在位置の取得に必要な権限を所有している、かつGpsがオンになっていればtrueを返す
         */
        fun isReadyForLocationServices(context: Context, activity: Activity): Boolean {
            for (permission in LocationPermissions) {
                if (ContextCompat.checkSelfPermission(context, permission)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    return isGpsEnabled(activity)
                }
            }
            return false
        }

        /**
         * ネットワーク接続状況の確認
         */
        fun isOnline(activity: Activity): Boolean {
            val connectionManager =
                activity.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
            if (connectionManager != null){
                val capabilities =
                    connectionManager.getNetworkCapabilities(connectionManager.activeNetwork)
                if (capabilities != null) {
                    return when {
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                        else -> false
                    }
                }
            }
            return false
        }

        // Gpsがオンになっていればtrueを返す
        fun isGpsEnabled(activity: Activity): Boolean {
            val locationManager = activity
                .getSystemService(Context.LOCATION_SERVICE) as LocationManager
            return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        }

        // 位置情報に関連する権限
        val LocationPermissions = arrayOf(
            ACCESS_FINE_LOCATION,
            ACCESS_COARSE_LOCATION
        )

        private val TAG = PermissionsRequestFragment::class.java.simpleName
    }

    private var mListener: PermissionRequestListener? = null
    private lateinit var requestedPermissions: Array<String>


    // リクエストのあった権限の種類によって処理を分岐
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            requestedPermissions = it.getStringArray("permissionType")!!
        }
        if (requestedPermissions.contentEquals(LocationPermissions)){
            isReadyForLocationServices()
        }
    }

    override fun onDetach() {
        mListener = null
        super.onDetach()
    }

    /**
     * [requestedPermissions] の内、一つでも権限を得ることが出来れば[granted] -> trueとなる
     * 権限の種類によって分岐処理を行う
     */
    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            var granted = false

            for (permission in requestedPermissions) {
                if (permissions.getOrElse(permission) { false }) {
                    granted = true
                    break
                }
            }
            // 権限の取得に成功
            if (granted) {
                if (requestedPermissions.contentEquals(LocationPermissions)) {
                    isGpsEnabled() // GPSのオンオフを確認
                }
            }
            // 権限の取得に失敗
            else {
                if (requestedPermissions.contentEquals(LocationPermissions)) {
                    CustomDialog
                        .create(true, getString(R.string.location_auth_error_title),
                            getString(R.string.location_auth_error_body),
                            getString(R.string.dialog_confirm),getString(R.string.dialog_confirm))
                        .show(parentFragmentManager, CustomDialog::class.simpleName)

                    mListener?.onLocationPermissionDenied()
                }
            }
        }

    /*
     * LocationPermissionsの権限の有無を確認の後、
     *   許可を持っていない場合は、確認ダイアログを表示させる
     *   許可を持っている場合は、Gpsがオンであるか確認
     */
    private fun isReadyForLocationServices(){
        for (permission in requestedPermissions) {
            if (ContextCompat.checkSelfPermission(requireContext(), permission)
                == PackageManager.PERMISSION_GRANTED)
            {
                isGpsEnabled()
                return
            }
        }
        // 必要な権限を持っていない場合は、リクエストを行う
        requestPermissionLauncher.launch(
            requestedPermissions
        )
    }

    /**
     * GPSのオンオフを確認する
     *   GPSがオフの場合、ダイアログを表示し設定画面に誘導する
     *   GPSがオンの場合、位置情報を利用する準備が整っているので、successListenerを呼び出す
     */
    private fun isGpsEnabled(){
        val locationManager = requireActivity()
            .getSystemService(Context.LOCATION_SERVICE) as LocationManager
        // GPS on
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            mListener?.onLocationPermissionGranted()
        }
        // GPS off
        else {
            CustomDialog
                .create(false, getString(R.string.gps_error_title),
                    getString(R.string.gps_error_body),
                    getString(R.string.open_settings),
                    getString(R.string.dialog_close),
                    object: CustomDialog.CustomDialogListener{
                        override fun onPositiveClicked(dialog: CustomDialog) {
                            // GPSの設定画面を開く
                            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                            super.onPositiveClicked(dialog)
                        }
                        override fun onNegativeClicked(dialog: CustomDialog) {
                            super.onPositiveClicked(dialog)
                        }
                    })
                .show(parentFragmentManager, CustomDialog::class.simpleName)

            mListener?.onLocationPermissionDenied()
        }
    }
}