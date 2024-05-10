package jp.gourtto.gourmet_api

import android.app.Activity
import androidx.core.content.ContextCompat.getString
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.bottomsheet.BottomSheetBehavior
import jp.gourtto.BuildConfig.RECRUIT_API_KEY
import jp.gourtto.MainActivity
import jp.gourtto.R
import jp.gourtto.fragments.PermissionsRequestFragment
import jp.gourtto.google_api.PlaceDetailsRequest
import jp.gourtto.google_api.PlaceIdRequest
import jp.gourtto.google_api.StreetViewMetadata
import jp.gourtto.layouts.CustomDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.net.UnknownHostException
import kotlin.coroutines.resume


/**
 * hotpepperApiへのリクエスト結果に対応するリスナー
 */
interface GourmetApiRequestListener{
    fun onGourmetRequestSucceed(type: GourmetRequestType, genreData: GourmetGenre? = null)
    fun onGourmetRequestFailed(type: GourmetRequestType, errorType: ErrorType, error: String)
}

/**
 * GooglePlaceApiへのリクエスト結果に対応するリスナー
 */
interface GoogleApiRequestListener{
    fun onGoogleRequestSucceed(type: GoogleRequestType, placeId: String? = null,
                               placeDetail: PlaceDetailsRequest? = null,
                               streetViewMeta: StreetViewMetadata? = null)
    fun onGoogleRequestFailed(type: GoogleRequestType, errorType: ErrorType, error: String)
}
// リクエストの種類
enum class GourmetRequestType{
    GENRE_MASTER_API,
    GOURMET_SEARCH_API,
}
enum class GoogleRequestType{
    PLACE_ID_REQUEST,
    PLACE_DETAIL_REQUEST,
    STREET_VIEW_META_REQUEST
}
// エラーの種類
enum class ErrorType{
    NETWORK_ERROR,
    DUPLICATE_ID,
    ERROR
}

/**
 * ActivityとFragment間で、検索結果の受け渡しを行うviewModel
 */
class DataShareViewModel: ViewModel() {

    companion object {
        private val TAG = DataShareViewModel::class.java.simpleName
    }

    private lateinit var targetLocation: LatLng // 検索の基準地点
    private lateinit var genreData: GourmetGenre // ジャンルマスタapiのレスポンス
    private lateinit var gourmetData: Gourmet // グルメサーチapiのレスポンス
    private lateinit var placeIdData: PlaceIdRequest // PlaceApi TextSearchのレスポンス
    private lateinit var placeDetailsData: PlaceDetailsRequest // PlaceApi PlaceDetailsのレスポンス
    private lateinit var streetViewMeta: StreetViewMetadata // StreetViewメタデータのレスポンス

    // ネットワークの接続状況を更新
    private var networkOnline: MutableLiveData<Boolean> = MutableLiveData()
    // checkNetworkConnection で CustomDialogが既に生成されていればtrue
    private var hasDialog: Boolean = false

    /**
     * 格納する値は、
     *   SearchScreenFragment(検索画面)が描写されている場合 -> -1
     *   SearchResultsFragment(検索結果画面)が描写されている場合 -> 検索結果の件数
     * MaiActivityのObserverで、この値に応じてBottomSheetUIの変更を行う
     */
    private var _searchResultCounter: MutableLiveData<Int> = MutableLiveData(-1)
    val searchResultsCounter
        get() = _searchResultCounter

    /**
     * 店舗詳細画面に表示する店舗ID
     * この変数にIDが格納されていれば詳細画面の表示を行い、
     *   空文字列の場合は非表示、もしくは詳細画面の破棄を行う
     */
    private var _displayedShopId: MutableLiveData<String> = MutableLiveData("")
    val shopIdForDetails
        get() = _displayedShopId

    /*
     * AutoCompleteの候補から選択した地点の座標
     */
    private var _fetchedLocation: MutableLiveData<LatLng> = MutableLiveData(null)
    val fetchedLocation
        get() = _fetchedLocation

    /*
     * 現在位置
     */
    private var _myLocation: MutableLiveData<LatLng> = MutableLiveData(null)
    val myLocation
        get() = _myLocation

    /*
     * BottomSheetの状態
     */
    private var _bottomsheetStatus: MutableLiveData<Int> = MutableLiveData(
        BottomSheetBehavior.STATE_EXPANDED)
    val bottomsheetState
        get() = _bottomsheetStatus

    /*
     * 検索画面で選択されている検索範囲
     */
    private var _selectedRadius: MutableLiveData<Double> = MutableLiveData(300.0)
    val selectedRadius
        get() = _selectedRadius

    /*
     * 検索結果画面のMap上でマーカーを作成するShopのインスタンス
     */
    private var _markedShopInstance: MutableLiveData<Shop> = MutableLiveData()
    val markedShopInstance
        get() = _markedShopInstance


    /**
     * ジャンルマスタapiからジャンル情報を取得
     * [listener] リクエスト(非同期)に成功,失敗した後の処理を渡す
     */
    fun getGenreParams(listener: GourmetApiRequestListener, activity: Activity, fragmentManager: FragmentManager){
        if (::genreData.isInitialized){
            listener.onGourmetRequestSucceed(GourmetRequestType.GENRE_MASTER_API, genreData = genreData)
        }
        else{
            viewModelScope.launch {
                // ネットワークの接続状況確認
                checkNetworkConnection(activity, fragmentManager)
                try{
                    genreData = ApiClient.retrofitService.getGenreMasterApi()
                    listener.onGourmetRequestSucceed(GourmetRequestType.GENRE_MASTER_API, genreData = genreData)
                }
                catch(networkError: UnknownHostException){
                    listener.onGourmetRequestFailed(GourmetRequestType.GENRE_MASTER_API,
                        ErrorType.NETWORK_ERROR, networkError.toString())
                }
                catch(e:Exception){
                    listener.onGourmetRequestFailed(GourmetRequestType.GENRE_MASTER_API,
                        ErrorType.ERROR, e.toString())
                }
            }
        }
    }

    /**
     * グルメサーチapiから店舗情報を取得
     * [listener] リクエスト(非同期)に成功,失敗した後の処理を渡す
     * [params] apiリクエストに追加するクエリパラメータの、keyとvalueが格納されたMap
     */
    fun getSearchResults(listener: GourmetApiRequestListener, params:MutableMap<String, String>,
                         activity: Activity, fragmentManager: FragmentManager){
        params["key"] = RECRUIT_API_KEY
        params["format"] = "json"
        params["lat"] = targetLocation.latitude.toString()
        params["lng"] = targetLocation.longitude.toString()

        viewModelScope.launch {
            try{
                // ネットワークの接続状況確認
                checkNetworkConnection(activity, fragmentManager)
                gourmetData = ApiClient.retrofitService.getGourmetSearchApi(params)
                /**
                 * ここでLiveData[searchResultsCounter]の更新を行い、
                 *   ObserverであるMainActivityにてUIの更新を促す
                 */
                _searchResultCounter.value = gourmetData.results.resultsReturned
                listener.onGourmetRequestSucceed(GourmetRequestType.GOURMET_SEARCH_API)
            }
            catch(networkError: UnknownHostException){
                listener.onGourmetRequestFailed(GourmetRequestType.GOURMET_SEARCH_API,
                    ErrorType.NETWORK_ERROR, networkError.toString())
            }
            catch(e:Exception){
                listener.onGourmetRequestFailed(GourmetRequestType.GOURMET_SEARCH_API,
                    ErrorType.ERROR, e.toString())
            }
        }
    }

    /**
     * 店舗の住所と店名を基に、PlacesIdのリクエストを行う
     * [url] PlacesTextSearchのリクエストURL
     */
    fun getGooglePlaceId(url: String, listener: GoogleApiRequestListener,
                         activity: Activity, fragmentManager: FragmentManager){
        viewModelScope.launch {
            try{
                // ネットワークの接続状況確認
                checkNetworkConnection(activity, fragmentManager)
                placeIdData = ApiClient.retrofitService.getPlaceId(url)
                // 複数の結果が返ってきた場合、特定ができないので失敗と見なす
                if (placeIdData.status == "OK" && placeIdData.candidates.size == 1){
                    listener.onGoogleRequestSucceed(GoogleRequestType.PLACE_ID_REQUEST,
                        placeId = placeIdData.candidates[0].placeId)
                }
                else{
                    listener.onGoogleRequestFailed(GoogleRequestType.PLACE_ID_REQUEST,
                        ErrorType.DUPLICATE_ID, "Duplicate data found")
                }
            }
            catch(networkError: UnknownHostException){
                listener.onGoogleRequestFailed(GoogleRequestType.PLACE_ID_REQUEST,
                    ErrorType.NETWORK_ERROR, networkError.toString())
            }
            catch(e: Exception){
                listener.onGoogleRequestFailed(GoogleRequestType.PLACE_ID_REQUEST,
                    ErrorType.ERROR, e.toString())
            }
        }
    }

    /**
     * PlaceIdを基に、PlacesDetailsリクエストを行う
     * [url] PlacesDetailsのリクエストURL
     */
    fun getGoogleShopDetail(url: String, listener: GoogleApiRequestListener,
                            activity: Activity, fragmentManager: FragmentManager){
        viewModelScope.launch {
            try{
                // ネットワークの接続状況確認
                checkNetworkConnection(activity, fragmentManager)
                placeDetailsData = ApiClient.retrofitService.getPlaceDetailInfo(url)

                if (placeDetailsData.status == "OK" && placeDetailsData.result != null){
                    listener.onGoogleRequestSucceed(GoogleRequestType.PLACE_DETAIL_REQUEST,
                        placeDetail = placeDetailsData)
                }
                else{
                    listener.onGoogleRequestFailed(GoogleRequestType.PLACE_DETAIL_REQUEST,
                        ErrorType.ERROR,"Failed to get the data")
                }
            }
            catch(networkError: UnknownHostException){
                listener.onGoogleRequestFailed(GoogleRequestType.PLACE_DETAIL_REQUEST,
                    ErrorType.NETWORK_ERROR, networkError.toString())
            }
            catch(e: Exception){
                listener.onGoogleRequestFailed(GoogleRequestType.PLACE_DETAIL_REQUEST,
                    ErrorType.ERROR, e.toString())
            }
        }
    }

    /**
     * 店舗名、住所を基に、StreetViewのメタデータのリクエストを行う
     * [url] StreetViewStaticのリクエストURL
     */
    fun getStreetViewMeta(url: String, listener: GoogleApiRequestListener,
                          activity: Activity, fragmentManager: FragmentManager){
        viewModelScope.launch {
            try{
                // ネットワークの接続状況確認
                checkNetworkConnection(activity, fragmentManager)
                streetViewMeta = ApiClient.retrofitService.getStreetViewMeta(url)

                if (streetViewMeta.status == "OK"){
                    listener.onGoogleRequestSucceed(GoogleRequestType.STREET_VIEW_META_REQUEST,
                        streetViewMeta = streetViewMeta)
                }
                else{
                    listener.onGoogleRequestFailed(GoogleRequestType.STREET_VIEW_META_REQUEST,
                        ErrorType.ERROR,"Failed to get the data")
                }
            }
            catch(networkError: UnknownHostException){
                listener.onGoogleRequestFailed(GoogleRequestType.STREET_VIEW_META_REQUEST,
                    ErrorType.NETWORK_ERROR, networkError.toString())
            }
            catch(e: Exception){
                listener.onGoogleRequestFailed(GoogleRequestType.STREET_VIEW_META_REQUEST,
                    ErrorType.ERROR, e.toString())
            }
        }
    }

    // 検索の基準地点となる座標を更新
    fun updateTargetLocation(latLng:LatLng){
        targetLocation = latLng
    }

    // 現在位置Buttonが押された際に呼び出される
    fun updateMyLocation(latlng: LatLng){
        _myLocation.value = latlng
        updateTargetLocation(latlng)
    }

    // 検索の基準地点となる座標を取得
    fun getTargetLocation(): LatLng?{
        if (::targetLocation.isInitialized.not()){
            return MainActivity.DEFAULT_LOCATION
        }
        return targetLocation
    }

    // Mapを指定座標に移動
    fun moveTo(latlng: LatLng){
        _fetchedLocation.value = latlng
    }

    // BottomSheetのStateを変更
    fun changeBottomsheetState(state: Int){
        _bottomsheetStatus.value = state
    }

    // 検索範囲の値が変更された際に呼び出される
    fun onSelectedRadiusChanged(radius: Double){
        selectedRadius.value = radius
    }

    /**
     * グルメサーチapiのレスポンスを、[perPage]件毎に分割したListにして返す
     */
    fun getPagedShopList(perPage: Int): List<List<Shop>>? {
        // 該当結果なし
        if (::gourmetData.isInitialized.not() ||
            gourmetData.results.resultsAvailable == 0) {
            return null
        }
        return gourmetData.results.shop!!.chunked(perPage)
    }

    // gourmetData が初期化されていればtrueを返す
    fun hasSearchData(): Boolean{
        return ::gourmetData.isInitialized
    }

    /**
     * LiveData[searchResultsCounter]の更新を行い、
     *   ObserverであるMainActivityにてUIの更新を促す
     */
    fun onBackToSearchScreen(){
        _searchResultCounter.value = -1
    }

    /**
     * 店舗の詳細画面(ShopDetailFragment)に表示する店舗IDを[shopIdForDetails]に格納
     */
    fun onCreateShopDetailFragment(shopId: String){
        // 連続して呼び出しがあった際は、1回目の結果のみを受け付ける
        if (_displayedShopId.value?.isNotBlank() == true){
            return
        }
        _displayedShopId.value = shopId
    }

    /**
     * 店舗詳細画面(ShopDetailFragment)の破棄時に、[shopIdForDetails]に空文字列を設定
     */
    fun resetDisplayedShopId(){
        _displayedShopId.value = ""
    }

    // idで指定された店舗idのShopクラスインスタンスを返す
    fun getShopDetailInfo(id: String? = null): Shop?{
        if (::gourmetData.isInitialized.not()) return null

        return gourmetData.results.shop?.find {
            if (id == null){
                it.id == shopIdForDetails.value
            }
            else{
                it.id == id
            }
        }
    }

    // 検索結果画面のMapButtonが押された際に、該当する店舗情報を受け取る
    fun onShopMarkButtonClicked(instance: Shop){
        _markedShopInstance.value = instance
    }

    /**
     * ネットワークの接続状況の確認
     * 未接続の際はダイアログを表示し、接続の確認が取れるまで以降の処理を中断
     */
    private suspend fun checkNetworkConnection(activity: Activity, fragmentManager: FragmentManager) {
        while (PermissionsRequestFragment.isOnline(activity).not()){
            networkOnline.value = false
            if (hasDialog){
                networkOnline.await()
            }
            else{
                hasDialog = true
                DataShareViewModel::class.simpleName?.let {
                    CustomDialog.create(true,
                        getString(activity, R.string.network_error_title),
                        getString(activity, R.string.network_error_body),
                        getString(activity, R.string.dialog_confirm),
                        getString(activity, R.string.dialog_close))
                        .waitAsync(fragmentManager, it)
                }
                hasDialog = false
            }
        }
        networkOnline.value = true
    }

    // networkOnline が true になるまで待機
    private suspend fun LiveData<Boolean>.await(): Boolean {
        return withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                val observer = object : Observer<Boolean> {
                    override fun onChanged(value: Boolean) {
                        if (value){
                            removeObserver(this)
                            continuation.resume(value)
                        }
                    }
                }
                observeForever(observer)
                continuation.invokeOnCancellation {
                    removeObserver(observer)
                }
            }
        }
    }
}