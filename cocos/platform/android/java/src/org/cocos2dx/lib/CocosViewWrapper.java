package org.cocos2dx.lib;

import android.content.Context;

public class CocosViewWrapper {
	static Context m_act = null;
	public static void setContext(Context ctx){
		m_act = ctx;
	}
	public static Context getContext(){
		return m_act;
	}
}
