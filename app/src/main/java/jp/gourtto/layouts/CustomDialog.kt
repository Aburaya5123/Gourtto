package jp.gourtto.layouts

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import jp.gourtto.R


/**
 * カスタムダイアログを作成するクラス
 * インスタンスはシングルトンで作成し、その際にBundleで引数を渡す
 */
class CustomDialog: DialogFragment(){

    /**
     * ユーザーの選択によって行う分岐処理を実装するインターフェイス
     * [onNegativeClicked]は、[justNotify]がfalseの時のみ呼び出される
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
         * シングルトンでインスタンス作成
         * [justNotify] ユーザーによる選択が不要な、通知のみを行う場合は true
         *                trueの場合は、NegativeButtonが削除される
         * [title] 通知タイトル
         * [body] 通知本文
         * [positive] 肯定的な選択肢のテキスト
         * [negative] 否定的な選択肢のテキスト
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

    private var justNotify: Boolean = false
    private lateinit var title: String
    private lateinit var body: String
    private lateinit var positive: String
    private lateinit var negative: String


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
        }
        if (justNotify.not()){
            negButton.setOnClickListener {
                listener?.onNegativeClicked(this)
            }
        }
        /**
         * 通知のみ行う場合は、NegativeButtonは不要なので削除
         */
        if (justNotify){
            val viewGroup = negButton.parent as ViewGroup
            viewGroup.removeView(negButton)
        }

        return dialog
    }
}