package jp.gourtto.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.transition.TransitionInflater
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.addCallback
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.gms.maps.OnStreetViewPanoramaReadyCallback
import com.google.android.gms.maps.StreetViewPanorama
import com.google.android.gms.maps.SupportStreetViewPanoramaFragment
import jp.gourtto.BuildConfig.PLACE_API_KEY
import jp.gourtto.R
import jp.gourtto.databinding.FragmentShopDetailBinding
import jp.gourtto.google_api.PlaceDetailsRequest
import jp.gourtto.google_api.PlaceIdRequest
import jp.gourtto.google_api.StreetViewMetadata
import jp.gourtto.gourmet_api.DataShareViewModel
import jp.gourtto.gourmet_api.GoogleApiRequestListener
import jp.gourtto.gourmet_api.Shop
import jp.gourtto.layouts.ArrangeShopInfo
import jp.gourtto.layouts.CustomDialog
import jp.gourtto.layouts.ShopDetailRecyclerViewAdapter


/**
 * 店舗詳細を表示するFragment
 * hotpepperApiに加えて、GooglePlaceApiも使用し、店舗の写真や電話番号、ストリートビューの取得、表示を行う
 * StreetViewの呼び出しはコストが高いため、注意が必要
 */
class ShopDetailFragment : Fragment(), OnStreetViewPanoramaReadyCallback,
    GoogleApiRequestListener {

    companion object{
        // PlaceIdのリクエスト(TextSearch)に用いるBASE_URL (FieldでPlaceIdのみ指定した場合、リクエストは無料)
        private const val PLACE_ID_REQUEST = "https://maps.googleapis.com/maps/api/place/findplacefromtext/"
        // PlaceDetailsリクエストに用いるBASE_URL (従量課金)
        private const val PLACE_DETAILS_REQUEST: String = "https://maps.googleapis.com/maps/api/place/details/"
        // PlacePhotoリクエストに用いるBASE_URL (従量課金)
        private const val PLACE_PHOTO_REQUEST: String = "https://maps.googleapis.com/maps/api/place/photo?"
        // StreetView画像メタデータリクエストに用いるBASE_URL (リクエストは無料)
        private const val STREET_VIEW_META_REQUEST: String = "https://maps.googleapis.com/maps/api/streetview/metadata?"

        // PlacePhotoで取得する画像の最大サイズ
        private const val MAX_HEIGHT_PX: Int = 500
        private const val MAX_WIDTH_PX: Int = 500
        // PlacePhotoで取得する画像の最大枚数
        private const val MAX_DISPLAY_IMAGE_COUNT: Int = 10

        private val TAG = ShopDetailFragment::class.java.simpleName
    }

    private val viewModel: DataShareViewModel by lazy {
        ViewModelProvider(requireActivity())[DataShareViewModel::class.java]
    }

    private var _fragmentShopDetailBinding: FragmentShopDetailBinding? = null
    private val fragmentShopDetailBinding
        get() = _fragmentShopDetailBinding!!

    // StreetView
    private lateinit var streetViewPanoramaFragment: SupportStreetViewPanoramaFragment
    private lateinit var streetViewPanorama: StreetViewPanorama

    private lateinit var gourmetShopInfo: Shop // 詳細画面に表示する店舗情報(グルメサーチapiから取得)
    private lateinit var googlePlaceDetail: PlaceDetailsRequest // 詳細画面に表示する店舗情報(GooglePlaceDetailsから取得)
    private lateinit var streetViewPanoramaId: String // 店舗住所のStreetViewを表示するために必要なID

    // 店舗詳細画面のRecyclerView
    private lateinit var recyclerAdapter: ShopDetailRecyclerViewAdapter
    private lateinit var recycler: RecyclerView

    // StreetViewのLocationが一度設定されるとtrue
    private var locationSet: Boolean = false
    // StreetViewFragmentが見える状態であればtrue
    private var isStreetViewDisplayed: Boolean = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 店舗情報の取得に失敗した場合は、Fragmentを破棄
        viewModel.getShopDetailInfo()?.let{
            gourmetShopInfo = it
            Log.e(TAG, gourmetShopInfo.name.toString())
            // 店舗情報を基にGooglePlaceIdを取得
            getPlaceId(gourmetShopInfo)
            // 店舗情報を基にStreetViewMetaリクエストを行い、StreetViewが利用可能であるか確認
            getStreetViewMeta(gourmetShopInfo)
        }
        if (::gourmetShopInfo.isInitialized.not()){
            destroyFragment()
        }
        // Fragment遷移のアニメーションの設定
        val inflater = TransitionInflater.from(requireContext())
        enterTransition = inflater.inflateTransition(R.transition.slide_right)
        exitTransition = inflater.inflateTransition(R.transition.slide_right)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentShopDetailBinding =
            FragmentShopDetailBinding.inflate(inflater, container, false)
        return fragmentShopDetailBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // gourmetShopInfoを基に、UI画面を作成
        setUiElements()
        // デバイスのBackボタンが押された際のコールバックを設定
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            destroyFragment()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        detachUiListeners()
        _fragmentShopDetailBinding = null
    }

    /**
     * 店舗の名前と住所を基に、店舗を一意に識別するGoogleのPlaceIdを取得
     * PlaceIdは、電話番号やレビュー等の情報にアクセスする際に必要となる
     * [info] 詳細画面に表示する店舗情報(グルメサーチapi)
     */
    private fun getPlaceId(info: Shop){
        val requestUrl: String =
            "${PLACE_ID_REQUEST}json?" +
                    "key=${PLACE_API_KEY}" +
                    "&fields=place_id" +
                    "&language=ja" +
                    "&input=${info.name} ${info.address}" +
                    "&inputtype=textquery"
       viewModel.getGooglePlaceId(requestUrl,this)
    }

    /**
     * [placeId]を基に、店舗の電話番号、レビュー、評価、写真をリクエスト
     */
    private fun getGoogleShopDetail(placeId: String){
        val requestUrl: String =
            "${PLACE_DETAILS_REQUEST}json?" +
                    "key=${PLACE_API_KEY}" +
                    "&fields=formatted_phone_number,rating,reviews,photos" +
                    "&language=ja" +
                    "&place_id=${placeId}"
        viewModel.getGoogleShopDetail(requestUrl, this)
    }

    /**
     * 店舗の住所を基に、StreetViewが利用可能であるか確認を行う
     */
    private fun getStreetViewMeta(shopInfo: Shop){
        val requestUrl: String =
            STREET_VIEW_META_REQUEST +
                    "key=${PLACE_API_KEY}" +
                    "&location=${shopInfo.address} ${shopInfo.name}"
        viewModel.getStreetViewMeta(requestUrl, this)
    }

    // PlaceDetailsのPhotoReferenceを基に、店舗画像のリクエストURLを作成
    private fun getPlacePhotoUrl(ref: String): String{
        return PLACE_PHOTO_REQUEST +
                    "photo_reference=${ref}" +
                    "&key=${PLACE_API_KEY}" +
                    "&maxheight=${MAX_HEIGHT_PX}"
                    //"&maxwidth=${MAX_WIDTH_PX}"
    }

    /**
     * StreetViewの初期化
     * Fragment内では、[onCreateView]でレイアウトをinflate後に呼び出す必要がある
     */
    private fun initStreetViewPanorama(){
        streetViewPanoramaFragment =
            childFragmentManager.findFragmentById(R.id.streetviewpanorama)
                    as SupportStreetViewPanoramaFragment
        streetViewPanoramaFragment.getStreetViewPanoramaAsync(this)
    }

    /*
     * StreetViewの初期化が完了した際のコールバック
     * StreetViewの切り替えButtonの作成
     */
    override fun onStreetViewPanoramaReady(panorama: StreetViewPanorama) {
        streetViewPanorama = panorama
        setStreetViewSwitch()
    }

    // viewModelが持つ店舗詳細画面の店舗IDを削除し、自らFragmentの破棄を行う
    private fun destroyFragment(){
        viewModel.onFailedToCreateShopDetailFragment()
        getParentFragmentManager().beginTransaction().remove(this).commit()
    }

    // hotpapperApiの情報を基に、UI画面の設定を行う
    private fun setUiElements() {
        // 店舗名
        fragmentShopDetailBinding.shopNameDetail.text = gourmetShopInfo.name
        // 店舗の画像（hotpapper）
        gourmetShopInfo.photo?.pc?.l?.takeIf { it.isNotEmpty() }
            ?.let {
                insertImageView(it)
            }
        /*
         * クーポンのリンクをButtonに設定
         * リンクがない場合は、Buttonアイコンを変更
         */
        gourmetShopInfo.couponUrls?.sp?.takeIf { it.isNotEmpty()}
            ?.let{url ->
                fragmentShopDetailBinding.couponAvailable.setOnClickListener{
                    openUrl(url)
                }
            }
            ?:{
                fragmentShopDetailBinding.couponAvailable.apply {
                    text = getString(R.string.no_coupon)
                    background = AppCompatResources.getDrawable(context, R.drawable.coupon_not_available)
                }
            }
        // hotpepperサイトのリンクをButtonに設定
        gourmetShopInfo.urls?.pc?.takeIf { it.isNotEmpty() }?.let{
            url ->
            fragmentShopDetailBinding.originalSite.setOnClickListener{
                openUrl(url)
            }
        }
        // RecyclerViewにその他情報を設定
        recyclerAdapter = ShopDetailRecyclerViewAdapter(ArrangeShopInfo(gourmetShopInfo).arrange())
        recycler = fragmentShopDetailBinding.shopDetailRecyclerView.apply {

            layoutManager = LinearLayoutManager(context)
            setAdapter(recyclerAdapter)
        }
    }

    /**
     * [googlePlaceDetail]のPhotoReferencesを基に、
     *   店舗に関連する画像を表示する
     * 表示する画像の数については、[MAX_DISPLAY_IMAGE_COUNT]で指定
     */
    private fun setPlacePhotos(placeDetails: PlaceDetailsRequest){
        placeDetails.result?.photos?.let{
            for ((counter, ref) in it.withIndex()){
                insertImageView(getPlacePhotoUrl(ref.photoReference))
                if (counter >= MAX_DISPLAY_IMAGE_COUNT) break
            }
        }
    }

    /**
     * [fragmentShopDetailBinding]の
     *   HorizontalScrollViewに[url]で指定された画像を追加
     */
    private fun insertImageView(url: String){
        val image: ImageView = ImageView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, // width
                ViewGroup.LayoutParams.MATCH_PARENT // height
            ).apply {
                setPadding(3, 0, 3, 0) // 水平padding
            }
        }
        Glide.with(requireContext()).load(url).into(image).apply {
            fragmentShopDetailBinding.shopImagesHolder.addView((image))
        }
    }

    // urlを開く
    private fun openUrl(url: String){
        val link = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(link)
    }

    // ダイアルButtonの設定
    private fun setPhoneNumber(phoneNumber: String){
        fragmentShopDetailBinding.callNumber.apply{
            visibility = View.VISIBLE
            setOnClickListener{
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber"))
                startActivity(intent)
            }
        }
    }

    /*
     * StreetViewと店舗画像の切り替えButtonの設定
     * StreetViewMetaの取得と、StreetViewの初期化が完了した後に実行
     */
    private fun setStreetViewSwitch(){
        fragmentShopDetailBinding.streetviewSwitch.apply {
            visibility = View.VISIBLE
            setOnClickListener{
                // Fragmentが初期化されていない,もしくはアタッチされていない場合は、Buttonを無効化
                if (::streetViewPanoramaFragment.isInitialized.not()
                    || streetViewPanoramaFragment.isAdded.not())
                {
                    visibility = View.GONE

                    CustomDialog.create(true,
                        "エラー",
                        "ストリートビューの取得に失敗しました",
                        "閉じる",
                        "閉じる")
                    return@setOnClickListener
                }
                // 位置の指定は課金が発生するため、一度限り
                if (locationSet.not()){
                    locationSet = true
                    setStreetViewLocation()
                }

                // StreetViewが非表示の場合
                if (isStreetViewDisplayed.not()){
                    isStreetViewDisplayed = isStreetViewDisplayed.not()
                    // StreetViewを表示
                    childFragmentManager.beginTransaction()
                        .show(streetViewPanoramaFragment)
                        .commit()
                    // 店舗写真Layoutを非表示にする
                    fragmentShopDetailBinding.shopImagesView.visibility = View.INVISIBLE
                    // Buttonを切り替え
                    text = getString(R.string.image_mode)
                    background = AppCompatResources.getDrawable(context, R.drawable.street_view_switch_off)
                }
                // StreetViewが表示されている場合
                else {
                    isStreetViewDisplayed = isStreetViewDisplayed.not()
                    // StreetViewを非表示
                    childFragmentManager.beginTransaction()
                        .hide(streetViewPanoramaFragment)
                        .commit()
                    // 店舗写真Layoutを表示する
                    fragmentShopDetailBinding.shopImagesView.visibility = View.VISIBLE
                    // Buttonを切り替え
                    text = getString(R.string.street_view_mode)
                    background = AppCompatResources.getDrawable(context, R.drawable.street_view_switch_on)
                }
            }
        }
    }

    /**
     * StreetViewに[streetViewPanoramaId]のViewを設定
     * 呼び出し毎に課金が発生するため、ユーザーが StreetViewButton をクリックしたタイミングで、一度だけ実行
     */
    private fun setStreetViewLocation(){
        if (::streetViewPanorama.isInitialized.not() ||
            ::streetViewPanoramaId.isInitialized.not()) return

        streetViewPanorama.isUserNavigationEnabled = false // ユーザーに別のパノラマに移動することを許可しない
        streetViewPanorama.setPosition(streetViewPanoramaId)
    }

    // UIリスナーの破棄
    private fun detachUiListeners(){
        if (_fragmentShopDetailBinding == null) return
        fragmentShopDetailBinding.couponAvailable.setOnClickListener(null)
    }

    /**
     * [getPlaceId]のリクエストに成功
     * 取得した[placeId]を元に、PlaceDetailsリクエストを行う
     */
    override fun onRequestSucceed(placeId: String) {
        getGoogleShopDetail(placeId)
    }

    /**
     * [getPlaceId]のリクエストに失敗
     * Googleの情報は使用せずに、hotpepperApiの情報だけで画面設定を行う
     */
    override fun onRequestFailed(error: String, data: PlaceIdRequest?) {
        Log.e(TAG, error)
    }

    /**
     * [getGoogleShopDetail]リクエストに成功し、店舗の詳細情報の取得に成功
     * 情報を基に、ストリートビューの設定, UI画面の更新を行う
     */
    override fun onRequestSucceed(data: PlaceDetailsRequest) {
        googlePlaceDetail = data
        setPlacePhotos(googlePlaceDetail)

        // 電話番号を持っていれば、ダイアルButtonの設定を行う
        googlePlaceDetail.result?.formattedPhoneNumber?.takeIf { it.isNotEmpty() }
            ?.let{ setPhoneNumber(it) }
    }

    /**
     * [getGoogleShopDetail]リクエストに失敗
     * Googleの情報は使用せずに、hotpepperApiの情報だけで画面設定を行う
     */
    override fun onRequestFailed(error: String, data: PlaceDetailsRequest?) {
        Log.e(TAG, error)
    }

    /**
     * [getStreetViewMeta]リクエストに成功したので、panoIdを変数に格納
     *   StreetViewFragmentの初期化を行う
     */
    override fun onRequestSucceed(data: StreetViewMetadata) {
        data.panoId?.let { streetViewPanoramaId = it }
        initStreetViewPanorama()
    }

    /**
     * [getStreetViewMeta]リクエストに失敗
     * StreetViewのMetaデータの取得に失敗したので、StreetView表示Buttonを無効化する
     */
    override fun onRequestFailed(error: String, data: StreetViewMetadata?) {
        fragmentShopDetailBinding.streetviewSwitch.visibility = View.GONE
    }
}