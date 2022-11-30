package com.googlecode.tesseraction;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.InputStream;

public class Utils {
	//public static Context context;
	public static ContentResolver contentResolver;
	public static String pluinHost;
	
	public static ParcelFileDescriptor openFileDescriptor(String name) {
		ParcelFileDescriptor fd = null;
		
		try {
			int idx = name.lastIndexOf("tessdata");
			if(idx>=0) {
				name = name.substring(idx);
			}
			Uri uri = Uri.parse(PluginFileProvider.baseUri+name);
			fd = contentResolver.openFileDescriptor(uri, "rb");
		} catch (Exception e) {
			CMN.Log(e);
		}
		
		return fd;
	}
}
