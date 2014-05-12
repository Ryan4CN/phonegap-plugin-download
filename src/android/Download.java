package so.doo.app.plugins;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.text.DecimalFormat;
import java.util.UUID;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONException;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.StatFs;
import android.util.Log;

public class Download extends CordovaPlugin {
	
	private static Context ctx;
    private DownloadTask task;
    private boolean             isRun = false;
    private double dp = 0.00;
    private static final int    SUCCESS = 0;
    private static final String TAG = "DOWNLOAD";
    private static final String SAVEDIR = "lltao"; // you can change it

    
    @SuppressLint("HandlerLeak")
	private Handler handler = new Handler() {
        @Override 
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:  // update progress
	                Bundle bundle = msg.getData();
	                dp = bundle.getDouble("dp");
                    break;
            }
        }
    };
   
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        ctx = cordova.getActivity().getApplicationContext();
    }
    
    private int start(String url) {

        if (!Utils.isSDCardPresent()) {
            return Error.SDCARD_IS_NOT_PRESENT;
        }
        
        if (!Utils.isSdCardWrittenable()) {
            return Error.SDCARD_IS_NOT_WRITTENABLE;
        }

        if (!Utils.isNetworkAvailabel()) {
            return Error.NETWORK_IS_NOT_AVAILABEL;
        }

        if (!Utils.hadSpace(url)) {
        	return Error.SDCARD_SPACE_NOT_ENOUGH;
        }

        isRun = true;

        // start task
        task = new DownloadTask(url);
        cordova.getThreadPool().execute(task);

        return SUCCESS;
    }

    private int stop() {

        if(isRun) {
    	    task.stop();
        }

    	return SUCCESS;
    }

    private void error(int code) {
    	Log.e(TAG, ""+ code);
    }


    /**
     * Executes the request and returns PluginResult.
     *
     * @param action            The action to execute.
     * @param args              JSONArry of arguments for the plugin.
     * @param callbackContext   The callback id used when calling back into JavaScript.
     * @return                  True if the action was valid, false otherwise.
     * @throws JSONException 
     */
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        
        int rs = -1;

        // download start
        if (action.equals("start")) {
            String url = args.getString(0);
            rs = start(url);
        }
        
        // download progress
        if (action.equals("progress")) {
        	Log.i(TAG, "progress ---- " + dp);
        	callbackContext.success(""+dp);
        	return true;
        }
        
        // download stop
        if (action.equals("stop")) {
        	rs = stop();
        }

        if(rs == 0) {
        	callbackContext.success();
        } else {
        	callbackContext.success();
        	error(rs);
        }
        
        return true;
    }


    public class DownloadTask implements Runnable {
        
        private static final String TAG = "DownloadTask";
        
        private String url;
        private boolean stop = false, finish = false;
        private long downloadSize = 0, size = 0;
        private DownloadThread thread;
    
        public DownloadTask (String url) {
            this.url     = url;
        }
        
        public void stop() {
            thread.onStop();
        }

        
        @Override
        public void run() {
            
            try {
                thread = new DownloadThread(url);
                Log.i(TAG, "init");
                cordova.getThreadPool().execute(thread);
                Log.i(TAG, "execute");

                while (!finish && !stop) {
                	size = thread.getSize();
                	
                	
                	downloadSize = thread.getDownloadSize();

                    if(downloadSize > 0 && size > 0) {
                    	finish = thread.isFinish();
                    	stop   = thread.isStop();
                    	
                    	double percent = (double) downloadSize / size;

                    	Bundle bundle = new Bundle();
                    	bundle.putDouble("dp", percent);
                    	
                        Message msg = new Message();
                        msg.what = 1;
                        msg.setData(bundle);
                        handler.sendMessage(msg);
                        Thread.sleep(200);
                    }
                } 
                
            } catch (Exception  e) {
            	Log.e(TAG +" Error1:", e.getMessage());
            }
            
        }
        
    }
    
    public class DownloadThread extends Thread {
        
        private static final int BUFFER_SIZE = 1024;
        private String url;
        private long curPos = 0, size = 0;
        
        private boolean finish = false;
        private boolean stop   = false;

        public DownloadThread(String url) {
        	this.url = url;
        }
        
        public void onStop() {
            stop = true;
        }
        
        @Override  
        public void run() {
            
            File file = new File(Utils.saveDir() +"/"+ Utils.getFileName(url));
            Log.i(TAG, "file ---- " + Utils.saveDir() +"/"+ Utils.getFileName(url));

            BufferedInputStream bis  = null;
            RandomAccessFile    fos  = null;
            byte[]              buf  = new byte[BUFFER_SIZE];
            URLConnection       con  = null;
            try {
            	Log.i(TAG, "connect");
            	URL durl = new URL(url);
                con = durl.openConnection();
                con.setAllowUserInteraction(true);
                
                con.setRequestProperty("Range", "bytes=0-");
                size = con.getContentLength();
                Log.i(TAG, "size ---- " + size);
                fos = new RandomAccessFile(file, "rw");
                fos.seek(0);
                bis = new BufferedInputStream(con.getInputStream());
                Log.i(TAG, "start write");
                while (curPos < size && !stop) {  
                    int len = bis.read(buf, 0, BUFFER_SIZE);                  
                    if (len == -1) {  
                        break;  
                    }

                    fos.write(buf, 0, len);  
                    curPos = curPos + len;
                    
                    // Log.i(TAG, "Pos ------ " + curPos);
                }
                
                Log.i(TAG, "finish");

                finish = true;
                bis.close();  
                fos.close();  
            } catch (IOException e) {  
                Log.e(TAG +" Error2:", e.getMessage());  
            }  
        }  
       
        public boolean isFinish(){  
            return finish;  
        }
        
        public boolean isStop() {
        	return stop;
        }
       
        public long getDownloadSize() {  
            return curPos;  
        }

        public long getSize() {
            return size;
        }
    }

    static class Utils {

    	private static final String TAG  = "UTILS";

    	public static boolean isSDCardPresent() {
    		Log.i(TAG, "isSDCardPresent");
	        return Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
	    }
	    
	    public static boolean isSdCardWrittenable() {
	    	Log.i(TAG, "isSdCardWrittenable");
	        return Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
	    }
	    
	    public static boolean isNetworkAvailabel() {
	    	Log.i(TAG, "isNetworkAvailabel");
	        ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(ctx.CONNECTIVITY_SERVICE);
	        if (cm != null) {
	            NetworkInfo[] info = cm.getAllNetworkInfo();
	            if (info != null) {
	                for (int i = 0; i < info.length; i++) {           
	                     if (info[i].getState() == NetworkInfo.State.CONNECTED ||
	                        info[i].getState() == NetworkInfo.State.CONNECTING) {              
	                            return true; 
	                     }   
	                }
	            }    
	        }
	        
	        return false;
	    }

	    public static boolean hadSpace(String url) {
	    	Log.i(TAG, "hadSpace");
	    	try {
		    	URL durl = new URL(url);
	            URLConnection conn = durl.openConnection();
	            long size = conn.getContentLength();

	            StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
				long bytesAvailable = (long)stat.getBlockSize() * (long)stat.getAvailableBlocks();

				if(bytesAvailable >= size) {
					return true;
				} else return false;
            } catch (Exception  e) {
            	Log.e(TAG, "get file size error");
                return false;
            }

	    }

	    public static File saveDir() {
	        File sdcardDir = Environment.getExternalStorageDirectory();
	        String path = sdcardDir.getPath() + "/" + SAVEDIR;
	        File saveDir = new File(path);
	        if (!saveDir.exists()) {
	            saveDir.mkdirs();
	        }

	        return saveDir;
	    }

	    public static String getFileName (String url) {
            int index = url.lastIndexOf('?');
            String filename;
            if (index > 1) {
                filename = url.substring(url.lastIndexOf('/') + 1,index);
            } else {
                filename = url.substring(url.lastIndexOf('/') + 1);
            }
            
            if(filename==null || "".equals(filename.trim())){
                filename = UUID.randomUUID()+ ".apk";
            }
	    	Log.i(TAG, "getFileName ---- " + filename);
            return filename;
        }
    }

    class Error {
    	public static final int SDCARD_IS_NOT_PRESENT       = 1;
    	public static final int SDCARD_IS_NOT_WRITTENABLE   = 2;
    	public static final int SDCARD_SPACE_NOT_ENOUGH     = 3;

    	public static final int NETWORK_IS_NOT_AVAILABEL    = 100;
    }
}

