package com.dji.MapDemo.GoogleMap;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.multidex.MultiDex;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;

import dji.sdk.base.BaseComponent;
import dji.sdk.base.BaseProduct;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.common.error.DJIError;
import dji.common.error.DJISDKError;

public class DJIDemoApplication extends  Application
{
    @Override
    public  void onCreate()
    {
        super.onCreate();
    }

    protected  void attachBaseConext (Context base)
    {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }
}
