package com.tools.tvguide.activities;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.tools.tvguide.managers.UrlManager;
import com.tools.tvguide.utils.NetDataGetter;
import com.tools.tvguide.utils.NetworkManager;
import com.tools.tvguide.utils.Utility;
import com.tools.tvguide.utils.XmlParser;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.util.Pair;
import android.view.Menu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.SimpleAdapter.ViewBinder;

public class ChannellistActivity extends Activity 
{
    private String mCategoryId;
    private ListView mChannelListView;
    private List<Pair<String, String>> mChannelList;                    // List of "id"-"name" pair
    private List<Pair<String, String>> mOnPlayingProgramList;           // List of "id"-"title" pair
    private HashMap<String, HashMap<String, Object>> mXmlChannelInfo;
    private SimpleAdapter mListViewAdapter;
    private ArrayList<HashMap<String, Object>> mItemList;
    private Handler mUpdateHandler;
    private final String XML_ELEMENT_LOGO = "logo";
    private final int MSG_REFRESH_CHANNEL_LIST              = 0;
    private final int MSG_REFRESH_ON_PLAYING_PROGRAM_LIST   = 1;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_channellist);
        mChannelListView = (ListView)findViewById(R.id.channel_list);
        mChannelList = new ArrayList<Pair<String, String>>();
        mOnPlayingProgramList = new ArrayList<Pair<String,String>>();
        mXmlChannelInfo = XmlParser.parseChannelInfo(this);
        mItemList = new ArrayList<HashMap<String, Object>>();
        createUpdateThreadAndHandler();
        createAndSetListViewAdapter();
        
        mCategoryId = getIntent().getStringExtra("category");
        if (mCategoryId != null)
        {
            updateChannelList();
        }
        mChannelListView.setOnItemClickListener(new OnItemClickListener() 
        {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) 
            {
                String channelId = mChannelList.get(position).first;
                String channelName = mChannelList.get(position).second;
                Intent intent = new Intent(ChannellistActivity.this, ChannelDetailActivity.class);
                intent.putExtra("id", channelId);
                intent.putExtra("name", channelName);
                startActivity(intent);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) 
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_channellist, menu);
        return true;
    }

    public void back(View view)
    {
        if (view instanceof Button)
        {
            // The same effect with press back key
            finish();
        }
    }
    
    private void createUpdateThreadAndHandler()
    {
        mUpdateHandler = new Handler(NetworkManager.getInstance().getNetworkThreadLooper());
    }
    
    private void createAndSetListViewAdapter()
    {
        mListViewAdapter = new SimpleAdapter(ChannellistActivity.this, mItemList, R.layout.channellist_item,
                new String[]{"image", "name", "program"}, 
                new int[]{R.id.itemImage, R.id.itemChannel, R.id.itemProgram});
        mListViewAdapter.setViewBinder(new MyViewBinder());
        mChannelListView.setAdapter(mListViewAdapter);
    }
    
    public class MyViewBinder implements ViewBinder
    {
        public boolean setViewValue(View view, Object data,
                String textRepresentation)
        {
            if((view instanceof ImageView) && (data instanceof Bitmap))
            {
                ImageView iv = (ImageView)view;
                Bitmap bm = (Bitmap)data;
                iv.setImageBitmap(bm);
                return true;
            }
            return false;
        }
    }
       
    private void updateChannelList()
    {
        mUpdateHandler.post(new Runnable()
        {
            public void run()
            {
                String url = UrlManager.URL_CHANNELS + "?category=" + mCategoryId;
                NetDataGetter getter;
                try 
                {
                    getter = new NetDataGetter(url);
                    JSONObject jsonRoot = getter.getJSONsObject();
                    mChannelList.clear();
                    if (jsonRoot != null)
                    {
                        JSONArray channelListArray = jsonRoot.getJSONArray("channels");
                        if (channelListArray != null)
                        {
                            for (int i=0; i<channelListArray.length(); ++i)
                            {
                                Pair<String, String> pair = new Pair<String, String>(channelListArray.getJSONObject(i).getString("id"), 
                                        channelListArray.getJSONObject(i).getString("name"));
                                mChannelList.add(pair);
                            }
                        }
                    }
                    uiHandler.sendEmptyMessage(MSG_REFRESH_CHANNEL_LIST);
                }
                catch (MalformedURLException e) 
                {
                    e.printStackTrace();
                }
                catch (JSONException e) 
                {
                    e.printStackTrace();
                }
            }
        });
    }
    
    private void updateOnPlayingProgramList()
    {
        mUpdateHandler.post(new Runnable()
        {
            public void run()
            {
                assert(mChannelList != null);
                String url = UrlManager.URL_ON_PLAYING_PROGRAMS;
                try 
                {
                    NetDataGetter getter;
                    getter = new NetDataGetter(url);
                    List<BasicNameValuePair> pairs = new ArrayList<BasicNameValuePair>();
                    //String test = "{\"channels\":[\"cctv1\", \"cctv3\"]}";
                    String idArray = "[";
                    for (int i=0; i<mChannelList.size(); ++i)
                    {
                        idArray += "\"" + mChannelList.get(i).first + "\"";
                        if (i < (mChannelList.size() - 1))
                        {
                            idArray += ",";
                        }
                    }
                    idArray += "]";
                    
                    pairs.add(new BasicNameValuePair("channels", "{\"channels\":" + idArray + "}"));
                    JSONObject jsonRoot = getter.getJSONsObject(pairs);
                    mOnPlayingProgramList.clear();
                    if (jsonRoot != null)
                    {
                        JSONArray resultArray = jsonRoot.getJSONArray("result");
                        if (resultArray != null)
                        {
                            for (int i=0; i<resultArray.length(); ++i)
                            {
                                Pair<String, String> pair = new Pair<String, String>(resultArray.getJSONObject(i).getString("id"), 
                                        resultArray.getJSONObject(i).getString("title"));
                                mOnPlayingProgramList.add(pair);
                            }
                        }
                    }
                    uiHandler.sendEmptyMessage(MSG_REFRESH_ON_PLAYING_PROGRAM_LIST);
                }
                catch (MalformedURLException e) 
                {
                    e.printStackTrace();
                }
                catch (JSONException e) 
                {
                    e.printStackTrace();
                }
            }
        });
    }
       
    private Handler uiHandler = new Handler()
    {
        public void handleMessage(Message msg)
        {
            super.handleMessage(msg);
            switch (msg.what) 
            {
                case MSG_REFRESH_CHANNEL_LIST:
                    if (mChannelList != null)
                    {
                        mItemList.clear();
                        for(int i=0; i<mChannelList.size(); ++i)
                        {
                            HashMap<String, Object> item = new HashMap<String, Object>();
                            if (mXmlChannelInfo.get(mChannelList.get(i).first) != null)
                            {
                                item.put("image", Utility.getImage(ChannellistActivity.this, (String) mXmlChannelInfo.get(mChannelList.get(i).first).get(XML_ELEMENT_LOGO)));                        
                            }
                            item.put("name", mChannelList.get(i).second);
                            mItemList.add(item);
                        }
                        mListViewAdapter.notifyDataSetChanged();
                        updateOnPlayingProgramList();
                    }
                    break;
                case MSG_REFRESH_ON_PLAYING_PROGRAM_LIST:
                    if (mOnPlayingProgramList != null)
                    {
                        for (int i=0; i<mOnPlayingProgramList.size(); ++i)
                        {
                            mItemList.get(i).put("program", "���ڲ��ţ�  " + mOnPlayingProgramList.get(i).second);
                        }
                        mListViewAdapter.notifyDataSetChanged();
                    }
                    break;
                default:
                    break;
            }
        }
    };
}