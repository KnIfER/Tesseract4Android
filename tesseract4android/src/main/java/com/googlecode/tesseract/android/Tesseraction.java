package com.googlecode.tesseract.android;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;

import androidx.annotation.WorkerThread;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.InvertedLuminanceSource;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;
import com.googlecode.leptonica.android.Pix;
import com.googlecode.leptonica.android.Pixa;
import com.googlecode.leptonica.android.ReadFile;
import com.googlecode.tesseraction.CMN;
import com.googlecode.tesseraction.PluginFileProvider;
import com.googlecode.tesseraction.Utils;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

//图文之心插件侧
public class Tesseraction {
	TessBaseAPI tess;
	
	MultiFormatReader multiFormatReader;
	EnumSet<BarcodeFormat> decodeFormats;
	Map<DecodeHintType,Object> hints;
	
	public boolean initTessdata(Context pluginContext, Activity host, String path, String languages) {
		try {
			if (tess == null) {
				tess = new TessBaseAPI();
			}
			if (Utils.contentResolver == null) {
				Utils.pluinHost = host.getPackageName();
				Utils.contentResolver = pluginContext.getContentResolver();
				try {
					Utils.contentResolver.openAssetFileDescriptor(Uri.parse(PluginFileProvider.baseUri+"test"), "rb");
				} catch (Exception e) {
					CMN.Log(e.getMessage());
					CMN.Log(e);
					try {
						if (String.valueOf(e.getMessage()).contains("No content provider")) {
							Intent intent = new Intent();
							intent.setComponent(new ComponentName(pluginContext.getPackageName(), Bootstrap.class.getName()));
							host.startActivity(intent);
						}
					} catch (Exception ex) {
						CMN.Log(e);
					}
				}
				//host.startActivity();
			}
			return tess.init(path, languages);
		} catch (Exception e) {
			CMN.Log(e);
		}
		return false;
	}
	
	public void setImage(byte[] imagedata, int width, int height, int bpp, int bpl) {
		if (tess.mRecycled)
			throw new IllegalStateException();
		tess.nativeSetImageBytes(tess.mNativeData, imagedata, width, height, bpp, bpl);
	}
	public void setImage(Bitmap bmp) {
		if (tess.mRecycled)
			throw new IllegalStateException();
		Pix image = ReadFile.readBitmap(bmp);
		if (image == null) {
			throw new RuntimeException("Failed to read bitmap");
		}
		tess.nativeSetImagePix(tess.mNativeData, image.getNativePix());
		image.recycle();
	}
	
	public void setRectangle(int left, int top, int width, int height) {
		if (tess.mRecycled)
			throw new IllegalStateException();
		tess.nativeSetRectangle(tess.mNativeData, left, top, width, height);
	}
	
	public ArrayList<Rect> getWordRects() {
		ArrayList<Rect> ret = null;
		Pixa words = tess.getWords();
		if(words.size()>0) {
			ret = words.getBoxRects();
		}
		words.recycle();
		return ret;
	}
	
	@WorkerThread
	public String getHOCRText(int page) {
		if (tess.mRecycled)
			throw new IllegalStateException();
		return tess.nativeGetHOCRText(tess.mNativeData, page);
	}
	
	@WorkerThread
	public String getUTF8Text() {
		if (tess.mRecycled)
			throw new IllegalStateException();
		// Trim because the text will have extra line breaks at the end
		String text = tess.nativeGetUTF8Text(tess.mNativeData);
		return text != null ? text.trim() : null;
	}
	
	public void stop() throws InvocationTargetException, IllegalAccessException {
		if (tess!=null && !tess.mRecycled) {
			tess.stop();
		}
	}
	
	WeakReference<byte[]> TmpData = new WeakReference<>(null);
	/**复用临时数据*/
	public byte[] acquireTmpData(int size) {
		byte[] ret = TmpData.get();
		if(ret==null||ret.length<size)
		{
			TmpData.clear();
			TmpData = new WeakReference<>(ret=new byte[size]);
		}
		//else CMN.Log("reusing……", ret.length, size);
		return ret;
	}
	public String decodeQrData(byte[] data, int sWidth, int sHeight, int left, int top
			, int widthwidth, int heightheight, boolean rotate, boolean invert
		, boolean rotated	) {
		prepareZxing();
		// Go ahead and assume it's YUV rather than die.
		//if(true) return new PlanarYUVLuminanceSource(data, sWidth, sHeight, 0, 0, sWidth, sHeight, false);
		//CMN.Log("buildLuminanceSource", sWidth, sHeight, rect.left, rect.top, rect.width(), rect.height());
		//CMN.Log("scale="+a.scale, "trans="+a.vTranslate);
		if(rotated) { //竖屏模式下，图像的数据其实还是横屏的老样子。所以交换一下宽高。
			int tmp=sWidth;
			sWidth = sHeight;
			sHeight = tmp;
			
			tmp = widthwidth;
			widthwidth = heightheight;
			heightheight = tmp;
			
			tmp=left;
			left=top;
			top=sHeight-tmp-heightheight;
		}
		
		if(left<0) left=0;
		if(widthwidth+left>=sWidth) {
			widthwidth=sWidth-1-left;
		}
		if(top<0) top=0;
		if(heightheight+top>=sHeight) {
			heightheight=sHeight-1-top;
		}
		if(widthwidth<=0||heightheight<=0) {
			return null;
		}
		LuminanceSource source;
		if(rotate) {//必须旋转数据时，为节省CPU时间，只处理必要的部分。
			int size=heightheight*widthwidth;
			if(heightheight+left-1+(widthwidth+top-1)*sWidth<size) {
				CMN.Log("oops", size, data.length);
				return null;
			}
			byte[] rotatedData = acquireTmpData(size);
			for (int y = 0; y < heightheight; y++) {
				for (int x = 0; x < widthwidth; x++)
					rotatedData[x + y * widthwidth] =  data[(y+left)  + (x+top) * sWidth];
			}
			data = rotatedData;
			source =  new PlanarYUVLuminanceSource(data, heightheight, widthwidth, 0, 0,  heightheight, widthwidth, false);
		} else {
			source = new PlanarYUVLuminanceSource(data, sWidth, sHeight, left, top,  widthwidth, heightheight, false);
		}
		
		if(invert) {
			source = new InvertedLuminanceSource(source);
		}
		//CMN.Log(sWidth, sHeight, left, top,  widthwidth, heightheight);
		//source = PlanarYUVLuminanceSource(data, sWidth, sHeight, 0, 0,  sWidth, sHeight, false);
		// try_decode_source
		BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
		Result ret = null;
		try {
			ret = multiFormatReader.decodeWithState(bitmap);
		} catch (NotFoundException e) { }
		return ret == null ? null : ret.getText();
	}
	public String decodeQrBitmap(Bitmap bitmap) {
		prepareZxing();
		int sWidth=bitmap.getWidth();
		int sHeight=bitmap.getHeight();
		int[] dataByts = new int[sWidth*sHeight];//cameraManager.acquireTmpData(w * h);
		bitmap.getPixels(dataByts, 0, sWidth, 0, 0, sWidth, sHeight);
		RGBLuminanceSource source = new RGBLuminanceSource(sWidth, sHeight, dataByts);
		Result res = null;
		try {
			BinaryBitmap binaryBitmap = new BinaryBitmap(new GlobalHistogramBinarizer(source));
			res = multiFormatReader.decodeWithState(binaryBitmap);
		} catch (NotFoundException e) {
			//e.printStackTrace();
		}
		if(res==null) { // 反色
			try {
				BinaryBitmap binaryBitmap = new BinaryBitmap(new GlobalHistogramBinarizer(new InvertedLuminanceSource(source)));
				res = multiFormatReader.decodeWithState(binaryBitmap);
			} catch (NotFoundException e) {
				//e.printStackTrace();
			}
		}
		if(res==null) { // 旋转
			//from zxing-demo
			int[] rotatedData = new int[dataByts.length];
			for (int y = 0; y < sHeight; y++) {
				for (int x = 0; x < sWidth; x++)
					rotatedData[x * sHeight + sHeight - y - 1] = dataByts[x + y * sWidth];
			}
			try {
				source = new RGBLuminanceSource(sHeight, sWidth, rotatedData);
				BinaryBitmap binaryBitmap = new BinaryBitmap(new GlobalHistogramBinarizer(source));
				res = multiFormatReader.decodeWithState(binaryBitmap);
			} catch (NotFoundException e) {
				//e.printStackTrace();
			}
		}
		return res == null ? null : res.getText();
	}
	private void prepareZxing() {
		if (multiFormatReader == null) {
			multiFormatReader = new MultiFormatReader();
			decodeFormats=EnumSet.noneOf(BarcodeFormat.class);
			hints = new EnumMap<>(DecodeHintType.class);
			resetZxingArgs();
		}
	}
	public void resetZxingArgs() {
		EnumSet<BarcodeFormat> decodeFormats = this.decodeFormats;
		Map<DecodeHintType, Object> hints = this.hints;
		decodeFormats.clear();
		if (true) {
			decodeFormats.addAll(com.googlecode.tesseraction.FormatUtils.PRODUCT_FORMATS);
		}
		if (true) {
			decodeFormats.addAll(com.googlecode.tesseraction.FormatUtils.INDUSTRIAL_FORMATS);
		}
		if (true) {
			decodeFormats.addAll(com.googlecode.tesseraction.FormatUtils.QR_CODE_FORMATS);
		}
		if (true) {
			decodeFormats.addAll(com.googlecode.tesseraction.FormatUtils.DATA_MATRIX_FORMATS);
		}
		if (true) {
			decodeFormats.addAll(com.googlecode.tesseraction.FormatUtils.AZTEC_FORMATS);
		}
		if (true) {
			decodeFormats.addAll(com.googlecode.tesseraction.FormatUtils.PDF417_FORMATS);
		}
		hints.put(DecodeHintType.POSSIBLE_FORMATS, decodeFormats);
		String characterSet=null;
		if (characterSet != null) {
			hints.put(DecodeHintType.CHARACTER_SET, characterSet);
		}
		//hints.put(DecodeHintType.NEED_RESULT_POINT_CALLBACK,  mManager.UIData.frameView.getPointsCollector());
		multiFormatReader.setHints(hints);
	}
	
	public void resetQrDecoder() {
		if (multiFormatReader!=null) {
			multiFormatReader.reset();
		}
	}
}
