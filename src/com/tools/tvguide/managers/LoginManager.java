package com.tools.tvguide.managers;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

import android.content.Context;

public class LoginManager 
{
    // millisecond
    public static final int KEEP_ALIVE_INTERVAL = 5000;
    private Context mContext;
    private Thread  mKeepAliveThread;
    private boolean mDone;
    private long    mLastActive = System.currentTimeMillis();
    
    public LoginManager(Context context)
    {
        mContext = context;
        mDone = false;
    }
    
    public void shutDown()
    {
        mDone = true;
    }
    
    public void startKeepAliveProcess()
    {
        int keepAliveInterval = KEEP_ALIVE_INTERVAL;
        if (keepAliveInterval > 0)
        {
            if (mKeepAliveThread == null || !mKeepAliveThread.isAlive())
            {
                KeepAliveTask task = new KeepAliveTask(KEEP_ALIVE_INTERVAL);
                mKeepAliveThread = new Thread(task);
                task.setThread(mKeepAliveThread);
                mKeepAliveThread.setDaemon(true);
                mKeepAliveThread.setName("Keep Alive");
                mKeepAliveThread.start();
            }
        }
    }
    
    /**
     * ��ʱ������������ά�ֳ�����
     */
    private class KeepAliveTask implements Runnable
    {

        private int     mDelay;
        private Thread  mThread;
        private Socket  mSocket;
        private OutputStream mOutputStream;

        public KeepAliveTask(int delay)
        {
            this.mDelay = delay;
            mSocket = new Socket();
        }

        protected void setThread(Thread thread)
        {
            this.mThread = thread;
        }

        public void run()
        {
            try
            {
                Thread.sleep(1500);
                mSocket.connect(new InetSocketAddress(UrlManager.HOST, UrlManager.PORT));
                mSocket.setSoTimeout(mDelay);
                mOutputStream = mSocket.getOutputStream();
            }
            catch (InterruptedException ie)
            {
                // Do nothing
            } 
            catch (IOException e) 
            {
                // Do nothing
                return;
            }
            while (!mDone && mKeepAliveThread == mThread)
            {
                if (System.currentTimeMillis() - mLastActive >= mDelay)
                {
                    try 
                    {
                        mOutputStream.write(" ".getBytes());
                        mOutputStream.flush();
                    } 
                    catch (IOException e) 
                    {
                        // Do nothing
                    }
                    mLastActive = System.currentTimeMillis();
                }
                try
                {
                    Thread.sleep(mDelay);
                }
                catch (InterruptedException ie)
                {
                    // Do nothing
                }
            }
        }
    }
}