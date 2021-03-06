package opencontacts.open.com.opencontacts.components;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.support.annotation.DrawableRes;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;

import java.util.HashMap;
import java.util.Map;

import opencontacts.open.com.opencontacts.utils.AndroidUtils;

import static opencontacts.open.com.opencontacts.utils.ThemeUtils.getBackgroundFloatingColor;
import static opencontacts.open.com.opencontacts.utils.ThemeUtils.getPrimaryColor;

public class TintedDrawablesStore {
    public static Map<Integer, Drawable> tintedDrawables = new HashMap<>();

    public static Drawable getTintedDrawable(@DrawableRes int drawableRes, Context context){
        Drawable cachedDrawable = tintedDrawables.get(drawableRes);
        return cachedDrawable == null ? getDrawableFor(drawableRes, context) : cachedDrawable;
    }

    private static Drawable getDrawableFor(@DrawableRes int drawableRes, Context context) {
        Drawable drawable = ContextCompat.getDrawable(context, drawableRes);
        if(drawable == null) return null;
        AndroidUtils.setColorFilterUsingColor(drawable, getPrimaryColor(context));
        tintedDrawables.put(drawableRes, drawable);
        return drawable;
    }

    public static void setDrawableForFAB(@DrawableRes int drawableRes, FloatingActionButton fab, Context context) {
        fab.setImageDrawable(getTintedDrawable(drawableRes, context));
        fab.setBackgroundTintList(ColorStateList.valueOf(getBackgroundFloatingColor(context)));
    }

    public static void reset(){
        tintedDrawables.clear();
    }
}
