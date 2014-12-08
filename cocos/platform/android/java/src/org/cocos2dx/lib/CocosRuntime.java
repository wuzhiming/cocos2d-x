package org.cocos2dx.lib;

import org.cocos2dx.proxy.CocosRuntimeInterface;

import android.content.Context;
import android.view.View;

public class CocosRuntime implements CocosRuntimeInterface {
	private Context context;
	private String url;
	private Cocos2dxView cocos2dxView;

	public CocosRuntime(Context ctx, String url) {
		this.context = ctx;
		this.url = url;
	}

	public void initGame(String url) {
		// TODO Auto-generated method stub

	}

	public View getCurrentView() {
		// TODO Auto-generated method stub
		return null;
	}

	public void loadGame() {
		// TODO Auto-generated method stub
		cocos2dxView = new Cocos2dxView(this.context);
	}

	@Override
	public void viewOnPause() {
		if(null != cocos2dxView){
			cocos2dxView.viewOnPause();
		}
		// TODO Auto-generated method stub
		
	}

	@Override
	public void viewOnResume() {
		cocos2dxView.viewOnResume();
		// TODO Auto-generated method stub
		
	}
	public void viewOnDestroy(){
		cocos2dxView.viewOnDestory();
	}
}
