package jp.gourtto.fragments

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.transition.TransitionInflater
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.Navigation
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import jp.gourtto.R
import jp.gourtto.databinding.FragmentSearchResultsBinding
import jp.gourtto.gourmet_api.DataShareViewModel
import jp.gourtto.gourmet_api.Shop
import jp.gourtto.layouts.RecyclerClickListener
import jp.gourtto.layouts.ShopListRecyclerViewAdapter


/**
 * 検索結果の一覧表示画面の作成を行うFragment
 */
class SearchResultsFragment : Fragment(), RecyclerClickListener {

    companion object{
        // 一度に表示する検索結果の件数
        private const val RESULTS_PER_PAGE: Int = 7
    }

    private var _fragmentSearchResultsBinding: FragmentSearchResultsBinding? = null
    private val fragmentSearchResultsBinding
        get() = _fragmentSearchResultsBinding!!

    private val viewModel: DataShareViewModel by lazy {
        ViewModelProvider(requireActivity())[DataShareViewModel::class.java]
    }
    // RecyclerViewのインスタンスとAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var recyclerAdapter: ShopListRecyclerViewAdapter
    // SearchScreenFragmentに戻るボタン
    private lateinit var backButton: Button

    /**
     * 全ての検索結果が格納されたList
     * [RESULTS_PER_PAGE]件毎にListに分割
     */
    private lateinit var searchResults: List<List<Shop>>

    // RecyclerViewで出力するデータのみをここに格納
    private lateinit var output: MutableList<Shop>

    private var currentPage: Int = 0 // 現在のページ数
    private var pageNum: Int = 0 // 合計のページ数 -> searchResults.sizeと一致
    private var loading: Boolean = false // 検索結果の追加ロード中であればtrue


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val inflater = TransitionInflater.from(requireContext())
        // Fragment遷移のアニメーションの設定
        enterTransition = inflater.inflateTransition(R.transition.slide_right)
        exitTransition = inflater.inflateTransition(R.transition.slide_right)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentSearchResultsBinding =
            FragmentSearchResultsBinding.inflate(inflater, container, false)
        return fragmentSearchResultsBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        backButton = requireActivity().findViewById(R.id.back_to_search_screen)
        // デバイスのBackボタンが押された際のコールバックを設定
       requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
           onBackToSearchScreen()
           isEnabled = false
           requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        addUiListeners()

        /**
         * アプリの復帰時に、viewModelが検索結果のデータを保持していなければ、SearchScreenFragmentに戻る
         */
        if (viewModel.hasSearchData().not()){
            onBackToSearchScreen()
            return
        }
        // 検索結果の取得に成功した場合、Uiの更新を行う
        if (receiveSearchResult()){
            setSearchResults(searchResults)
        }
    }

    override fun onDestroyView(){
        super.onDestroyView()
        detachUiListeners()
        _fragmentSearchResultsBinding = null
    }

    /**
     * SearchResultFragmentからSearchScreenFragmentに遷移する際の処理
     * [viewModel]の検索結果件数を -1 に変更し、MainActivityのObserverでUI更新を行う
     */
    private fun onBackToSearchScreen(){
        backButton.visibility = View.INVISIBLE
        viewModel.backToSearchScreen() // 検索件数のLiveDataをリセット
    }

    private fun addUiListeners(){
        backButton.visibility = View.VISIBLE
        backButton.setOnClickListener{
            onBackToSearchScreen()
            Navigation.findNavController(requireActivity(), R.id.nav_host_fragment)
                .navigate(R.id.action_searchResultsFragment_to_searchScreenFragment, null)
        }
    }

    private fun detachUiListeners(){
        if (::backButton.isInitialized.not()) return
        backButton.setOnClickListener(null)
    }

    /*
     * viewModelから検索結果を取得
     * 結果の取得に成功するとtrueを返す
     */
    private fun receiveSearchResult(): Boolean{
        viewModel.getPagedShopList(RESULTS_PER_PAGE)?.let{
            // 検索結果が見つかったので、RecyclerViewを作成
            searchResults = it
            pageNum = searchResults.size
            resultMatched()
            return true
        } ?:{
            // 検索結果が見つからなかったので、画面表示を切替
            noMatchFound()
        }
        return false
    }

    // RecyclerViewAdapterに検索結果を渡し、Viewの作成を行う
    private fun setSearchResults(results: List<List<Shop>>){
        output = results[currentPage].toMutableList()

        recyclerAdapter = ShopListRecyclerViewAdapter(output,this)
        recycler = fragmentSearchResultsBinding.shopListRecycler.apply {
            // リスト下までスクロールした際のリスナーを設定
            addOnScrollListener(object: RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    if (loading.not() && recyclerView.canScrollVertically(1).not()
                            && pageNum > currentPage + 1){
                        loadMore()
                    }
                }
            })
            layoutManager = LinearLayoutManager(context)
            setAdapter(recyclerAdapter)
        }
    }

    // 検索結果が見つからなかった場合
    private fun noMatchFound(){
        fragmentSearchResultsBinding.resultNotFoundLayout.visibility = View.VISIBLE
    }

    // 検索結果が見つかった場合
    private fun resultMatched(){
        fragmentSearchResultsBinding.resultNotFoundLayout.visibility = View.INVISIBLE
    }

    /**
     * 画面下までスクロールしたとき、追加でViewの作成を行う
     * [output]に要素を追加し、Adapterの .notifyItemInserted()を呼ぶことで、
     *   RecyclerViewの更新が自動で行われる
     */
    private fun loadMore(){
        loading = true
        val progressB: ProgressBar = fragmentSearchResultsBinding.loadingMore
        progressB.visibility = View.VISIBLE

        Handler((Looper.getMainLooper())).postDelayed(
            {
                val lastPosition = output.size
                output.addAll(searchResults[++currentPage])
                recyclerAdapter.notifyItemInserted(lastPosition)
                loading = false
                progressB.visibility = View.GONE
            }, 1000
        )
    }

    /**
     * RecycllerViewのObjectがクリックされた際のリスナー
     * [shopId] 該当する店舗のID、このIDを基に店舗の詳細情報画面の作成を行う
     */
    override fun onRecyclerObjectClicked(shopId: String) {
        viewModel.createShopDetailFragment(shopId)
    }
}