package com.tools.tvguide.activities;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.tools.tvguide.utils.NetDataGetter;

import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Pair;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.SimpleAdapter.ViewBinder;

public class ChannellistActivity extends Activity 
{
    private String mCategoryId;
    private ListView mChannelListView;
    private List<Pair<String, String>> mChannelList;
    private HashMap<String, HashMap<String, Object>> mXmlChannelInfo;
    private SimpleAdapter mListViewAdapter;
    private ArrayList<HashMap<String, Object>> mItemList;
    private HandlerThread mUpdateThread;
    private Handler mUpdateHandler;
    private final String XML_FILE = "channels.xml";
    private final String XML_ELEMENT_LOGO = "logo";
    private final String XML_ELEMENT_CHANNEL = "channel";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_channellist);
        mChannelListView = (ListView)findViewById(R.id.channel_list);
        mChannelList = new ArrayList<Pair<String, String>>();
        mXmlChannelInfo = new HashMap<String, HashMap<String,Object>>();
        mItemList = new ArrayList<HashMap<String, Object>>();
        createUpdateThreadAndHandler();
        createAndSetListViewAdapter();
        parseXML();
        
        mCategoryId = getIntent().getStringExtra("category");
        if (mCategoryId != null)
        {
            update();
        }
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
        mUpdateThread = new HandlerThread("SearchThread");
        mUpdateThread.start();
        mUpdateHandler = new Handler(mUpdateThread.getLooper());
    }
    
    private void createAndSetListViewAdapter()
    {
        mListViewAdapter = new SimpleAdapter(ChannellistActivity.this, mItemList, R.layout.channellist_item,
                new String[]{"image", "name"}, 
                new int[]{R.id.itemImage, R.id.itemText});
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
       
    private void update()
    {
        mUpdateHandler.post(new Runnable()
        {
            public void run()
            {
                String url = "http://192.168.1.103/projects/TV/json/channels.php?category=" + mCategoryId;
                NetDataGetter getter;
                try 
                {
                    getter = new NetDataGetter(url);
                    JSONObject jsonRoot = getter.getJSONsObject();
                    mChannelList.clear();
                    if (jsonRoot != null)
                    {
                        JSONArray categoryArray = jsonRoot.getJSONArray("channel_list");
                        if (categoryArray != null)
                        {
                            for (int i=0; i<categoryArray.length(); ++i)
                            {
                                Pair<String, String> pair = new Pair<String, String>(categoryArray.getJSONObject(i).getString("id"), 
                                        categoryArray.getJSONObject(i).getString("name"));
                                mChannelList.add(pair);
                            }
                        }
                    }
                    uiHandler.sendEmptyMessage(0);
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
    
    private Bitmap getImage(String fileName)
    {
        Bitmap bitmap = null;
        try
        {
            InputStream is = getAssets().open(fileName);
            bitmap = BitmapFactory.decodeStream(is);
            is.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
            return null;
        }
        
        return bitmap;
    }
    
    private void parseXML()
    {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try 
        {
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(getAssets().open(XML_FILE));
            NodeList nl1 = doc.getElementsByTagName(XML_ELEMENT_CHANNEL);
            for (int i=0; i<nl1.getLength(); ++i) 
            {
                String id = null, logo = null;
                Node n = nl1.item(i);
                if (n.hasAttributes())
                {
                    id = n.getAttributes().getNamedItem("id").getNodeValue();
                }
                // 获取 n 节点下所有的子节点。此处值得注意，在DOM解析时会将所有回车都视为 n 节点的子节点。
                NodeList nl2 = n.getChildNodes();
                for (int j=0; j<nl2.getLength(); ++j) 
                {
                    Node n2 = nl2.item(j);
                    if (n2.hasChildNodes()) 
                    {
                        String name = n2.getNodeName();
                        if (name.equalsIgnoreCase(XML_ELEMENT_LOGO))
                        {
                            logo = n2.getFirstChild().getNodeValue();
                        }
                    }
                }
                if (id != null && logo != null)
                {
                    HashMap<String, Object> item = new HashMap<String, Object>();
                    item.put(XML_ELEMENT_LOGO, logo);
                    mXmlChannelInfo.put(id, item);
                }
            }
        } 
        catch (ParserConfigurationException e) 
        {
            e.printStackTrace();
        } 
        catch (SAXException e) 
        {
            e.printStackTrace();
        } 
        catch (IOException e) 
        {
            e.printStackTrace();
        }
    }
    
    private Handler uiHandler = new Handler()
    {
        public void handleMessage(Message msg)
        {
            super.handleMessage(msg);
            if (mChannelList != null)
            {
                for(int i=0; i<mChannelList.size(); ++i)
                {
                    HashMap<String, Object> item = new HashMap<String, Object>();
                    if (mXmlChannelInfo.get(mChannelList.get(i).first) != null)
                    {
                        item.put("image", getImage((String) mXmlChannelInfo.get(mChannelList.get(i).first).get(XML_ELEMENT_LOGO)));                        
                    }
                    item.put("name", mChannelList.get(i).second);
                    mItemList.add(item);
                }
                mListViewAdapter.notifyDataSetChanged();
            }
        }
    };
}
