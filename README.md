免责声明 / Disclaimer
中文
本软件仅供技术学习与交流使用。使用者须自行确保其使用行为符合所在国家和地区的法律法规。
因使用本软件产生的一切法律纠纷、经济损失及其他后果，由使用者自行承担全部责任，与本软件开发者无关。
开发者不对使用者的任何行为提供担保或背书，亦不对使用者的使用方式承担任何直接或间接责任。
下载、安装或使用本软件即视为已阅读并同意本声明全部内容。如不同意，请立即卸载并删除本软件。
中华人民共和国用户特别提示
根据《中华人民共和国刑法》《中华人民共和国治安管理处罚法》《中华人民共和国网络安全法》等相关法律法规：

伪造位置信息用于欺诈可能构成诈骗罪（刑法第266条）
干扰企事业单位考勤管理系统可能构成破坏计算机信息系统罪（刑法第286条）
利用虚假定位逃避监管或干扰公共服务可能面临行政处罚或刑事追诉
伪造运动数据以获取不正当利益可能构成不当得利或违反平台服务协议
请务必在合法合规的前提下使用本软件。


This is a lightweight enhancement module for the Chaoxing (Xuexitong) application based on the Xposed/LSPosed framework. It is designed to provide a cleaner, more efficient learning environment by removing intrusive advertisements and optimizing the layout for productivity.


!Only for root user sadge. I've tried lspatch and sandbox both failed. If you have a better idea please leave a message.

Key Features
Bypass Splash Advertisements: Suppresses launch-screen ads by intercepting SplashViewModel and PromotionView logic, significantly reducing application startup time.

Remove Home Page Banners: Hides the scrolling advertisement banners in the home page header to focus on core functional areas.

Disable Recommended Feeds: Blocks the infinite "Recommended" post stream at the bottom of the home page, which often includes distracting social-media-style content.

Expand Recent Records: Overrides the hardcoded limit of "Recently Used" items, increasing the visible count from 3 up to 10 to facilitate faster access to frequent resources.

Remove ads in the message page

Support single device multi-account-signin. You can simply change a singlular char at the fingerprint key in the config file to make it different. Just keep the base64 formatt.

！！！！！！！！！！！！！！！！！！！！！

How to do the spoof location check in?
You don't need a UI to configure this. The module dynamically reads your desired coordinates from a local text file.

Open your Android file manager.

Navigate to the app's private external directory:
/storage/emulated/0/Android/data/com.chaoxing.mobile/files/

Once you have sent a checkin request, there should be a config file named as chaoxing_loc.txt .

Open the file and enter your target coordinates in the latitude,longitude format. (English comma only!!)
Config the specified switch.
Name and address keys can be shown on the teachers' side . You can use front end syntax to set their appearance but with certain limitation.



Save the file. The module will automatically read this file the next time you initiate a sign-in.

To Disable the Feature:
If you want to use your real location temporarily, simply change the text file content to -1,-1. The module will let the original request pass through untouched.

⚠️ Important Caveat: Map vs. Text Address
Please be aware of how the server handles this specific request:
If the sign-in initiator (e.g., your teacher) clicks into your specific sign-in details, the pinned location on the map will show your modified (fake) coordinates, but the text-based address displayed below it will still show your REAL address (derived from IP or underlying base stations).

So remember to change the address string when you spoof the location


Deep UI Cleanup: Automatically hides empty containers and specific title headers (such as the "Recommended" section) to ensure a seamless, native-looking interface.

🛑 Disclaimer
For Educational and Research Purposes Only. This module and its source code are provided strictly for learning about Android WebView request interception, HttpURLConnection proxying, and reverse engineering concepts. Please do not use this tool for illegal purposes, academic misconduct, or to violate the terms of service of any application. The author assumes no responsibility for any consequences arising from the use of this code.


