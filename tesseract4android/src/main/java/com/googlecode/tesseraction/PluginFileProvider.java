package com.googlecode.tesseraction;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class PluginFileProvider extends ContentProvider {
	public final static String baseUri = "content://com.googlecode.tesseraction/";
	private final static String AUTHORITY = "com.googlecode.tesseraction";
	private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
	private static final int FileNo1 = 1;
	
	static {
		MATCHER.addURI(AUTHORITY, "data.txt", FileNo1);
	}
	
	@Override
	public boolean onCreate() {
		return true;
	}
	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
		return null;
	}
	
	@Override
	public String getType(Uri uri) {
		if (uri.toString().endsWith(".txt")) {
			return "text/plain";
		}
		return null;
	}
	
	@Override
	public Uri insert(Uri uri, ContentValues values) {
		CMN.Log("insert---->uri = "+uri);
		CMN.Log("MATCHER.match(uri) =  "+MATCHER.match(uri));
		return null;
	}
	
	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		return 0;
	}
	
	@Override
	public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		return 0;
	}
	
	
	@Override
	public AssetFileDescriptor openAssetFile(Uri uri, String mode) throws FileNotFoundException {
		CMN.Log("PluginFileProvider::openAssetFile", uri, mode, uri.getPath());
		if (uri.getPath().equals("/tessdata/chi_sim.traineddata")
			|| uri.getPath().equals("/tessdata/eng.traineddata")) {
			File file = new File(getContext().getExternalFilesDir(null), uri.getPath());
			if (!file.exists()) {
				try {
					InputStream input = getContext().getAssets().open("fast" + uri.getPath());
					file.getParentFile().mkdirs();
					FileOutputStream fout = new FileOutputStream(file);
					byte[] data = new byte[4096];
					int len;
					while((len=input.read(data))!=-1)
						fout.write(data, 0, len);
					fout.close();
				} catch (Exception e) {
					CMN.Log(e);
				}
			}
		}
		return super.openAssetFile(uri, mode);
	}
	
	@Override
	public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
		CMN.Log("PluginFileProvider::openFile::", uri);
		File file = new File(getContext().getExternalFilesDir(null), uri.getPath());
		if (file.exists()) {
			//CMN.Log("PluginFileProvider_openFile::exists");
			return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
		}
		//CMN.Log("PluginFileProvider_openFile::not exist!!");
		throw new FileNotFoundException(uri.getPath());
	}
	
}