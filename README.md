<div id="top"></div>

## 使用技術
<p style="display: inline">
  <img src="https://img.shields.io/badge/-Androidstudio-34A853.svg?logo=androidstudio&style=flat&logoColor=white">  <img src="https://img.shields.io/badge/-Kotlin-7F52FF.svg?logo=kotlin&style=flat&logoColor=white">  
  <img src="https://img.shields.io/badge/-GoogleMaps-4285F4.svg?logo=googlemaps&style=flat&logoColor=white">
</p>

# Gourtto (グルっと)  
 
ホットペッパーグルメWEBサービスを利用した、グルメ検索アプリ。  
現在位置、あるいは指定した地点を中心に、周囲のお店を検索する機能を持っている。  
一番のポイントは、アプリ内でお店周辺のストリートビューを観れる点で、お店の料理だけでなく立地や外観も手軽に確認することができる。  
これにより、外出先で近くのお店を素早く検索し、お店選びに必要な情報をこのアプリ一つで取得することができる。   
   
## こだわった点  
 
外出先でも素早く検索を行えることをコンセプトにしており、それを実現するためにボトムシートを採用し、MAPとグルメ情報のスムーズな移動を実現した。  
また、**初めて入る居酒屋は外観で判断する 63.6% (なんでも酒や カクヤス調べ)** のデータを参考に、お店の立地、外観を手軽に確認する目的で、ストリートビューを取り入れた。  
UIUXの面では、ユーザーがストレスなく操作できるよう、アニメーションやプログレスバーを使用し、画面が固まって動かなくなる時間がなくなるよう設計を行った。  
また、テーマカラーには暖色を使用した点や、お店の写真をGoogleAPIから追加で取得した点において、ユーザーの食欲を刺激するようなデザインを意識した。  
  
参考:**初めて入る居酒屋は外観で判断する(なんでも酒や カクヤス調べ) https://kakulabo.jp/serial/kl20230726.html**

## 使用例 
 
https://github.com/Aburaya5123/Gourtto/assets/166899082/88d7261c-2e21-4300-9d32-c01165596012  
 
## 画面構成 
 
### 検索画面 / 検索結果画面
<img src="https://github.com/Aburaya5123/Gourtto/assets/166899082/37730d6a-e465-4d8a-8f5c-099259eb58a5" width=330>
<img src="https://github.com/Aburaya5123/Gourtto/assets/166899082/f5b0eaad-3110-42ca-beec-61b1b63f880c" width=330>  
 
### お店詳細画面 / ストリートビュー画面
<img src="https://github.com/Aburaya5123/Gourtto/assets/166899082/3076f289-988c-4ef7-b203-fcf99ae3cb66" width=330>
<img src="https://github.com/Aburaya5123/Gourtto/assets/166899082/17740899-86a6-42bf-9d2f-301d294876db" width=330>  
 
### 周囲にお店が見つからなかった際の画面
<img src="https://github.com/Aburaya5123/Gourtto/assets/166899082/b6ec67fc-e312-4966-8e4b-7421a06c81c3" width=330>  
 
### ネットワーク未接続の際はユーザーに通知を行う 
### GPSがオフの場合は、設定画面を開くボタンを通知する
<img src="https://github.com/Aburaya5123/Gourtto/assets/166899082/fe6a5770-aacb-40c9-8c67-5c5b181e7cbf" width=330>
<img src="https://github.com/Aburaya5123/Gourtto/assets/166899082/7a04a0a2-bd03-4460-aba2-38e7584e15bc" width=330>  

 
##  使用方法 
 
使用に際して、ホットペッパーグルメWEBサービスのAPIキーと、GoogleCloudサービスのAPIキーが必要です。  
キーの入力場所は、rootディレクトリの'local.properties'です。  
GoogleCloudAPIキーを制限して利用する際には、必ず下記のAPIに許可を与えてください。  
- Places API  
- Street View Static API  
- Maps SDK for Android  
