package com.tools.tvguide.activities;

import java.io.Serializable;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import com.jeremyfeinstein.slidingmenu.lib.SlidingMenu;
import com.tools.tvguide.AdvanceAlarmActivity;
import com.tools.tvguide.R;
import com.tools.tvguide.adapters.ChannelDetailListAdapter;
import com.tools.tvguide.adapters.DateAdapter;
import com.tools.tvguide.adapters.ResultProgramAdapter;
import com.tools.tvguide.adapters.DateAdapter.DateData;
import com.tools.tvguide.components.DefaultNetDataGetter;
import com.tools.tvguide.components.MyProgressDialog;
import com.tools.tvguide.data.AlarmData;
import com.tools.tvguide.data.Channel;
import com.tools.tvguide.data.ChannelDate;
import com.tools.tvguide.data.Program;
import com.tools.tvguide.managers.AlarmHelper.AlarmListener;
import com.tools.tvguide.managers.ChannelHtmlManager.ChannelDetailCallback;
import com.tools.tvguide.managers.AppEngine;
import com.tools.tvguide.managers.CollectManager;
import com.tools.tvguide.managers.ContentManager;
import com.tools.tvguide.managers.UrlManager;
import com.tools.tvguide.utils.NetDataGetter;
import com.tools.tvguide.utils.NetworkManager;
import com.tools.tvguide.utils.ProgramUtil;
import com.tools.tvguide.utils.Utility;
import com.tools.tvguide.views.DetailLeftGuide;
import com.tools.tvguide.views.DetailLeftGuide.OnChannelSelectListener;

import android.os.Bundle;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Message;
import android.os.Vibrator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Service;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class ChannelDetailActivity extends Activity implements AlarmListener, Callback 
{
    private static final int RequestCode = 100;
    private static boolean sHasShownFirstStartTips = false;
    private static boolean sUseLocalTime = false;
    private static int sRequestId = 0;
    private String mChannelName;
    private String mChannelId;
    private List<Channel> mChannelList;
    private TextView mChannelNameTextView;
    private ListView mProgramListView;
    private ChannelDetailListAdapter mListViewAdapter;
    private Handler mUiHandler;
    
    private ListView mDateChosenListView;
    private DateAdapter mDateAdapter;
    private Timer mTimer;
    private DetailLeftGuide mLeftMenu;
    private ImageView mFavImageView;
    
    private List<Program> mProgramList;
    private Program mOnPlayingProgram;
    private MyProgressDialog mProgressDialog;
    private int mCurrentSelectedDay;
    private int mMaxDays;
    private List<ResultProgramAdapter.IListItem> mItemDataList;
    private boolean mAlarmCanSetTipsShown = false;
    
    private enum SelfMessage {MSG_UPDATE_PROGRAMS, MSG_UPDATE_ONPLAYING_PROGRAM, MSG_UPDATE_DATELIST};
    private final int TIMER_SCHEDULE_PERIOD = 3 * 60 * 1000;        // 3 minute
    private final int DEFAULT_MAX_DAYS = 7;

    @SuppressWarnings("unchecked")
    @Override
    protected void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_channel_detail);
        
        // configure the SlidingMenu
        SlidingMenu menu = new SlidingMenu(this);
//        menu.setMode(SlidingMenu.LEFT_RIGHT);
        menu.setMode(SlidingMenu.LEFT);
        menu.setTouchModeAbove(SlidingMenu.TOUCHMODE_FULLSCREEN);
        menu.setShadowWidthRes(R.dimen.shadow_width);
        menu.setShadowDrawable(R.drawable.shadow);
        menu.setBehindOffsetRes(R.dimen.slidingmenu_offset);
        menu.setFadeDegree(0.35f);
        mLeftMenu = new DetailLeftGuide(this);
        menu.setMenu(mLeftMenu);
//        menu.setSecondaryMenu(R.layout.channel_detail_right);
//        menu.setSecondaryShadowDrawable(R.drawable.shadowright);
        menu.attachToActivity(this, SlidingMenu.SLIDING_CONTENT);
        
        mUiHandler = new Handler(this);
        mChannelId = getIntent().getStringExtra("tvmao_id");
        if (mChannelId == null)
            return;
        
        mChannelName = getIntent().getStringExtra("name");
        mChannelList = (List<Channel>) getIntent().getSerializableExtra("channel_list");
        if (mChannelList == null)
        	mChannelList = new ArrayList<Channel>();
        mProgramList = new ArrayList<Program>();
        mOnPlayingProgram = new Program();
        mProgressDialog = new MyProgressDialog(this);
        mCurrentSelectedDay = Utility.getProxyDay(Calendar.getInstance().get(Calendar.DAY_OF_WEEK));
        mMaxDays = DEFAULT_MAX_DAYS;
        mItemDataList = new ArrayList<ResultProgramAdapter.IListItem>();
        AppEngine.getInstance().getAlarmHelper().addAlarmListener(this);
     
        initViews();
        updateAll();
        
        mTimer = new Timer(true);
        mTimer.schedule(new TimerTask() 
        {
            @Override
            public void run() 
            {
                updateOnplayingProgram();
            }
        }, TIMER_SCHEDULE_PERIOD, TIMER_SCHEDULE_PERIOD);
    }
    
    @Override
    public void onDestroy()
    {
        super.onDestroy();
        mTimer.cancel();
        AppEngine.getInstance().getAlarmHelper().removeAlarmListener(this);
    }
    
    @Override
    public void onNewIntent (Intent intent)
    {
        setIntent(intent);
    }
    
    @Override
    public void onBackPressed() 
    {
        finish();
    };
    
    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);
        
        // 检测屏幕的方向：纵向或横向  
        if (this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
        {
            // 当前为横屏， 在此处添加额外的处理代码 
        }
        else if (this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT)
        {
            // 当前为竖屏， 在此处添加额外的处理代码  
        }
    }
    
    @Override
    public void onAlarmed(final AlarmData alarmData) 
    {
        if (alarmData == null)
            return;
        
        if (mProgramListView != null) {
        	mProgramListView.post(new Runnable() {
				@Override
				public void run() {
					Program program = alarmData.getRelatedProgram();
			        if (program != null && mListViewAdapter != null) {
			            mListViewAdapter.removeAlarmProgram(program);
			        }
				}
			});
        }
    }
    
    public void back(View view)
    {
        if (view instanceof Button)
        {
            // The same effect with press back key
            finish();
        }
    }
    
    public void onClick(View view)
    {
        switch (view.getId()) 
        {
            case R.id.channeldetail_date_iv:
                toggleDateListView();
                break;
            case R.id.channeldetail_fav_iv:
                toggleFavIcon();
                break;
            default:
                break;
        }
    }
    
    public void collectChannel(boolean doCollect)
    {
        CollectManager manager = AppEngine.getInstance().getCollectManager();
        if (doCollect)
        {
            HashMap<String, Object> info = new HashMap<String, Object>();
            info.put("name", mChannelName);
            manager.addCollectChannel(mChannelId, info);
            String format = getResources().getString(R.string.collect_channel_success);
            String tips = String.format(format, mChannelName);
            Toast.makeText(this, tips, Toast.LENGTH_SHORT).show();
        }
        else
        {
            if (manager.isChannelCollected(mChannelId))
                manager.removeCollectChannel(mChannelId);
            Toast.makeText(this, R.string.cancel_collect, Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override  
    protected void onActivityResult(int requestCode, int resultCode, Intent data)  
    {
        if (requestCode != RequestCode)
            return;
        
        if (data == null)
        	return;
        
        Program program = (Program) data.getSerializableExtra("program");
        if (program == null)
            return;
        
        if (resultCode == AdvanceAlarmActivity.Result_Code_Cancelled) {  // 取消了闹钟
            mListViewAdapter.removeAlarmProgram(program);
        } else {
        	Channel channel = new Channel();
        	channel.tvmaoId = mChannelId;
        	channel.name = mChannelName;
        	if (AppEngine.getInstance().getAlarmHelper().isAlarmSet(channel, program)) {
        		mListViewAdapter.addAlarmProgram(program);
        	}
        }
    }
    
    private void initViews()
    {
        mChannelNameTextView = (TextView) findViewById(R.id.channeldetail_channel_name_tv);
        mProgramListView = (ListView) findViewById(R.id.channeldetail_program_listview);
        mDateChosenListView = (ListView) findViewById(R.id.channeldetail_date_chosen_listview);
        mFavImageView = (ImageView) findViewById(R.id.channeldetail_fav_iv);
        
        mDateAdapter = new DateAdapter(this, DEFAULT_MAX_DAYS);
        mDateChosenListView.setAdapter(mDateAdapter);
        mDateAdapter.setCurrentIndex(mCurrentSelectedDay - 1);
        
        mLeftMenu.setChannelList(mChannelList);
        for (int i=0; i<mChannelList.size(); ++i)
        {
            if (mChannelList.get(i).tvmaoId.equals(mChannelId))
            {
                mLeftMenu.setCurrentIndex(i);
                mLeftMenu.setSelection(i);
            }
        }
        mLeftMenu.setOnChannelSelectListener(new OnChannelSelectListener() 
        {
            @Override
            public void onChannelSelect(Channel channel) 
            {
                mChannelId = channel.tvmaoId;
                mChannelName = channel.name;
                updateAll();
            }
        });
        
        mDateChosenListView.setOnItemClickListener(new AdapterView.OnItemClickListener() 
        {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, final int position, long id) 
            {
                mDateAdapter.setCurrentIndex(position);
                mCurrentSelectedDay = position + 1;
                updateProgramList();
            }
        });
        
        mProgramListView.setOnScrollListener(new OnScrollListener() 
        {            
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) 
            {
                foldDateListView();
            }
            
            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) 
            {
            }
        });
        
        mProgramListView.setLongClickable(true);
        mProgramListView.setOnItemLongClickListener(new OnItemLongClickListener() 
        {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, final int position, long id) 
            {
                // 震动
                Vibrator vib = (Vibrator) ChannelDetailActivity.this.getSystemService(Service.VIBRATOR_SERVICE);
                vib.vibrate(50);
                
                AlertDialog.Builder builder = new AlertDialog.Builder(ChannelDetailActivity.this).setTitle(null);
                String[] stringArray = new String[1];
                stringArray[0] = getResources().getString(R.string.setting_dialog);
                AlertDialog settingDialog = builder.setItems(stringArray, new DialogInterface.OnClickListener() 
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which) 
                    {
                        foldDateListView();
        
                        final Program program = mListViewAdapter.getProgram(position);
                        Intent intent = new Intent(ChannelDetailActivity.this, AdvanceAlarmActivity.class);
                        Channel channel = new Channel();
                        channel.tvmaoId = mChannelId;
                        channel.name = mChannelName;
                        
                        intent.putExtra("channel", channel);
                        intent.putExtra("program", program);
                        startActivityForResult(intent, RequestCode);
                        
                        dialog.dismiss();
                    }
                }).create();
                settingDialog.show();
                
                return true;
            }
        });
        
        mProgramListView.setOnItemClickListener(new AdapterView.OnItemClickListener() 
        {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, final int position, long id) 
            {
                Program program = mListViewAdapter.getProgram(position);
                
                if (program != null && program.hasLink())
                {
                    Intent intent = new Intent(ChannelDetailActivity.this, ProgramActivity.class);
                    intent.putExtra("program", (Serializable) program);
                    startActivity(intent);
                }
            }
        });
    }
    
    private void toggleDateListView()
    {
        if (mDateChosenListView.getVisibility() == View.GONE)
            unfoldDateListView();
        else
            foldDateListView();
    }
    
    private void foldDateListView()
    {
        if (mDateChosenListView.getVisibility() == View.VISIBLE)
        {
            Animation pushRightOut = AnimationUtils.loadAnimation(this, R.anim.push_right_out);
            mDateChosenListView.startAnimation(pushRightOut);
            mDateChosenListView.setVisibility(View.GONE);
        }
    }
    
    private void unfoldDateListView()
    {
        if (mDateChosenListView.getVisibility() == View.GONE)
        {
            Animation pushRightIn = AnimationUtils.loadAnimation(this, R.anim.push_right_in);
            pushRightIn.setFillAfter(true);
            mDateChosenListView.startAnimation(pushRightIn);
            mDateChosenListView.setVisibility(View.VISIBLE);
        }
    }
    
    private void toggleFavIcon()
    {
        
        CollectManager manager = AppEngine.getInstance().getCollectManager();
        if (manager.isChannelCollected(mChannelId))
        {
            collectChannel(false);
            mFavImageView.setImageResource(R.drawable.btn_fav);
        }
        else
        {
            collectChannel(true);
            mFavImageView.setImageResource(R.drawable.btn_fav_checked);
        }
    }
    
    private void updateAll()
    {
    	mCurrentSelectedDay = getDayOfToday();
    	mDateAdapter.setCurrentIndex(mCurrentSelectedDay - 1);
    	
    	updateTitle();
    	updateFavIcon();
    	updateProgramList();
    }
    
    private void updateTitle()
    {
        mChannelNameTextView.setText(mChannelName);
    }
    
    private void updateFavIcon()
    {
    	if (AppEngine.getInstance().getCollectManager().isChannelCollected(mChannelId))
            mFavImageView.setImageResource(R.drawable.btn_fav_checked);
    	else
    		mFavImageView.setImageResource(R.drawable.btn_fav);
    }

    private void updateProgramList()
    {
        updateProgramListFromWeb();
    }
    
    private void updateProgramListFromWeb()
    {
        sRequestId++;
        mProgramList.clear();
        
        AppEngine.getInstance().getChannelHtmlManager().getChannelDetailFromFullWebAsync(sRequestId, UrlManager.getWebChannelUrl(mChannelId, mCurrentSelectedDay), new ChannelDetailCallback() 
        {            
            @Override
            public void onProgramsLoaded(int requestId, List<Program> programList) 
            {
                mProgramList.addAll(programList);
                mUiHandler.sendEmptyMessage(SelfMessage.MSG_UPDATE_PROGRAMS.ordinal());
                updateOnplayingProgram();
                reportVisitToProxy();
                if (!mAlarmCanSetTipsShown) {
                    mAlarmCanSetTipsShown = true;
                    mUiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(ChannelDetailActivity.this, getResources().getString(R.string.alarm_tips_can_set), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
            
            @Override
            public void onDateLoaded(int requestId, final List<ChannelDate> channelDateList) 
            {
                // TODO: 这里应该用更合适的方式重构，即用消息的方式通知，又太影响公共的逻辑
                mUiHandler.post(new Runnable() 
                {
                    @Override
                    public void run() 
                    {
                        List<DateData> dateList = new ArrayList<DateData>();
                        for (int i=0; i<channelDateList.size(); ++i)
                        {
                            DateData dateData = new DateData(channelDateList.get(i).name);
                            dateList.add(dateData);
                        }
                        mDateAdapter.resetDates(dateList);
                    }
                });
            }

            @Override
            public void onError(int requestId, String errorMsg) {
                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mProgressDialog.isShowing()) {
                            mProgressDialog.dismiss();
                        }
                        
                        String tips = "获取数据失败，请稍后重试";
                        getCenterToast(tips, (float)1.2).show();
                    }
                });
            }
        });
        
        mProgressDialog.show();
    }
    
    @SuppressLint("ShowToast")
    private Toast getCenterToast(String text, float textSize) {
        Toast centerToast = Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG);
        centerToast.setGravity(Gravity.CENTER, 0, 0);
        SpannableString ss = new SpannableString(text);
        ss.setSpan(new RelativeSizeSpan((float) textSize), 0, text.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        centerToast.setText(ss);
        return centerToast;
    }
    
    private void updateOnplayingProgram()
    {
        updateOnplayingProgramFromWeb();
    }
    
    private void updateOnplayingProgramFromWeb()
    {
        mOnPlayingProgram = new Program();
        final StringBuffer buffer = new StringBuffer();
        // 使用本地时间
        if (sUseLocalTime)
        {
            Program onPlayingProgram = ProgramUtil.getOnplayingProgramByTime(mProgramList, System.currentTimeMillis());
            if (onPlayingProgram != null) {
                mOnPlayingProgram.copy(onPlayingProgram);
                mUiHandler.sendEmptyMessage(SelfMessage.MSG_UPDATE_ONPLAYING_PROGRAM.ordinal());
            }
            return;
        }
        
        // 使用网络时间
        AppEngine.getInstance().getContentManager().loadNowTimeFromNetwork(buffer, new ContentManager.LoadListener() 
        {    
            @Override
            public void onLoadFinish(int status) 
            {
                try {
                    Date date = new Date(Long.valueOf(buffer.toString()));
    				String currentTime = String.valueOf(date.getHours()) + ":" + String.valueOf(date.getMinutes());
    				Program onPlayingProgram = ProgramUtil.getOnplayingProgramByTime(mProgramList, currentTime);
    	            if (onPlayingProgram != null) {
    	                mOnPlayingProgram.copy(onPlayingProgram);
    	            }
    				
    				// 优化：判断下次是否使用本地时间替代网络时间，以加快“正在播出”节目的显示
    				long proxyHour = date.getHours();
    				long proxyMinute = date.getMinutes();
    				int localHour = Calendar.getInstance().getTime().getHours();
    				int localMinute = Calendar.getInstance().getTime().getMinutes();
    				if (Math.abs((proxyHour * 60 + proxyMinute) - (localHour * 60 + localMinute)) < 10)	// 相差在10分钟以内
    					sUseLocalTime = true;
    				
    				mUiHandler.sendEmptyMessage(SelfMessage.MSG_UPDATE_ONPLAYING_PROGRAM.ordinal());
                } catch (NumberFormatException ex) {
                    ex.printStackTrace();
                }
            }
        });
    }
    
    private void reportVisitToProxy()
    {
        new Handler(NetworkManager.getInstance().getNetworkThreadLooper()).post(new Runnable() 
        {
            @Override
            public void run() 
            {
                String url = AppEngine.getInstance().getUrlManager().tryToGetDnsedUrl(UrlManager.ProxyUrl.Choose) 
                             + "?channel=" + mChannelId + "&day=" + mCurrentSelectedDay + "&report_visit";
                NetDataGetter getter;
                try 
                {
                    getter = new DefaultNetDataGetter(url);
                    getter.getStringData();
                } 
                catch (MalformedURLException e) 
                {
                    e.printStackTrace();
                }
            }
        });
    }
    
    private void showFirstStartTips()
    {
        String tips = ">>> 试试向右滑动 >>>";
        getCenterToast(tips, (float)1.5).show();
        sHasShownFirstStartTips = true;
    }
        
    private boolean isTodayChosen()
    {
    	return mCurrentSelectedDay == getDayOfToday();
    }
    
    private int getDayOfToday()
    {
    	return Utility.getProxyDay(Calendar.getInstance().get(Calendar.DAY_OF_WEEK));
    }
    
    private Channel getCurrentChannel()
    {
        Channel channel = new Channel();
        channel.tvmaoId = mChannelId;
        channel.name = mChannelName;
        return channel;
    }

    @Override
    public boolean handleMessage(Message msg) 
    {
        SelfMessage selfMsg = SelfMessage.values()[msg.what];
        switch (selfMsg) 
        {
            case MSG_UPDATE_PROGRAMS:
                mItemDataList.clear();
                if (mProgressDialog.isShowing()) {
                    mProgressDialog.dismiss();
                }
                List<Program> programList = new ArrayList<Program>();
                programList.addAll(mProgramList);
                mListViewAdapter = new ChannelDetailListAdapter(ChannelDetailActivity.this, programList);
                mProgramListView.setAdapter(mListViewAdapter);
                
                // 标注已经设定过闹钟的节目
                for (int i=0; i<programList.size(); ++i)
                {
                    Program program = programList.get(i);
                    if (AppEngine.getInstance().getAlarmHelper().isAlarmSet(getCurrentChannel(), program)) {
                        mListViewAdapter.addAlarmProgram(program);
                    }
                }
                
                // 标注正在播放的节目
                if (isTodayChosen() && mOnPlayingProgram != null)
                {
                    int position = mListViewAdapter.setOnplayingProgram(mOnPlayingProgram);
                    if (position != -1)
                        mProgramListView.setSelection(position);
                }
                
                foldDateListView();
                
                if (AppEngine.getInstance().getContext() != null    // Crash上报这里进入BootManager会crash，不知原因，故先做保护
                    && AppEngine.getInstance().getBootManager().isFirstStart() && sHasShownFirstStartTips == false)
                {
                    showFirstStartTips();
                }
                break;
            case MSG_UPDATE_ONPLAYING_PROGRAM:
                if (isTodayChosen() && mOnPlayingProgram != null && mListViewAdapter != null)
                {
                    int position = mListViewAdapter.setOnplayingProgram(mOnPlayingProgram);
                    if (position != -1)
                        mProgramListView.setSelection(position);
                }
                break;
            case MSG_UPDATE_DATELIST:
                if (mMaxDays != mDateAdapter.maxDays())
                    mDateAdapter.resetMaxDays(mMaxDays);
                break;
            default:
                break;
        }
        return true;
    }
}
