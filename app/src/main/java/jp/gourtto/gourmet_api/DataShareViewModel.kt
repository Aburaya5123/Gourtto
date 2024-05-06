package jp.gourtto.gourmet_api

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import jp.gourtto.BuildConfig
import jp.gourtto.google_api.PlaceDetailsRequest
import jp.gourtto.google_api.PlaceIdRequest
import jp.gourtto.google_api.StreetViewMetadata
import kotlinx.coroutines.launch


/**
 * hotpepperApiへのリクエスト結果に対応するリスナー
 */
interface GourmetApiRequestListener{
    fun onRequestSucceed(data: GourmetGenre)
    fun onRequestFailed(error: String, data: GourmetGenre?)

    fun onRequestSucceed(data: Gourmet?)
    fun onRequestFailed(error: String, data: Gourmet?)
}

/**
 * GooglePlaceApiへのリクエスト結果に対応するリスナー
 */
interface GoogleApiRequestListener{
    fun onRequestSucceed(placeId: String)
    fun onRequestFailed(error: String, data: PlaceIdRequest?)

    fun onRequestSucceed(data: PlaceDetailsRequest)
    fun onRequestFailed(error: String, data: PlaceDetailsRequest?)

    fun onRequestSucceed(data: StreetViewMetadata)
    fun onRequestFailed(error: String, data: StreetViewMetadata?)
}

/**
 * ActivityとFragment間で、検索結果の受け渡しを行うviewModel
 */
class DataShareViewModel: ViewModel() {

    companion object {
        private val TAG = DataShareViewModel::class.java.simpleName
    }

    private lateinit var myLocation: LatLng // 現在位置
    private lateinit var genreData: GourmetGenre // ジャンルマスタapiのレスポンス
    private lateinit var gourmetData: Gourmet // グルメサーチapiのレスポンス
    private lateinit var placeIdData: PlaceIdRequest // PlaceApi TextSearchのレスポンス
    private lateinit var placeDetailData: PlaceDetailsRequest // PlaceApi PlaceDetailsのレスポンス
    private lateinit var streetViewMeta: StreetViewMetadata // StreetViewメタデータのレスポンス

    /**
     * 格納する値は、
     *   SearchScreenFragment(検索画面)が描写されている場合 -> -1
     *   SearchResultsFragment(検索結果画面)が描写されている場合 -> 検索結果の件数
     * MaiActivityのObserverで、この値に応じてBottomSheetUIの変更を行う
     */
    private var _searchResultCounter: MutableLiveData<Int> = MutableLiveData(-1)
    val searchResultCounter
        get() = _searchResultCounter

    /**
     * 店舗詳細画面に表示する店舗ID
     * この変数にIDが格納されていれば詳細画面の表示を行い、
     *   空文字列の場合は非表示、もしくは詳細画面の破棄を行う
     */
    private var _displayedShopId: MutableLiveData<String> = MutableLiveData("")
    val displayedShopId
        get() = _displayedShopId


    // 現在位置の更新
    fun updateLocation(latLng:LatLng){
        myLocation = latLng
    }

    // 現在位置を返す
    fun getLocation(): LatLng{
        return myLocation
    }

    /**
     * ジャンルマスタapiからジャンル情報を取得
     * [listener] リクエスト(非同期)に成功,失敗した後の処理を渡す
     */
    fun getGenreParams(listener: GourmetApiRequestListener){
        if (::genreData.isInitialized){
            listener.onRequestSucceed(genreData)
        }
        else{
            viewModelScope.launch {
                try{
                    genreData = ApiClient.retrofitService.getGenreMasterApi()
                    listener.onRequestSucceed(genreData)
                }
                catch(e:Exception){
                    val tmp: GourmetGenre? = null
                    listener.onRequestFailed(e.toString(), tmp)
                }
            }
        }
    }

    /**
     * グルメサーチapiから店舗情報を取得
     * [listener] リクエスト(非同期)に成功,失敗した後の処理を渡す
     * [params] apiリクエストに追加するクエリパラメータの、keyとvalueが格納されたMap
     * [location] 指定がなければ、[myLocation]を検索の基準地点として指定
     */
    fun getSearchResults(listener: GourmetApiRequestListener,
                         params:MutableMap<String, String>, location: LatLng? =null){
        params["key"] = BuildConfig.RECRUIT_API_KEY
        params["format"] = "json"
        if (location!=null){
            params["lat"] = location.latitude.toString()
            params["lng"] = location.longitude.toString()
        }
        else{
            params["lat"] = myLocation.latitude.toString()
            params["lng"] = myLocation.longitude.toString()
        }

        viewModelScope.launch {
            try{
                gourmetData = ApiClient.retrofitService.getGourmetSearchApi(params)
                /**
                 * ここでLiveData[searchResultCounter]の更新を行い、
                 *   ObserverであるMainActivityにてUIの更新を促す
                 */
                _searchResultCounter.value = gourmetData.results.resultsReturned
                listener.onRequestSucceed(gourmetData)
            }
            catch(e:Exception){
                val tmp: Gourmet? = null
                listener.onRequestFailed(e.toString(), tmp)
            }
        }
    }

    /**
     * グルメサーチapiのレスポンスを、[perPage]件毎に分割したListにして返す
     */
    fun getPagedShopList(perPage: Int): List<List<Shop>>? {
        // 該当結果なし
        if (gourmetData.results.resultsAvailable == 0) {
            return null
        }
        return gourmetData.results.shop!!.chunked(perPage)
    }

    // gourmetData が初期化されていればtrueを返す
    fun hasSearchData(): Boolean{
        return ::gourmetData.isInitialized
    }

    /**
     * ここでLiveData[searchResultCounter]の更新を行い、
     *   ObserverであるMainActivityにてUIの更新を促す
     */
    fun backToSearchScreen(){
        _searchResultCounter.value = -1
    }

    /**
     * 店舗の詳細画面(ShopDetailFragment)に表示する店舗IDを[displayedShopId]に格納
     */
    fun createShopDetailFragment(shopId: String){
        // 連続して呼び出しがあった際は、1回目の結果のみを受け付ける
        if (_displayedShopId.value?.isNotBlank() == true){
            return
        }
        _displayedShopId.value = shopId
    }

    /**
     * 店舗詳細画面(ShopDetailFragment)の作成に失敗したため、[displayedShopId]に空文字列を設定
     */
    fun onFailedToCreateShopDetailFragment(){
        _displayedShopId.value = ""
    }

    // idで指定された店舗idのShopクラスインスタンスを返す
    fun getShopDetailInfo(id: String? = null): Shop?{
        if (::gourmetData.isInitialized.not()) return null

        return gourmetData.results.shop?.find {
            if (id == null){
                it.id == displayedShopId.value
            }
            else{
                it.id == id
            }
        }
    }

    // 店舗の名前と住所を基に、PlaceIdリクエストを行う
    fun getGooglePlaceId(url: String, listener: GoogleApiRequestListener){
        viewModelScope.launch {
            try{
                placeIdData = ApiClient.retrofitService.getPlaceId(url)
                // 複数の結果が返ってきた場合、特定ができないので失敗と見なす
                if (placeIdData.status == "OK" && placeIdData.candidates.size == 1){
                    listener.onRequestSucceed(placeIdData.candidates[0].placeId)
                }
                else{
                    val tmp: PlaceIdRequest? = null
                    listener.onRequestFailed("Duplicated data found.", tmp)
                }
            }
            catch(e:Exception){
                val tmp: PlaceIdRequest? = null
                listener.onRequestFailed(e.toString(), tmp)
            }
        }
    }

    // PlaceIdを基に、PlaceDetailsリクエストを行う
    fun getGoogleShopDetail(url: String, listener: GoogleApiRequestListener){
        viewModelScope.launch {
            try{
                placeDetailData = ApiClient.retrofitService.getPlaceDetailInfo(url)

                if (placeDetailData.status == "OK" && placeDetailData.result != null){
                    listener.onRequestSucceed(placeDetailData)
                }
                else{
                    val tmp: PlaceDetailsRequest? = null
                    listener.onRequestFailed("Failed to get the data.", tmp)
                }
            }
            catch(e:Exception){
                val tmp: PlaceDetailsRequest? = null
                listener.onRequestFailed(e.toString(), tmp)
            }
        }
    }

    // 店舗名、住所を基に、StreetViewのメタデータのリクエストを行う
    fun getStreetViewMeta(url: String, listener: GoogleApiRequestListener){
        viewModelScope.launch {
            try{
                streetViewMeta = ApiClient.retrofitService.getStreetViewMeta(url)

                if (streetViewMeta.status == "OK"){
                    listener.onRequestSucceed(streetViewMeta)
                }
                else{
                    val tmp: StreetViewMetadata? = null
                    listener.onRequestFailed("Failed to get the data.", tmp)
                }
            }
            catch(e:Exception){
                val tmp: StreetViewMetadata? = null
                listener.onRequestFailed(e.toString(), tmp)
            }
        }
    }
}