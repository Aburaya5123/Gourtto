package jp.gourtto


import android.annotation.SuppressLint
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.Navigation
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.floatingactionbutton.FloatingActionButton
import jp.gourtto.PermissionsRequestFragment.Companion.locationServiceReady
import jp.gourtto.databinding.ActivityMainBinding
import jp.gourtto.gourmetapi.GourmetViewModel


class MainActivity : AppCompatActivity(), PermissionsRequestFragment.PermissionRequestListener {

    companion object{
        private val TAG = PermissionsRequestFragment::class.java.simpleName
        // 権限の種類を識別するためのTAG
        private const val LOCATION_PERMISSIONS:String = "LocationPermissions"
    }

    private lateinit var binding: ActivityMainBinding

    private val viewModel: GourmetViewModel by lazy {
        ViewModelProvider(this)[GourmetViewModel::class.java]
    }

    // location API
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    // 現在地点のマーカーを保持
    private var mCurrLocationMarker: Marker? = null
    private lateinit var mMap: GoogleMap


    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initGoogleMap()
        initLocationApi()
        setSystemUi()
        addUiListeners()

        // アプリの実行に必要な権限の確認、リクエストを行う (リクエストはアプリ起動時に限り実行)
        if (savedInstanceState == null) {
            if (locationServiceReady(this, this).not()) {
                createPermissionFragment(LOCATION_PERMISSIONS)
            }
        }
    }

    // MapFragmentの読み込みが完了した際のコールバックを設定
    private fun initGoogleMap(){
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(onMapReadyCallback)
    }

    // LocationApiのインスタンス作成
    private fun initLocationApi(){
        fusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(this)
    }

    // システムUIの変更設定
    private fun setSystemUi() {
        // ナビゲーションバー,ステータスバーの変更
        window.apply {
            setDecorFitsSystemWindows(false)
            isStatusBarContrastEnforced = false
            isNavigationBarContrastEnforced = false
        }
    }

    // UIに対してリスナーを設定
    private fun addUiListeners(){
        // 現在位置ボタンのリスナー
        this.findViewById<FloatingActionButton>(R.id.location_button).setOnClickListener {
            onMyLocationButtonClicked()
        }
        // BottomSheet上部のテキスト
        val title: TextView = this.findViewById(R.id.bottom_sheet_title)
        // BottomSheet上部のアイコン
        val icon: ImageView = this.findViewById(R.id.bottom_sheet_icon)

        /**
         * [viewModel]にて検索結果件数のLiveDataに変動があった際に、ButtonSheetのUIの更新を行う
         * TextView,ImageViewの入れ替え
         */
        viewModel.searchResultCounter.observe(this, Observer {
            if (it==-1){ // 検索画面
                title.text = getString(R.string.search_title)
                icon.apply {
                    background = AppCompatResources
                        .getDrawable(context, R.drawable.search_fill0_wght400_grad0_opsz24)
                }
            }
            else{ // 検索結果画面
                title.text = String.format(getString(R.string.search_result_counter), it)
                icon.apply {
                    background = AppCompatResources
                        .getDrawable(context, R.drawable.menu_book_fill0_wght400_grad0_opsz24)
                }
            }
        })
    }

    /**
     * [PermissionsRequestFragment.PermissionRequestListener]の実装
     * 位置情報使用の許可・GPSオンの両方を満たす場合に呼び出される
     */
    @SuppressLint("MissingPermission")
    override fun onLocationPermissionGranted(){
        if (::mMap.isInitialized){
            updateCurrentLocation()
        }
    }

    /**
     * [PermissionsRequestFragment.PermissionRequestListener]の実装
     * 位置情報使用の許可・GPSオンのいずれかを満たさない場合に呼び出される
     */
    override fun onLocationPermissionDenied() { }

    /**
     * [PermissionsRequestFragment]の作成
     * [permissionType]で、Permissionの種類を指定、TAGとして渡す
     */
    private fun createPermissionFragment(permissionType: String){
        val permissions: Array<String> =
            if (permissionType==LOCATION_PERMISSIONS){
                PermissionsRequestFragment.LocationPermissions
            }
            else{
                arrayOf() // 権限を追加する際はここ
            }

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.fragment_container_view,
                PermissionsRequestFragment.create(permissions) // インスタンス作成
                , permissionType)
            .commit()
    }

    /**
     * [permissionType]を使用して、Fragmentの生存確認を行う
     * [createPermissionFragment]でFragment作成の際に渡したTAGと一致
     * 生存していればtrueを返す
     */
    private fun containerFragmentIsAlive(permissionType: String): Boolean{
        return try{
            val thisFragment =
                supportFragmentManager.findFragmentByTag(permissionType)
            thisFragment != null && thisFragment.isVisible
        } catch(e:Exception){
            Log.e(TAG, e.toString())
            false
        }
    }

    /*
     * mapFragmentの準備ができた際のコールバック
     * 以降に現在位置の取得を行う
     * 初期地点は神戸に設定
     */
    @SuppressLint("MissingPermission")
    private val onMapReadyCallback = OnMapReadyCallback { googleMap ->
        mMap = googleMap

        val kobe = LatLng(34.6801, 135.1776)
        mCurrLocationMarker = mMap.addMarker(MarkerOptions().position(kobe).title("神戸駅"))
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(kobe, 13f))

        // viewModelの現在位置情報を更新
        viewModel.updateLocation(kobe)

        if (locationServiceReady(this,this)) {
            updateCurrentLocation()
        }
    }

    /**
     * 現在位置の更新
     * [onMapReadyCallback], [locationServiceReady]==true の後に呼び出す
     */
    @SuppressLint("MissingPermission")
    private fun updateCurrentLocation(){
        // キャンセルトークン
        val cancellationTokenSource = CancellationTokenSource()

        fusedLocationProviderClient.getCurrentLocation(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            cancellationTokenSource.token)
            .addOnSuccessListener { location: Location? ->
                if (location == null)
                    Log.e(TAG, "Failed to get current location.")
                else {
                    // 設置済みマーカーを削除
                    mCurrLocationMarker?.remove()

                    val latLng = LatLng(location.latitude, location.longitude)

                    // viewModelの現在位置情報を更新
                    viewModel.updateLocation(latLng)

                    // マーカーオプション, マーカーの設置
                    val markerOptions = MarkerOptions().apply{
                        position(latLng)
                        title("現在地")
                        icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_MAGENTA))
                    }
                    mCurrLocationMarker = mMap.addMarker(markerOptions)
                    // カメラ移動
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 13f))
                }
            }
    }

    // 現在位置ボタンをクリックした際のリスナー
    private fun onMyLocationButtonClicked() {
        // PermissionRequestFragmentが生存している間は返す
        if (containerFragmentIsAlive(LOCATION_PERMISSIONS)){
            return
        }
        else if (locationServiceReady(this,this)){
            updateCurrentLocation()
        }
        // 許可を持っていないのでPermissionRequestFragmentの生成
        else{
            createPermissionFragment(LOCATION_PERMISSIONS)
        }
    }
}