package io.selendroid.server.handler.uiautomatorv2handler;

import android.support.test.uiautomator.UiObject2;
import io.selendroid.server.model.AndroidElement;

/**
 * Created by prakashg on 25/9/15.
 */
public abstract class AndroidElementv2 implements AndroidElement{
    private final UiObject2 el;
    private String id;

    public AndroidElementv2(UiObject2 el, String id){
        this.el = el;
        this.id = id;
    }

    public void click() {
        el.click();
    }
}
