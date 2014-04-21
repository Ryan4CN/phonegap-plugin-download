package so.doo.app.plugins;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONException;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.StatFs;
import android.util.Log;
import android.widget.Toast;

@SuppressLint("HandlerLeak")
public class Download extends CordovaPlugin {
	
	private static final String TAG = "DOWNLOAD";
	private static final String SaveDir = "lltao";
	private int threadNum = 1; // 1个线程
	private DownloadTask downloadTask;
	
	private Map<String, DownloadTask> taskMaps = new HashMap<String, Download.DownloadTask>();
	
    private Handler handler = new Handler() {
    	@Override 
        public void handleMessage(Message msg) {
    		
    		switch (msg.what) {
	    		case 1:
	    			Log.i("MSG", "进度：" + msg.arg1 + " / " + msg.arg2);
	    			long downloadPercent = msg.arg1 * 100 / msg.arg2;
	    			Bundle bundle = msg.getData();
	    			String id = bundle.getString("id");
	    			webView.sendJavascript("DownloadProgress('"+id+"', '"+downloadPercent+"%')");
	    			break;
    		}
    	}
    };
   
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        Log.i(TAG, "======== DOWNLOAD INIT ========");
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
    	
    	Log.i(TAG, "======== DOWNLOAD PLUGIN ========");
    	
    	// download
    	if (action.equals("download")) {
    		
    		String url = args.getString(0);
    		String id = args.getString(1);
    		
    		Log.i(TAG, "download url:" + url);
    		Log.i(TAG, "download id:" + id);

    		download(url, id);
    		
    		return true;
    	}
    	
    	
    	// pause
    	if (action.equals("pause")) {
    		
    		String id = args.getString(0);
    		Log.i(TAG, "pause id:" + id);
    		
    		taskMaps.get(id).stop();
   		
    		return true;
    	}
    	
    	// stop
    	if (action.equals("stop")) {
    		String id = args.getString(0);
    		
    		Log.i(TAG, "stop id:" + id);
    		
    		taskMaps.get(id).stop();
    		
    		Context ctx = cordova.getActivity().getApplicationContext();
			SharedPreferences dlxml = ctx.getSharedPreferences("download", Context.MODE_PRIVATE);
			Editor editor = dlxml.edit();
    		for (int i = 0; i < threadNum; i++) {
    			editor.putInt(id+i, 0);
    			Log.e("stop set pos" + id+i, ""+0);
    		}
    		editor.commit();

    		return true;
    	}
    	
        return false;
    }
    
    
    public boolean isSDCardPresent() {
    	return Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
    }
    
	public static boolean isSdCardWrittenable() {
	    if (android.os.Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED)) {
		return true;
	    }
	    return false;
	}
	
	public boolean isNetworkAvailabel() {
	    ConnectivityManager connectivity = (ConnectivityManager) cordova.getActivity().getApplicationContext().getSystemService(cordova.getActivity().getApplicationContext().CONNECTIVITY_SERVICE);
	    if (connectivity == null) {
	    	return false;
	    } else {
	    	NetworkInfo[] info = connectivity.getAllNetworkInfo();
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
    
	public File getDir() {

		File sdcardDir = Environment.getExternalStorageDirectory();
		String path = sdcardDir.getPath() + "/" + SaveDir;
		File saveDir = new File(path);
		if (!saveDir.exists()) {
			saveDir.mkdirs();
		}

		return saveDir;
	}
	
	private void download(String url, String id) {
		
		// 是否有SD卡
	    if (!isSDCardPresent()) {
			Toast.makeText(cordova.getActivity().getApplicationContext(), "未发现SD卡", Toast.LENGTH_LONG);
			return;
	    }
	    
		// SD卡是否可写
	    if (!isSdCardWrittenable()) {
			Toast.makeText(cordova.getActivity().getApplicationContext(), "SD卡不能读写", Toast.LENGTH_LONG);
			return;
	    }
	    
	    // 空间是否足够
		
		// 网络判断
	    if (!isNetworkAvailabel()) {
	    	Toast.makeText(cordova.getActivity().getApplicationContext(), "当前网络不可用", Toast.LENGTH_LONG);
			return;
	    }
	    
	    File saveDir = getDir();
        downloadTask = new DownloadTask(url, id, saveDir);
        
        taskMaps.put(id, downloadTask);
        
        cordova.getThreadPool().execute(downloadTask);
	}


	class DownloadTask implements Runnable {
		
		private static final String TAG = "DownloadTask";
		
		private String url, id;
		
		private int fileSize, blockSize, downloadSizeMore;
		private int downloadedSize = 0;
		private boolean isStop = false;
		private File saveDir;
		private DownloadThread[] dts;
	
		public DownloadTask (String url, String id, File saveDir) {
			this.url = url;
			this.id  = id;
			this.saveDir = saveDir;
		}
		
		public void stop() {
			for (int i = 0; i < threadNum; i++) {
				dts[i].doStop();
			}
			this.isStop = true;
		}
		
		public String getFileNameFromUrl(String url) {
		    // 通过 ‘？’ 和 ‘/’ 判断文件名
		    int index = url.lastIndexOf('?');
		    String filename;
		    if (index > 1) {
		    	filename = url.substring(url.lastIndexOf('/') + 1,index);
		    } else {
		    	filename = url.substring(url.lastIndexOf('/') + 1);
		    }
		    
		    if(filename==null || "".equals(filename.trim())){ //如果获取不到文件名称
		    	filename = UUID.randomUUID()+ ".apk";         //默认取一个文件名
		    }
		    
		    return filename;
		}
		

		
		@Override
		public void run() {
			
			dts = new DownloadThread[threadNum];
			
			try {
				URL durl = new URL(url);  
                URLConnection conn = durl.openConnection();
                fileSize = conn.getContentLength();
                blockSize = fileSize / threadNum; 
                downloadSizeMore = (fileSize % threadNum);
                
    			Context ctx = cordova.getActivity().getApplicationContext();
    			SharedPreferences dlxml = ctx.getSharedPreferences("download", Context.MODE_PRIVATE);
                File file = new File(saveDir +"/"+ getFileNameFromUrl(url));

                for (int i = 0; i < threadNum; i++) {  
                    int addtionSize = 0;
                    if(i + 1 == threadNum) {
                    	addtionSize = downloadSizeMore;
                    }
                    
                    int begin =  dlxml.getInt(id+i, 0);
                    
                    Log.e("Load pos" + id+i, ""+begin);
                    
                    if(begin <= 0) {
                    	begin = i * blockSize;
                    }
                    
                    Log.e("Start pos", ""+begin);
                    
                	DownloadThread fdt = new DownloadThread(durl, file, id,	begin, (i + 1) * blockSize - 1 + addtionSize, i * blockSize);  
                	fdt.setName(""+i);
                    cordova.getThreadPool().execute(fdt);
                    dts[i] = fdt;
                }
                
                boolean finished = false;
                while (!finished && !this.isStop) {  
                    // 先把整除的余数搞定  
                    finished = true;
                    downloadedSize = 0;
                    for (int i = 0; i < dts.length; i++) {  
                        downloadedSize += dts[i].getDownloadSize();  
                        if (!dts[i].isFinished()) {  
                            finished = false;  
                        }  
                    }
                    
                    if(downloadedSize > 0) {
                    	Message msg = new Message();
	                    msg.what = 1;
	                    msg.arg1 = downloadedSize;
	                    msg.arg2 = fileSize;
	                    Bundle bundle = new Bundle();
	                    bundle.putString("id", id); 
	                    msg.setData(bundle);
	                    handler.sendMessage(msg);
	                    //休息1秒后再读取下载进度  
	                    Thread.sleep(500);
                    }
                } 
				
			} catch (Exception  e) {
				Log.i(TAG, "下载分支线程错误");
			}
			
		}
		
	}
	
	class DownloadThread extends Thread {
		
	    private static final int BUFFER_SIZE=1024;  
	    private URL url;  
	    private File file;  
	    private int startPosition,endPosition, curPosition, initPosition;  
	    private String id;
	    
	    private boolean finished=false;  
	    private int downloadSize;
	    private boolean isStop = false;
		
		public DownloadThread(URL url, File file, String id, int startPosition,int endPosition, int initPosition) {
	        this.url=url;  
	        this.file=file;  
	        this.id = id;
	        this.startPosition=startPosition;  
	        this.curPosition=startPosition;  
	        this.endPosition=endPosition;
	        this.initPosition=initPosition;
		}
		
		public void doStop() {
			this.isStop = true;
		}
		
	    @Override  
	    public void run() {  
	        BufferedInputStream bis = null;  
	        RandomAccessFile fos = null;                                                 
	        byte[] buf = new byte[BUFFER_SIZE];  
	        URLConnection con = null;  
	        try {  
	            con = url.openConnection();  
	            con.setAllowUserInteraction(true);  
	            //设置当前线程下载的起点，终点  
	            con.setRequestProperty("Range", "bytes=" + startPosition + "-" + endPosition);  
	            //使用java中的RandomAccessFile 对文件进行随机读写操作  
	            fos = new RandomAccessFile(file, "rw");  
	            //设置开始写文件的位置  
	            fos.seek(startPosition);  
	            bis = new BufferedInputStream(con.getInputStream());  
	            
	            downloadSize = startPosition - initPosition;
	            //开始循环以流的形式读写文件  
	            Context ctx = cordova.getActivity().getApplicationContext();
    			SharedPreferences dlxml = ctx.getSharedPreferences("download", Context.MODE_PRIVATE);
    			Editor editor = dlxml.edit();
	            while (curPosition < endPosition && !this.isStop) {  
	                int len = bis.read(buf, 0, BUFFER_SIZE);                  
	                if (len == -1) {  
	                    break;  
	                }
                
	                fos.write(buf, 0, len);  
	                curPosition = curPosition + len;

	    			editor.putInt(id+getName(), curPosition);
	    			editor.commit();
	    			Log.i("Save pos" + id+getName(), ""+curPosition);
	                if (curPosition > endPosition) {  
	                    downloadSize+=len - (curPosition - endPosition) + 1;
	                } else {  
	                    downloadSize+=len;  
	                }
	            }  
	            //下载完成设为true
	            if(!this.isStop) {
		            editor.putInt(id+getName(), 0);
	    			editor.commit();
	            }
	            this.finished = true;
	            bis.close();  
	            fos.close();  
	        } catch (IOException e) {  
	          Log.d("Thread " + getName() +" Error:", e.getMessage());  
	        }  
	    }  
	   
	    public boolean isFinished(){  
	        return finished;  
	    }  
	   
	    public int getDownloadSize() {  
	        return downloadSize;  
	    }
	    
	    public int getInitSize() {
	    	return initPosition;
	    }

	}
}
