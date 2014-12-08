package org.cocos2dx.proxy;

import android.content.Context;
import android.view.View;

/**
 * Created by wzm on 14/12/1.
 */
public interface CocosRuntimeInterface {

    /**
     * ������url������������������
     *
     * @param url
     */
    public void initGame(String url);

    /**
     * ������������������������view
     *
     * @return
     */
    public View getCurrentView();
	/**
     * ������������
     */
    public void loadGame();
    public void viewOnPause();
    public void viewOnResume();
    public void viewOnDestroy();

}