package me.limeice.netlite.internal;

import java.util.concurrent.atomic.AtomicBoolean;

public class WrapEmitter {

    public final Object lock = new Object();

    public String url;

    /* 是否成功结束 */
    public volatile boolean error = true;

    public WrapEmitter(String url) {
        this.url = url;
    }
}
