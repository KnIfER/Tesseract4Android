package com.googlecode.tesseraction;


import android.app.Activity;
import android.os.Bundle;
import android.os.StrictMode;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;


import com.github.wget.WGet;
import com.github.wget.info.DownloadInfo;

import java.io.File;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

public class TestActivity extends Activity {
	
	AtomicBoolean abort = new AtomicBoolean();
	private DownloadInfo info;
	long last;
	
	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		FrameLayout fl = new FrameLayout(this);
		setContentView(fl);
		
		//TesseractPluginTest.Test(this);
		
		
		Runnable notify = new Runnable() {
			@Override
			public void run() {
				switch (info.getState()) {
					case EXTRACTING:
					case EXTRACTING_DONE:
					case DONE:
						//CMN.Log(info.getState());
						break;
					case RETRYING:
						CMN.Log("wget::", info.getState() + " " + info.getDelay());
						break;
					case DOWNLOADING:
						long now = System.currentTimeMillis();
						if (now - 1000 > last) {
							last = now;
							CMN.Log("wget::", info.getCount());
						}
						
//						if(!notified && info.getCount()>0) {
//							state = 1;
//							BrowseActivity a = aRef.get();
//							a.updateViewForRow(id);
//							if(shotExt ==null)
//								a.thriveIfNeeded(id);
//							notified = true;
//						}
						break;
					default:
						break;
				}
			}
		};
		
		StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
				.detectDiskReads().detectDiskWrites().detectNetwork()
				.penaltyLog().build());
		try {
			URL url = new URL("https://github.com/tesseract-ocr/tessdata_best/raw/main/eng.traineddata");
			url = new URL("https://hub.fastgit.xyz/tesseract-ocr/tessdata_best/raw/main/eng.traineddata");
			info = new DownloadInfo(url);
			info.extract(abort, notify);
			info.setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/99.0.4840.0 Safari/537.36");
			WGet w = new WGet(info, new File(getExternalCacheDir(), "eng.tmp"));
			w.download(abort, notify);
		} catch (Exception e) {
			CMN.Log(e);
		}
		
	}
	
}
