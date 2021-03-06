package com.tools.tvguide.managers;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.http.message.BasicNameValuePair;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.tools.tvguide.data.Category;
import com.tools.tvguide.data.Channel;
import com.tools.tvguide.utils.HtmlUtils;
import com.tools.tvguide.utils.CacheControl;

import android.content.Context;
import android.text.TextUtils;

public class OnPlayingHtmlManager 
{
    public static final String CATEGORY_ENTRY_URL = "http://m.tvmao.com/program/playing/cctv";
    private Context mContext;
    
    public OnPlayingHtmlManager(Context context)
    {
        assert (context != null);
        mContext = context;
    }
    
    public interface CategoryEntriesCallback
    {
        void onCategoryEntriesLoaded(int requestId, List<Category> categories);
    }
    
    public interface OnPlayingCallback
    {
        void onChannelsLoaded(int requestId, List<Channel> channels);
        void onProgramsLoaded(int requestId, HashMap<String, String> programs);  // programs: key(channel id), value(program name)
    }
    
    public void getCategoryEntries(final int requestId, final CategoryEntriesCallback callback)
    {
        assert (callback != null);
        new Thread(new Runnable() 
        {
            @Override
            public void run() 
            {
                try 
                {
                    Document doc = HtmlUtils.getDocument(CATEGORY_ENTRY_URL, CacheControl.Disk);
                    
                    // 返回结果
                    List<Category> categoryList = new ArrayList<Category>();
                    
                    Elements topMenuElements = doc.select("dl.chntypetab dd");
                    for (int i=0; i<topMenuElements.size(); ++i)
                    {
                        Element linkElement = topMenuElements.get(i).select("a").first();
                        if (linkElement != null)
                        {
                            Category category = new Category();
                            category.name = linkElement.ownText().replace("?", "");   // 因tvmao出错而产生多余的符号，需删除
                            category.link = getAbsoluteUrl(linkElement.attr("href"));
                            categoryList.add(category);
                        }
                    }
                    
                    Elements optionElements = doc.select("select[name=prov] option");
                    for (int i=0; i<optionElements.size(); ++i)
                    {
                        Element optionElement = optionElements.get(i);
                        String value = optionElement.attr("value");
                        if (TextUtils.equals(value, "0"))   // 自动
                            continue;
                        
                        Category category = new Category();
                        category.name = optionElement.ownText();
                        category.tvmaoId = optionElement.attr("value");
                        category.link = getAbsoluteUrlByOptionValue(optionElement.attr("value"));
                        categoryList.add(category);
                    }
                    
                    callback.onCategoryEntriesLoaded(requestId, categoryList);
                } 
                catch (IOException e) 
                {
                    e.printStackTrace();
                }
            }
        }).start();
    }
    
    public void getOnPlayingChannels(final int requestId, final Category category, final OnPlayingCallback callback)
    {
        assert (callback != null);
        if (category == null || category.link == null)
            return;
        
        new Thread(new Runnable() 
        {
            @Override
            public void run() 
            {
                try 
                {
                    Document doc = null;
                    // 返回结果
                    List<Channel> channelList = new ArrayList<Channel>();
                    HashMap<String, String> programs = new HashMap<String, String>();
                    
                    // 尝试从缓存中加载
                    doc = getDocumentByCategory(category, CacheControl.Disk);
                    if (doc == null)
                        return;
                    getPlayingChannelPrograms(doc, channelList, programs);
                    callback.onChannelsLoaded(requestId, channelList);
                    
                    // 实时获取
                    doc = getDocumentByCategory(category, CacheControl.Never);
                    if (doc == null)
                        return;
                    getPlayingChannelPrograms(doc, channelList, programs);
                    callback.onProgramsLoaded(requestId, programs);
                } 
                catch (IOException e) 
                {
                    e.printStackTrace();
                }
            }
        }).start();
    }
    
    private Document getDocumentByCategory(Category category, CacheControl control) throws IOException
    {
        Document doc = null;
        if (category.tvmaoId != null && category.tvmaoId.trim().length() > 0)
        {
            List<BasicNameValuePair> pairs = new ArrayList<BasicNameValuePair>();
            pairs.add(new BasicNameValuePair("prov", category.tvmaoId));
            doc = HtmlUtils.getDocument(category.link, "utf-8", pairs, control);
        }
        else
        {
            doc = HtmlUtils.getDocument(category.link, control);
        }
        
        return doc;
    }
    
    private void getPlayingChannelPrograms(Document doc, List<Channel> channels, HashMap<String, String> programs)
    {
        if (doc == null || channels == null || programs == null)
            return;
        
        Elements onplayingElements = doc.select("table.playing tbody tr");
        for (int i=0; i<onplayingElements.size(); ++i)
        {
            Element onplayingElement = onplayingElements.get(i);
            Element channelElement = onplayingElement.select("td").first();
            if (channelElement != null)
            {
                if (channelElement.ownText().equals("频道"))
                    continue;
                
                Element linkElement = channelElement.select("a").first();
                if (linkElement != null)
                {
                    Channel channel = new Channel();
                    channel.name = linkElement.ownText();
                    channel.tvmaoId = HtmlUtils.filterTvmaoId(linkElement.attr("href"));
                    channel.tvmaoLink = getAbsoluteUrl(linkElement.attr("href"));
                    channels.add(channel);
                    
                    Element programElement = channelElement.nextElementSibling();
                    if (programElement != null)
                    {
                        programs.put(channel.tvmaoId, programElement.text());
                    }
                }
            }
        }
    }
    
    private String getAbsoluteUrlByOptionValue(String value)
    {
        String prefix = "http://m.tvmao.com/program/playing/";
        return prefix + value;
    }
    
    private String getAbsoluteUrl(String url)
    {
        if (url == null)
            return null;
        
        try 
        {
            String protocol = new URL(CATEGORY_ENTRY_URL).getProtocol();
            String host = new URL(CATEGORY_ENTRY_URL).getHost();
            String prefix = protocol + "://" + host;
            
            if (!url.contains("http://"))
                url = prefix + url;
        } 
        catch (MalformedURLException e) 
        {
            e.printStackTrace();
        }
        
        return url;
    }
}
