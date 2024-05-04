package jp.gourtto.gourmetapi

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import jp.gourtto.ApiRequestListener
import jp.gourtto.BuildConfig
import kotlinx.coroutines.launch


/**
 * ActivityとFragment間で、検索結果の受け渡しを行うviewModel
 */
class GourmetViewModel: ViewModel() {

    companion object {
        private val TAG = GourmetViewModel::class.java.simpleName
    }

    private lateinit var myLocation: LatLng // 現在位置
    private lateinit var genreData: GourmetGenre // ジャンルマスタapiのレスポンス
    private lateinit var gourmetData: Gourmet // グルメサーチapiのレスポンス

    /**
     * 格納する値は、
     *   SearchScreenFragment(検索画面) -> -1
     *   SearchResultsFragment(検索結果画面) -> 検索結果の件数
     * MaiActivityのObserverで、この値に応じてBottomSheetのUIを変更する
     */
    private var _searchResultCounter: MutableLiveData<Int> = MutableLiveData(-1)
    val searchResultCounter
        get() = _searchResultCounter

    // 取得した現在位置の更新
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
    fun getGenreParams(listener: ApiRequestListener){
        if (::genreData.isInitialized){
            listener.onRequestSucceed(genreData)
        }
        else{
            viewModelScope.launch {
                try{
                    genreData = GourmetApi.retrofitService.getGenreParams()
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
    fun getSearchResults(listener: ApiRequestListener,
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
                gourmetData = GourmetApi.retrofitService.getShopData(params)
                /**
                 * ここでLiveData[searchResultCounter]の更新を行い、
                 *   ObserverであるMainActivityにてUIの更新を行う
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
     * グルメサーチapiのレスポンスを[perPage]件毎に分割したListにして返す
     */
    fun getPagedShopList(perPage: Int): List<List<Shop>>? {
        // 該当結果なし
        if (gourmetData.results.resultsAvailable == 0) {
            return null
        }
        return gourmetData.results.shop!!.chunked(perPage)
    }

    // gourmetData 初期化されていればtrueを返す
    fun hasSearchData(): Boolean{
        return ::gourmetData.isInitialized
    }

    /**
     * ここでLiveData[searchResultCounter]の更新を行い、
     *   ObserverであるMainActivityにてUIの更新を行う
     */
    fun backToSearchScreen(){
        _searchResultCounter.value = -1
    }
}