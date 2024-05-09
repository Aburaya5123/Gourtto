package jp.gourtto.layouts

import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import jp.gourtto.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume


/**
 * カスタムダイアログを作成するクラス
 * インスタンス作成後、[waitAsync]を呼ぶことで、ユーザーがダイアログの操作を行うまで待つことが可能
 *
 * キャンセル操作は無効化済み
 */
class CustomDialog: DialogFragment(){

    /**
     * ユーザーの選択によって実行する、分岐処理を実装するインターフェイス
     * [onNegativeClicked]は、[justNotify]がfalseの場合のみ呼び出される
     */
    interface CustomDialogListener{
        fun onPositiveClicked(dialog: CustomDialog){
            dialog.dismiss()
        }
        fun onNegativeClicked(dialog: CustomDialog){
            dialog.dismiss()
        }
    }

    companion object{
        /**
         * インスタンス作成
         * [justNotify] 通知のように、ユーザーによる選択が不要な場合は true
         *                trueの場合は、NegativeButtonが削除される
         * [title] 通知タイトル
         * [body] 通知本文
         * [positive] 肯定的な選択肢のボタンに表示するテキスト
         * [negative] 否定的な選択肢のボタンに表示するテキスト
         */
        fun create(justNotify: Boolean, title: String, body: String,
                   positive: String, negative: String, listener: CustomDialogListener?=null): CustomDialog {
            return CustomDialog().apply {
                val bundle = Bundle().apply{
                    putBoolean("JustNotify", justNotify)
                    putString("Title", title)
                    putString("Body", body)
                    putString("Positive", positive)
                    putString("Negative", negative)
                }
                arguments = bundle

                if (listener==null){
                    this.listener = object: CustomDialogListener {}
                }
                else{
                    this.listener = listener
                }
            }
        }
        private val TAG = CustomDialog::class.java.simpleName
    }

    private var listener: CustomDialogListener? = null
    /*
     * trueの場合は、negativeButtonを削除
     * 目的がユーザーに通知を行う場合に使用
     */
    private var justNotify: Boolean = false
    private lateinit var title: String
    private lateinit var body: String
    private lateinit var positive: String
    private lateinit var negative: String
    /*
     * ダイアログに対して操作があった際に値が入力される
     *  positive -> 1
     *  negative -> 0
     *  dismiss -> -1
     */
    private val _buttonClickLiveData = MutableLiveData<Int>()
    private val buttonClickLiveData: LiveData<Int> = _buttonClickLiveData


    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        arguments?.let {
            justNotify =  it.getBoolean("JustNotify", false)
            title = it.getString("Title", "通知")
            body = it.getString("Body", "")
            positive = it.getString("Positive", "確認")
            negative = it.getString("Negative", "閉じる")
        }
        val posButton: Button
        val negButton: Button

        val dialog = Dialog(requireContext()).apply {
            // ダイアログを透過状態に変更
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setContentView(R.layout.custom_notification_dialog)
            // テキストの設定
            findViewById<TextView>(R.id.dialog_title).text = title
            findViewById<TextView>(R.id.dialog_body).text = body
            posButton = this.findViewById(R.id.notification_positive)
            negButton = this.findViewById(R.id.notification_negative)
            posButton.text = positive
            negButton.text = negative
        }

        // Buttonにリスナーを設定
        posButton.setOnClickListener {
            listener?.onPositiveClicked(this)
            _buttonClickLiveData.value = 1
        }
        if (justNotify.not()){
            negButton.setOnClickListener {
                listener?.onNegativeClicked(this)
                _buttonClickLiveData.value = 0
            }
        }
        /**
         * 通知のみ行う場合は、NegativeButtonは不要なので削除
         */
        if (justNotify){
            val viewGroup = negButton.parent as ViewGroup
            viewGroup.removeView(negButton)
        }
        /**
         * キャンセル操作の無効化
         */
        this.isCancelable = false

        return dialog
    }

    // ボタン操作以外での終了
    override fun onCancel(dialog: DialogInterface) {
        _buttonClickLiveData.value = -1
        super.onCancel(dialog)
    }

    /**
     * [buttonClickLiveData]の値が変動(ダイアログに対するアクションを取得)するまで待機
     */
    suspend fun waitAsync(fragmentManager: FragmentManager, tag:String): Int{
        show(fragmentManager, tag)
        return buttonClickLiveData.await()
    }

    // LiveDataの値が変更されるまで待機
    private suspend fun LiveData<Int>.await(): Int {
        return withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                val observer = object : Observer<Int> {
                    override fun onChanged(value: Int) {
                        removeObserver(this)
                        continuation.resume(value)
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