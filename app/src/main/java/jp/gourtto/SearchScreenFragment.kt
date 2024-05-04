package jp.gourtto

import android.os.Bundle
import android.transition.TransitionInflater
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.Navigation
import jp.gourtto.databinding.FragmentSearchScreenBinding
import jp.gourtto.gourmetapi.Gourmet
import jp.gourtto.gourmetapi.GourmetGenre
import jp.gourtto.gourmetapi.GourmetViewModel
import jp.gourtto.layouts.ExpandableListAdapter


/**
 * apiへのリクエスト結果に対応するリスナー
 * SearchingFragmentにて実装し、viewModelから呼び出される
 */
interface ApiRequestListener{
    fun onRequestSucceed(data: GourmetGenre)
    fun onRequestFailed(error: String, data: GourmetGenre?)

    fun onRequestSucceed(data: Gourmet?)
    fun onRequestFailed(error: String, data: Gourmet?)
}

/**
 * 検索画面のViewを作成するFragment
 */
class SearchScreenFragment : Fragment(), ApiRequestListener {

    companion object {
        /**
         * ExpandableListViewにおける、タイトル'ジャンルを選択'(@string/param2_title1)
         *   に該当するGroupのIndex (現在は 0 )
         */
        private const val GENRE_INDEX_ON_LISTVIEW: Int = 0
        // Apiからの最大レスポンス件数
        private const val MAX_OUTPUT_COUNT = 100

        private val TAG = SearchScreenFragment::class.java.simpleName
    }

    private var _fragmentSearchingBinding: FragmentSearchScreenBinding? = null
    private val fragmentSearchingBinding
        get() = _fragmentSearchingBinding!!

    private lateinit var myAdapter: ExpandableListAdapter

    private val viewModel: GourmetViewModel by lazy {
        ViewModelProvider(requireActivity())[GourmetViewModel::class.java]
    }

    // retrofitに渡し、リクエストに追加するクエリパラメータ <Key, Value>
    private lateinit var genreMap: Map<String,String>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Fragment遷移のアニメーションの設定
        val inflater = TransitionInflater.from(requireContext())
        enterTransition = inflater.inflateTransition(R.transition.slide_left)
        exitTransition = inflater.inflateTransition(R.transition.slide_left)
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentSearchingBinding =
          FragmentSearchScreenBinding.inflate(inflater, container, false)
        return fragmentSearchingBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // UIのリスナーを設定
        addUiListeners()

        // ジャンルマスタapiへのリクエスト実行
        viewModel.getGenreParams(this)
    }

    override fun onDestroyView(){
        super.onDestroyView()
        // UIのリスナーを破棄
        removeUiListeners()
        _fragmentSearchingBinding = null
    }

    // Uiのリスナーを追加
    private fun addUiListeners(){
        fragmentSearchingBinding.searchingButton.setOnClickListener {
            onSeachingButtonClicked() }
    }

    // Uiのリスナーを破棄
    private fun removeUiListeners(){
        fragmentSearchingBinding.searchingButton.setOnClickListener(null)
    }

    /*
     * 検索ボタンが押された際のリスナー
     * 各種設定項目の値を取得し、クエリパラメータMap<Key,Value>に変換後、グルメApiServiceを呼び出す
     */
    private fun onSeachingButtonClicked(){

        val queryParameter: MutableMap<String, String> = mutableMapOf()

        // 検索範囲パラメータの追加
        queryParameter["range"] = getSearchingRadius().toString()
        // ジャンルパラメータの追加
        for (code in getSelectedGenres(GENRE_INDEX_ON_LISTVIEW)){
            queryParameter["genre"] = code
        }
        // 最大検索件数の設定
        queryParameter["count"] = MAX_OUTPUT_COUNT.toString()

        // リクエスト送信
        viewModel.getSearchResults(this, queryParameter, null)
    }

    /**
     * 検索範囲のRadioButtonから選択されている要素を取得
     * apiの検索範囲パラメータは300m, 500m, 1000m, 2000m, 3000m の順に、1, 2, 3, 4, 5 が対応するので、
     *   戻り値は index + 1 の値となる
     * ExpandableListViewにヘッダーとして後から追加(layout/expandable_list_view_header.xml)しているため、
     *   viewBindingは使用できない
     */
    private fun getSearchingRadius(): Int{
        var buttonIndex: Int = 0
        view?.findViewById<ViewGroup>(R.id.param1_options)?.let {group ->
            for (radioButton in group.children){
                (radioButton as? RadioButton)?.takeIf {radio ->
                    radio.isChecked
                }?.let{
                    buttonIndex = group.indexOfChild(it)
                }
            }
        }
        return buttonIndex + 1
    }

    /**
     * [ExpandableListAdapter]から現在選択されているジャンル名を取得
     * ジャンルマスターapiの結果を格納した[genreMap]を参照し、クエリパラメータ用のコードを取得する
     * 戻り値[params]には、お店ジャンルコード が文字列で格納されている
     */
    private fun getSelectedGenres(groupPosition: Int):List<String>{

        val params: MutableList<String> = mutableListOf()

        myAdapter.returnSelectedGenres()?.let{ indexes ->
            for (index in indexes){
                genreMap[index.value]?.let { params.add(it) }
            }
        }
        return params
    }

    // ジャンルマスタapiへのリクエストに成功し、データを取得した際のリスナー
    override fun onRequestSucceed(data: GourmetGenre) {
        if (data.results.resultsAvailable==0){
            // 該当なし
            displayNoGenreParameter()
            return
        }
        // キー:name, 値:code　としたMapを作成
        genreMap = data.results.genre?.let { genre ->
            genre.associateBy({ it.name }, { it.code })
        }!!
        displayGenreParameters(genreMap.keys.toList())
    }

    /**
     * グルメサーチapiへのリクエストに成功した際のリスナー
     * レスポンス結果はviewModelが保持
     * ここでは、NavigationでSearchingResultsFragmentへの遷移を行うだけなので、
     *   [data]は null を受け取る
     */
    override fun onRequestSucceed(data: Gourmet?) {
        Navigation.findNavController(requireActivity(), R.id.nav_host_fragment)
            .navigate(R.id.action_searchScreenFragment_to_searchResultsFragment)
    }

    // ジャンルマスタapiへのリクエストに失敗した際のリスナー
    override fun onRequestFailed(error: String, data: GourmetGenre?) {
        Log.e(TAG, error)
    }

    // グルメサーチapiへのリクエストに失敗した際のリスナー
    override fun onRequestFailed(error: String, data: Gourmet?) {
        Log.e(TAG, error)
    }

    /*
     * ジャンルマスタapiから取得したジャンル名をExpandableListViewに表示
     * ヘッダーとして、検索範囲設定のレイアウトも追加する
     */
    private fun displayGenreParameters(genreKeys: List<String>){
        // <'Group名', List<'ジャンル名', 'ジャンルコード'>>
        val genre: Map<String,List<String>>
            = mapOf(resources.getString(R.string.param2_title1) to genreKeys)
        myAdapter = ExpandableListAdapter(requireContext(), genre)
        fragmentSearchingBinding.expandableview.apply{
            // ヘッダーの追加
            addHeaderView(layoutInflater.inflate(R.layout.expandable_list_view_header, null))
            setAdapter(myAdapter)
        }
    }

    // ジャンルマスタapiから取得したジャンル名が0件であった場合
    private fun displayNoGenreParameter(){ }
}
