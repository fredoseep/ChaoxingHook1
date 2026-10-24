package com.fredoseep.chaoxinghook;

/** DexKit 匹配失败时使用的混淆类名、字段名回退表。 */
final class ObfuscationMap {

    // 以下均为"当前版本已知名"，仅作 DexKit 结构匹配失败时的回退；方法名不做依赖
    public static final String CLASS_SPLASH_VIEW_MODEL = "com.chaoxing.mobile.activity.SplashViewModel";
    public static final String METHOD_SPLASH_A = "a";
    public static final String CLASS_HOME_PAGE_HEADER = "com.chaoxing.mobile.study.home.mainpage.view.HomePageHeader";
    public static final String METHOD_HOME_HEADER_G = "g";
    public static final String CLASS_CATEGORY_HOLDER = "com.chaoxing.mobile.study.home.mainpage2.adapter.viewholder.MainRecordCategoryHolder";
    public static final String METHOD_CATEGORY_HOLDER_O = "o";
    public static final String FIELD_CATEGORY_HOLDER_TV_LEFT = "c";
    public static final String CLASS_MAIN_PAGE_RECORD_ADAPTER = "com.chaoxing.mobile.study.home.mainpage2.adapter.MainPageRecordAdapter";
    public static final String METHOD_ADAPTER_GET_ITEM_COUNT = "getItemCount";
    public static final String FIELD_ADAPTER_LIST_A = "a";
    public static final String FIELD_ADAPTER_LIST_F82688A = "f82688a";
    public static final String CLASS_DB_QUERY = "y6e"; // 7.0.1（6.7.8 为 zo.b0，顶级混淆包，勿加包前缀）
    public static final String METHOD_DB_QUERY_W = "W";
    public static final String CLASS_CHAT_MANAGER = "com.chaoxing.mobile.chat.manager.b"; // 7.0.1（6.7.8 为 chat.manager.q1）
    public static final String CLASS_EM_CMD_MESSAGE_BODY = "com.hyphenate.chat.EMCmdMessageBody";
    public static final String METHOD_EM_CMD_ACTION = "action";
}
