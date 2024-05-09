package jp.gourtto

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.libraries.places.api.Places
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.google.android.material.floatingactionbutton.FloatingActionButton
import jp.gourtto.BuildConfig.PLACE_API_KEY
import jp.gourtto.databinding.ActivityMainBinding
import jp.gourtto.fragments.PermissionsRequestFragment
import jp.gourtto.fragments.PermissionsRequestFragment.Companion.isReadyForLocationServices
import jp.gourtto.fragments.ShopDetailFragment
import jp.gourtto.gourmet_api.DataShareViewModel
import jp.gourtto.gourmet_api.Shop
import jp.gourtto.layouts.CustomDialog
import java.util.Locale


class MainActivity : AppCompatActivity(), PermissionsRequestFragment.PermissionRequestListener {

    companion object{
        // PermissionRequestFragment(位置情報)作成の際に使用するTAG
        private const val LOCATION_PERMISSIONS:String = "LocationPermissions"
        // ShopDetailFragments作成の際に使用するTAG
        private const val SHOP_DETAIL_INFO: String = "ShopDetailInfo"
        // アプリ起動時の初期地点(神戸駅)
        val DEFAULT_LOCATION: LatLng = LatLng(34.6801, 135.1776)
        // BottomSheetのpeak(dp)
        private const val BOTTOMSHEET_BOTTOM_DP = 50
        private val TAG = MainActivity::class.java.simpleName
    }
    
    private val viewModel: DataShareViewModel by lazy {
        ViewModelProvider(this)[DataShareViewModel::class.java]
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var bottomsheetBehaviour: BottomSheetBehavior<View>
    private lateinit var bottomId: ConstraintLayout // BottomSheetのId
    private lateinit var floatingButton: FloatingActionButton
    // BottomSheetに変動があった際のコールバック
    private lateinit var bottomsheetCallback: BottomSheetCallback
    
    // location API
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private var mCurrLocationMarker: Marker? = null // 現在地点のマーカー
    private var shopPinnedMarker: Marker? = null // 選択されたお店のマーカー
    private lateinit var mMap: GoogleMap
    private lateinit var searchRadius: Circle // マップ上の範囲サークル

    // 検索結果件数に変動があった際のObserver
    private lateinit var shopSearchResultsObserver: Observer<Int?>
    // 詳細画面に表示するお店の店舗IDに変動があった際のObserver
    private lateinit var shopIdForDetailsObserver: Observer<String?>
    // AutoCompleteの候補から選択されたLocationに変動があった際のObserver
    private lateinit var autoCompleteLocationObserver: Observer<LatLng?>
    // BottomSheetのStateに変化があった際のObserver
    private lateinit var bottomsheetStateObserver: Observer<Int>
    // 検索範囲の値に変化があった際のObserver
    private lateinit var searchRadiusObserver: Observer<Double>
    // マーカーを表示するお店に変動があった際のObserver
    private lateinit var markedShopObserver: Observer<Shop>


    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initGoogleMap()
        initLocationApi()
        initPlaceSdk()
        setSystemUi()
        addUiListeners()
        addObservers()
        delayedMapAdjustment()

        // アプリの実行に必要な権限の確認、リクエストを行う (リクエストはアプリ起動時に限り実行)
        if (savedInstanceState == null) {
            if (isReadyForLocationServices(this, this).not()) {
                createPermissionFragment(LOCATION_PERMISSIONS)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        supportFragmentManager.dismissAllDialogs()
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        detachObservers()
        detachListeners()
        super.onDestroy()
    }

    // MapFragmentの読み込みが完了した際のコールバックを設定
    private fun initGoogleMap(){
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(onMapReadyCallback)
    }

    // MapでAutoComplete機能を使用するためPlaceSdkを使用
    private fun initPlaceSdk(){
        if (Places.isInitialized().not()){
            Places.initialize(applicationContext, PLACE_API_KEY, Locale.JAPAN)
        }
    }

    // LocationApiのインスタンス作成
    private fun initLocationApi(){
        fusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(this)
    }

    // システムUIの変更
    private fun setSystemUi() {
        // ナビゲーションバー,ステータスバーの変更
        window.apply {
            setDecorFitsSystemWindows(false)
            isStatusBarContrastEnforced = false
            isNavigationBarContrastEnforced = false
        }
    }

    // Layoutが生成された後に、Mapの初期位置を設定
    private fun delayedMapAdjustment(){
        val parent = bottomId.parent as View
        parent.post{
            bottomId.post{
                mapPadding()
            }
        }
    }

    private fun addUiListeners(){
        // 現在位置ボタンのリスナー
        floatingButton = this.findViewById(R.id.location_button)
        floatingButton.setOnClickListener {
            onMyLocationButtonClicked()
        }
        // BottomsheetBehaviourの取得
        bottomId = this.findViewById(R.id.bottom_sheet)
        bottomsheetBehaviour = BottomSheetBehavior.from(bottomId)

        bottomsheetCallback = object: BottomSheetCallback(){
            // BottomSheetのスライド時に、Mapのpaddingを行う
            override fun onSlide(p0: View, p1: Float) {
                mapPadding()
            }
            override fun onStateChanged(p0: View, p1: Int) { }
        }
        bottomsheetBehaviour.addBottomSheetCallback(bottomsheetCallback)
    }

    private fun detachListeners(){
        if (::floatingButton.isInitialized) floatingButton.setOnClickListener(null)
        if (::bottomsheetBehaviour.isInitialized && ::bottomsheetCallback.isInitialized)
            bottomsheetBehaviour.removeBottomSheetCallback(bottomsheetCallback)
    }

    // BottomSheetのサイズに変動があった際にMapのpaddingを変更し、Map画面中心が追従するように設定
    private fun mapPadding(){
        val parent = bottomId.parent as View
        binding.map.setPadding(0, 0, 0,
            parent.height - bottomId.top - BOTTOMSHEET_BOTTOM_DP)
    }

    private fun addObservers(){
        /**
         * SearchScreenFragmentとSearchResultsFragmentの間で遷移があった際に、ButtonSheetのUI更新を行う
         * viewModel.searchResultCounter
         *   検索画面(SearchScreenFragment) -> -1
         *   検索結果画面(SearchResultFragment) -> ヒット件数(Int)
         */
        shopSearchResultsObserver = Observer{hit ->
            // BottomSheet上部のテキスト
            val title: TextView = this.findViewById(R.id.bottom_sheet_title)
            // BottomSheet上部のアイコン
            val icon: ImageView = this.findViewById(R.id.bottom_sheet_icon)

            // 検索画面
            if (hit==-1){
                title.text = getString(R.string.search_title)
                icon.apply {
                    background = AppCompatResources
                        .getDrawable(context, R.drawable.search_fill0_wght400_grad0_opsz24)
                }
            }
            // 検索結果画面
            else{
                title.text = String.format(getString(R.string.search_result_counter), hit)
                icon.apply {
                    background = AppCompatResources
                        .getDrawable(context, R.drawable.menu_book_fill0_wght400_grad0_opsz24)
                }
            }
        }
        viewModel.searchResultsCounter.observe(this, shopSearchResultsObserver)

        /**
         * 検索結果画面(SearchResultsFragment)で店舗が選択された際に、ShopDetailFragmentの作成を行う
         * viewModel.displayedShopId
         *   -> 選択された店舗のId (グルメサーチapi)
         */
        shopIdForDetailsObserver = Observer{ id ->
            id?.takeIf{it.isNotEmpty()}?.let {
                // 既に画面が作成されていないか確認
                if (isFragmentInContainerAlive(SHOP_DETAIL_INFO).not()){
                    createShopDetailFragment(it)
                }
                else{
                    viewModel.resetDisplayedShopId()
                }
            }
        }
        viewModel.shopIdForDetails.observe(this, shopIdForDetailsObserver)

        /**
         * SearchScreenFragmentにて、AutoCompleteで候補が選択された場合、Mapの地点を該当座標へ移動させる
         * viewModel.fetchedLocation
         *   -> AutoCompleteで選択された候補地のLatLng
         */
        autoCompleteLocationObserver = Observer{location ->
            location?.takeIf { ::mMap.isInitialized }?.let {
                moveToLocation(it)
            }
        }
        viewModel.fetchedLocation.observe(this, autoCompleteLocationObserver)

        /**
         * BottomSheetのStateの変更指示を受け付ける
         * viewModel.bottomsheetStatus
         *   -> [BottomSheetBehavior.STATE_COLLAPSED]
         *   -> [BottomSheetBehavior.STATE_EXPANDED]
         */
        bottomsheetStateObserver = Observer{ status ->
            if (::bottomsheetBehaviour.isInitialized.not()) return@Observer
            if (bottomsheetBehaviour.state != status){
                bottomsheetBehaviour.state = status
                mapPadding()
            }
        }
        viewModel.bottomsheetState.observe(this, bottomsheetStateObserver)

        /**
         * 検索範囲が変更された際に、Map上のサークルのサイズを変更する
         * viewModel.selectedRadius
         *   -> (Double) 300.0/ 500.0/ 1000.0/ 2000.0/ 3000.0
         */
        searchRadiusObserver = Observer{radius ->
            onSearchRadiusChanged(radius)
        }
        viewModel.selectedRadius.observe(this, searchRadiusObserver)

        /**
         * 検索結果画面でお店のMapButtonがクリックされた際に、Map座標の更新を行う
         * viewModel.pinnedShop
         *   -> 選択されたお店の[Shop]インスタンス
         */
        markedShopObserver = Observer{ shop ->
            if (shop.name != null && shop.lat!=null && shop.lng !=null){
                onMarkedShopChanged(shop.name, LatLng(shop.lat, shop.lng))
            }
        }
        viewModel.markedShopInstance.observe(this, markedShopObserver)
    }

    private fun detachObservers(){
        viewModel.searchResultsCounter.removeObserver(shopSearchResultsObserver)
        viewModel.shopIdForDetails.removeObserver(shopIdForDetailsObserver)
        viewModel.fetchedLocation.removeObserver(autoCompleteLocationObserver)
        viewModel.bottomsheetState.removeObserver(bottomsheetStateObserver)
        viewModel.markedShopInstance.removeObserver(markedShopObserver)
    }

    /**
     * [PermissionsRequestFragment]の作成
     * [tag]で、Permissionの種類を指定
     */
    private fun createPermissionFragment(tag: String){
        val permissions: Array<String> =
            when (tag){
                LOCATION_PERMISSIONS -> PermissionsRequestFragment.LocationPermissions
                else -> arrayOf() // ここに権限を追加
            }
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.fragment_container_view_permissions,
                PermissionsRequestFragment.create(permissions, this)
                , tag)
            .commit()
    }

    /**
     * 店舗の詳細情報を表示する[ShopDetailFragment]の作成を行う
     * [id] グルメサーチapiで取得した店舗ID
     */
    private fun createShopDetailFragment(id: String){
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.fragment_container_view_shop_detail,
                ShopDetailFragment()
                , SHOP_DETAIL_INFO)
            .commit()
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
     * [tag]を使用して、Fragmentの生存確認を行う
     * [createPermissionFragment]でFragment作成の際に渡したTAGと一致
     * 生存していればtrueを返す
     */
    private fun isFragmentInContainerAlive(tag: String): Boolean{
        return try{
            val thisFragment =
                supportFragmentManager.findFragmentByTag(tag)
            thisFragment != null && thisFragment.isVisible
        } catch(e:Exception){
            Log.e(TAG, e.toString())
            false
        }
    }

    /*
     * mapFragmentの準備ができた際のコールバック
     * 以降に現在位置の取得を行う
     */
    @SuppressLint("MissingPermission")
    private val onMapReadyCallback = OnMapReadyCallback { googleMap ->
        mMap = googleMap
        // viewModelの現在位置情報を更新
        viewModel.updateTargetLocation(DEFAULT_LOCATION)
        moveToLocation(DEFAULT_LOCATION)

        if (isReadyForLocationServices(this,this)) {
            updateCurrentLocation()
        }
    }

    /*
     * 検索範囲に変動があった際のリスナー
     * Map上のサークルの再表示を行う
     */
    private fun onSearchRadiusChanged(rad: Double){
        val displayedLocation = viewModel.getTargetLocation()
        if (::mMap.isInitialized.not() || displayedLocation==null) return
        // 現在表示されているサークルを削除
        if (::searchRadius.isInitialized) searchRadius.remove()
        searchRadius = mMap.addCircle(
            CircleOptions()
                .center(displayedLocation)
                .radius(rad)
                .strokeColor(getColor(R.color.light_orange))
                .fillColor(getColor(R.color.trans_yellow))
                .strokeWidth(0.5f)
        )
    }

    /**
     * 現在位置の更新
     * [onMapReadyCallback], [isReadyForLocationServices] -> true の後に呼び出す
     */
    @SuppressLint("MissingPermission")
    private fun updateCurrentLocation(){
        val cancellationTokenSource = CancellationTokenSource()

        fusedLocationProviderClient.getCurrentLocation(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            cancellationTokenSource.token)
            .addOnSuccessListener { location: Location? ->
                if (location == null)
                    Log.e(TAG, "Failed to get current location.")
                else {
                    val latLng = LatLng(location.latitude, location.longitude)
                    // viewModelの現在位置情報を更新
                    viewModel.updateMyLocation(latLng)
                    moveToLocation(latLng)
                }
            }
    }

    // Mapのカメラ移動とマーカーの更新を行う
    private fun moveToLocation(destination: LatLng){
        // 設置済みマーカーを削除
        mCurrLocationMarker?.remove()

        // マーカーオプション, マーカーの設置
        val markerOptions = MarkerOptions().apply{
            position(destination)
            icon(bitmapDescriptorFromVector(R.drawable.emoji_people_24dp_fill0_wght400_grad0_opsz24))
        }
        mCurrLocationMarker = mMap.addMarker(markerOptions)
        // カメラ移動
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(destination, 15f))
        // 範囲サークルの再描写
        viewModel.selectedRadius.value?.let { onSearchRadiusChanged(it) }
    }

    // 現在位置ボタンをクリックした際のリスナー
    private fun onMyLocationButtonClicked() {
        // PermissionRequestFragmentが生存している間は返す
        if (isFragmentInContainerAlive(LOCATION_PERMISSIONS)){
            return
        }
        else if (isReadyForLocationServices(this,this)){
            updateCurrentLocation()
        }
        // 許可を持っていないのでPermissionRequestFragmentの生成
        else{
            createPermissionFragment(LOCATION_PERMISSIONS)
        }
    }

    // 検索結果画面でMap上でマークされているお店に変化があった際のリスナー
    private fun onMarkedShopChanged(name: String, location: LatLng){
        // 設置済みマーカーを削除
        shopPinnedMarker?.remove()

        val markerOptions = MarkerOptions().apply{
            position(location)
            icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
            title(name)
            snippet("右下のアイコンから経路を検索")
        }
        shopPinnedMarker = mMap.addMarker(markerOptions)
        // カメラ移動
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 17f))
        shopPinnedMarker?.showInfoWindow()
    }

    // Vector画像からMarkerの作成
    private fun bitmapDescriptorFromVector(vectorResId: Int): BitmapDescriptor {
        val vectorDrawable = AppCompatResources.getDrawable(this, vectorResId)
        vectorDrawable!!.setBounds(
            0,
            0,
            vectorDrawable.intrinsicWidth,
            vectorDrawable.intrinsicHeight
        )
        val bitmap = Bitmap.createBitmap(
            vectorDrawable.intrinsicWidth,
            vectorDrawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        vectorDrawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    // 全てのCustomDialogを削除
    private fun FragmentManager.dismissAllDialogs() {
        fragments.forEach { fragment ->
            (fragment as? CustomDialog)?.dismissAllowingStateLoss()
            fragment.childFragmentManager.dismissAllDialogs()
        }
    }
}