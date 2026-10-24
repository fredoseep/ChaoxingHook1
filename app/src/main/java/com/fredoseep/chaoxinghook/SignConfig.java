package com.fredoseep.chaoxinghook;

/** 宿主 Hook 使用的配置快照；字段与现有配置文件保持一致。 */
final class SignConfig {

    boolean modifyLocation = false;
    String longitude = "";
    String latitude = "";
    boolean modifyAddress = false;
    String address = "";
    boolean modifyName = false;
    String name = "";
    boolean randomizeDeviceFlag = false;
    boolean autoCalculateLocation = false;
    boolean autoGestureSign = false;
    boolean autoCodeSign = false;
    boolean bypassExamCheat = true;
    boolean enableCopyRestriction = true;
    boolean replaceExamScreenshot = false;
    String fakeImagePath = "";
}
