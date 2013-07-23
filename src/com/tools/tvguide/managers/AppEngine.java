package com.tools.tvguide.managers;

import com.tools.tvguide.activities.R.id;
import com.tools.tvguide.components.VersionController;

import android.content.Context;

public class AppEngine 
{
    private static final String                     TAG                         = "AppEngine";
    private static AppEngine                        mInstance                   = new AppEngine();
    private Context                                 mContext;
    private Context                                 mApplicationContext;
    private CollectManager                          mUserSettingManager;
    private LoginManager                            mLoginManager;
    private ContentManager                          mContentManager;
    private CacheManager                            mCacheManager;
    private AlarmHelper                             mAlarmHelper;
    private UrlManager                              mUrlManager;
    private DnsManager                              mDnsManager;
    private UpdateManager                           mUpdateManager;
    
    /********************************* Manager定义区，所有受AppEngine管理的Manger统一定义 **********************************/
    
    public static AppEngine getInstance()
    {
        return mInstance;
    }
    
    public void setContext(Context context)
    {
        mContext = context;
    }

    public void setApplicationContext(Context context)
    {
        mApplicationContext = context;
    }

    public Context getApplicationContext()
    {
        return mApplicationContext;
    }

    public Context getContext()
    {
        return mContext;
    }
    
    public CollectManager getCollectManager()
    {
        if (mUserSettingManager == null)
            mUserSettingManager = new CollectManager(mContext);
        return mUserSettingManager;
    }
    
    public LoginManager getLoginManager()
    {
        if (mLoginManager == null)
            mLoginManager = new LoginManager(mContext);
        return mLoginManager;
    }
    
    public ContentManager getContentManager()
    {
        if (mContentManager == null)
            mContentManager = new ContentManager(mContext);
        return mContentManager;
    }
    
    public CacheManager getCacheManager()
    {
        if (mCacheManager == null)
            mCacheManager = new CacheManager(mContext);
        return mCacheManager;
    }
    
    public AlarmHelper getAlarmHelper()
    {
        if (mAlarmHelper == null)
            mAlarmHelper = new AlarmHelper(mContext);
        return mAlarmHelper;
    }
    
    public UrlManager getUrlManager()
    {
        if (mUrlManager == null)
            mUrlManager = new UrlManager(mContext);
        return mUrlManager;
    }
    
    public DnsManager getDnsManager()
    {
        if (mDnsManager == null)
            mDnsManager = new DnsManager(mContext);
        return mDnsManager;
    }
    
    public UpdateManager getUpdateManager()
    {
        if (mUpdateManager == null)
            mUpdateManager = new UpdateManager(mContext);
        return mUpdateManager;
    }
    
    public void prepareBeforeExit()
    {
        if (mUserSettingManager != null)
            mUserSettingManager.shutDown();
        
        if (mAlarmHelper != null)
            mAlarmHelper.shutDown();
    }
    
}
