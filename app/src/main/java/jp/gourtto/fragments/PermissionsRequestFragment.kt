package jp.gourtto.fragments

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import jp.gourtto.layouts.CustomDialog


/**
 * アプリの実行に必要な権限の確認、リクエストを行う
 */
class PermissionsRequestFragment : Fragment() {

    /**
     * 権限の取得に成功,失敗した際のリスナー
     * 呼び出し元で実装していない場合、[onAttach]でこのFragmentは破棄される
     */
    interface PermissionRequestListener{
        fun onLocationPermissionGranted()
        fun onLocationPermissionDenied()
    }

    // シングルトンでのインスタンス作成, 現在の権限の確認を行う
    companion object {
        /**
         * インスタンスの作成、及び必要な権限の受け取り
         * [permissionType]は、Manifest.permission の権限名が格納されたArray
         */
        fun create(permissionType: Array<String>): PermissionsRequestFragment {
            return PermissionsRequestFragment().apply {
                val bundle = Bundle()
                bundle.putStringArray("permissionType", permissionType)
                arguments = bundle
            }
        }

        /**
         * 現在位置の取得に必要な権限を所有している、かつGpsがオンになっていればtrueを返す
         */
        fun locationServiceReady(context: Context, activity: Activity): Boolean {
            for (permission in LocationPermissions) {
                if (ContextCompat.checkSelfPermission(context, permission)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    return gpsIsEnabled(activity)
                }
            }
            return false
        }

        // Gpsがオンになっていればtrueを返す
        private fun gpsIsEnabled(activity: Activity): Boolean {
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

    private var listener: PermissionRequestListener? = null
    private lateinit var requestedPermissions: Array<String>


    /**
     * 呼び出し元が[PermissionRequestListener]を実装しているか確認
     * 未実装の場合は、Fragmentを破棄
     */
    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is PermissionRequestListener){
            listener = context
        }
        else{
            Log.e(TAG, "PermissionRequestListener is not implemented")
            activity?.supportFragmentManager
                ?.beginTransaction()
                ?.remove(this)
                ?.commit()
        }
    }

    /**
     * リクエストのあった権限の種類によって処理を分岐
     * [create]でインスタンスを作成した際に、[Bundle]の中にArgumentsを格納
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            requestedPermissions = it.getStringArray("permissionType")!!
        }
        // ここからは、権限の種類によって分岐処理
        if (requestedPermissions.contentEquals(LocationPermissions)){
            checkLocationService()
        }
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    /**
     * [requestedPermissions] の内、一つでも権限を得ることが出来れば
     *   [granted]=trueとなる
     * 権限の種類によって分岐処理を行う
     */
    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            var granted: Boolean = false

            for (permission in requestedPermissions) {
                if (permissions.getOrElse(permission) { false }) {
                    granted = true
                    break
                }
            }
            // 権限の取得に成功
            if (granted) {
                if (requestedPermissions.contentEquals(LocationPermissions)) {
                    gpsIsEnabled() // GPSのオンオフを確認
                }
            }
            // 権限の取得に失敗
            else {
                if (requestedPermissions.contentEquals(LocationPermissions)) {
                    CustomDialog
                        .create(true, "GPSを利用できません",
                            "現在位置を取得するためには、位置情報の利用を許可してください",
                            "確認","")
                        .show(getParentFragmentManager(), CustomDialog::class.simpleName)

                    listener?.onLocationPermissionDenied()
                }
            }
        }

    /*
     * LocationPermissionsの権限の有無を確認の後、
     *   許可を持っていない場合は、確認ダイアログを表示させる
     *   許可を持っている場合は、Gpsがオンであるか確認
     */
    private fun checkLocationService(){
        for (permission in requestedPermissions) {
            if (ContextCompat.checkSelfPermission(requireContext(), permission)
                == PackageManager.PERMISSION_GRANTED)
            {
                gpsIsEnabled()
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
     * GPSがオフの場合、ダイアログを表示し設定画面に誘導する
     *   設定の変更の有無は確認できないので、[PermissionRequestListener.onLocationPermissionDenied]を返す
     * GPSがオンの場合、位置情報を利用する準備が整っているので、
     *   [PermissionRequestListener.onLocationPermissionGranted]を返す
     */
    private fun gpsIsEnabled(){
        val locationManager = requireActivity()
            .getSystemService(Context.LOCATION_SERVICE) as LocationManager
        // GPS on
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            listener?.onLocationPermissionGranted()
        }
        // GPS off
        else {
            // カスタムダイアログの作成
            CustomDialog
                .create(false, "GPSがオフになっています",
                    "位置情報サービスを利用するためには、GPSを有効にする必要があります",
                    "設定を開く","キャンセル",
                    object: CustomDialog.CustomDialogListener{
                        override fun onPositiveClicked(dialog: CustomDialog) {
                            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                            super.onPositiveClicked(dialog)
                        }
                        override fun onNegativeClicked(dialog: CustomDialog) {
                            super.onPositiveClicked(dialog)
                        }
                    })
                .show(getParentFragmentManager(), CustomDialog::class.simpleName)

            listener?.onLocationPermissionDenied()
        }
    }
}