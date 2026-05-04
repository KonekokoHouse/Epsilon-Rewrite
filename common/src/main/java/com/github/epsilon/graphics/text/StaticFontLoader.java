package com.github.epsilon.graphics.text;

import com.github.epsilon.assets.resources.ResourceLocationUtils;
import com.github.epsilon.graphics.text.ttf.TtfFontLoader;

public class StaticFontLoader {

    public static final TtfFontLoader DEFAULT = new TtfFontLoader(ResourceLocationUtils.getIdentifier("fonts/font.ttf"));

    public static final TtfFontLoader DUCKSANS = new TtfFontLoader(ResourceLocationUtils.getIdentifier("fonts/ducksans.ttf"));

    public static final TtfFontLoader ICONS = new TtfFontLoader(ResourceLocationUtils.getIdentifier("fonts/icon.ttf"));

    public static final TtfFontLoader JURA = new TtfFontLoader(ResourceLocationUtils.getIdentifier("fonts/Jura-Light.ttf"));

}
