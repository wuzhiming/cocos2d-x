package org.cocos2dx.lib;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.cocos2dx.lib.Cocos2dxGLSurfaceView;
import org.cocos2dx.lib.Cocos2dxHelper.Cocos2dxHelperListener;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.preference.PreferenceManager.OnActivityResultListener;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.FrameLayout;

public class Cocos2dxView extends Cocos2dxGLSurfaceView implements
		Cocos2dxHelperListener {
	private Context m_ctx = null;
	private Cocos2dxVideoHelper mVideoHelper = null;
	private Cocos2dxWebViewHelper mWebViewHelper = null;
	private int[] glContextAttrs;
	private Cocos2dxView m_cocoView = null;
	protected FrameLayout mFrameLayout = null;
	private Cocos2dxHandler mHandler;

	private static boolean loadLibraryFlag = false;

	public Cocos2dxView(Context context) {
		super(context);
		m_ctx = context;
		m_cocoView = this;
		CocosViewWrapper.setContext(m_ctx);
		viewOnCreate();
	}

	private void viewOnCreate() {
		Log.e("cocos", "in viewOnCreate");

		if (!loadLibraryFlag) {
			onLoadNativeLibrariesFromSdCard();
			loadLibraryFlag = true;
		}

		Activity curAct = (Activity) m_ctx;
		Log.e("cocos", "Cocos2dxHelper init start");
		Cocos2dxHelper.init(curAct, this, this.getClass().getClassLoader());
		Log.e("cocos", "Cocos2dxHelper init over");
		this.glContextAttrs = getGLContextAttrs();
		Log.e("cocos", "getGLContextAttrs over");
		this.setEGLConfigChooser(5, 6, 5, 0, 16, 8);
		init();
		Log.e("cocos", "create over");

		// this.mHandler = new Cocos2dxHandler(this);
		//
		if (mVideoHelper == null) {
			mVideoHelper = new Cocos2dxVideoHelper(this, mFrameLayout);
		}

		// if(mWebViewHelper == null){
		// mWebViewHelper = new Cocos2dxWebViewHelper(mFrameLayout);
		// }
	}

	private void init() {
		// FrameLayout
		ViewGroup.LayoutParams framelayout_params = new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT);
		mFrameLayout = new FrameLayout(m_ctx);
		mFrameLayout.setLayoutParams(framelayout_params);

		// Cocos2dxEditText layout
		ViewGroup.LayoutParams edittext_layout_params = new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT);
		Cocos2dxEditText edittext = new Cocos2dxEditText(m_ctx);
		edittext.setLayoutParams(edittext_layout_params);

		// ...add to FrameLayout
		mFrameLayout.addView(edittext);

		// ...add to FrameLayout
		// mFrameLayout.addView(this);

		if (isAndroidEmulator())
			this.setEGLConfigChooser(8, 8, 8, 8, 16, 0);

		this.setCocos2dxRenderer(new Cocos2dxRenderer());
		// this.setCocos2dxEditText(edittext);

		// Set framelayout as the content view
		// ((Activity) m_ctx).setContentView(mFrameLayout);

	}

	public void destroyGame()
	{
		destroyDirector();
	}
	
	private static native void destroyDirector();

	private static native int[] getGLContextAttrs();

	protected void onLoadNativeLibrariesFromSdCard() {
		try {

			Log.e("cocos", "load start--------------");
			String fileName = "libcocos2djs.so";
			File fis = new File("/sdcard/gameEngine/libcocos2djs.zip");
			File dir = m_ctx.getDir("gameEngine", Activity.MODE_PRIVATE);

			File nf = new File(dir.getAbsolutePath() + File.separator
					+ fileName);
			if (nf.exists()) {
				nf.deleteOnExit();
			}
			upZipFile(fis, dir.getAbsolutePath());
			System.load(dir.getAbsolutePath() + File.separator + fileName);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * 解压缩功能. 将zipFile文件解压到folderPath目录下.
	 * 
	 * @throws Exception
	 */
	public int upZipFile(File zipFile, String folderPath) throws ZipException,
			IOException {
		ZipFile zfile = new ZipFile(zipFile);
		Enumeration zList = zfile.entries();
		ZipEntry ze = null;
		byte[] buf = new byte[10240];
		while (zList.hasMoreElements()) {
			ze = (ZipEntry) zList.nextElement();
			Log.d("upZipFile", "ze.getName() = " + ze.getName());
			OutputStream os = new BufferedOutputStream(new FileOutputStream(
					folderPath + File.separator + ze.getName()));
			InputStream is = new BufferedInputStream(zfile.getInputStream(ze));
			int readLen = 0;
			while ((readLen = is.read(buf, 0, 10240)) != -1) {
				os.write(buf, 0, readLen);
			}
			is.close();
			os.close();
		}
		zfile.close();
		return 0;
	}

	@Override
	public void showDialog(final String pTitle, final String pMessage) {
		Message msg = new Message();
		msg.what = Cocos2dxHandler.HANDLER_SHOW_DIALOG;
		msg.obj = new Cocos2dxHandler.DialogMessage(pTitle, pMessage);
		this.mHandler.sendMessage(msg);
	}

	@Override
	public void showEditTextDialog(final String pTitle, final String pContent,
			final int pInputMode, final int pInputFlag, final int pReturnType,
			final int pMaxLength) {
		Message msg = new Message();
		msg.what = Cocos2dxHandler.HANDLER_SHOW_EDITBOX_DIALOG;
		msg.obj = new Cocos2dxHandler.EditBoxMessage(pTitle, pContent,
				pInputMode, pInputFlag, pReturnType, pMaxLength);
		this.mHandler.sendMessage(msg);
	}

	@Override
	public void runOnGLThread(final Runnable pRunnable) {
		this.queueEvent(pRunnable);
	}

	public void vewOnResume() {
		Cocos2dxHelper.onResume();
		this.onResume();
	}

	public void viewOnPause() {
		Cocos2dxHelper.onPause();
		this.onPause();
	}

	public void viewOnDestory() {
		//Cocos2dxHelper.end();
		destroyGame();
	}

	private final static boolean isAndroidEmulator() {
		String model = Build.MODEL;
		String product = Build.PRODUCT;
		boolean isEmulator = false;
		if (product != null) {
			isEmulator = product.equals("sdk") || product.contains("_sdk")
					|| product.contains("sdk_");
		}
		return isEmulator;
	}

	public void viewOnActivityResult(int requestCode, int resultCode,
			Intent data) {
		for (OnActivityResultListener listener : Cocos2dxHelper
				.getOnActivityResultListeners()) {
			listener.onActivityResult(requestCode, resultCode, data);
		}
	}

	public void setKeepScreenOn(boolean value) {
		final boolean newValue = value;
		((Activity) m_ctx).runOnUiThread(new Runnable() {
			@Override
			public void run() {
				m_cocoView.setKeepScreenOn(newValue);
			}
		});
	}
}
