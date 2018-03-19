package me.limeice.netlite.internal;

public class WrapEmitter {

    private final Object lock = new Object();

    public String url;

    /* 是否成功结束 */
    public volatile boolean error = true;

    public WrapEmitter(String url) {
        this.url = url;
    }

    public void lock() throws InterruptedException {
        synchronized (lock) {
            lock.wait();
        }
    }

    public void notifyAllCall() {
        synchronized (lock) {
            lock.notifyAll();
        }
    }

    @Override
    public boolean equals(Object obj) {
        return (obj instanceof WrapEmitter) && url.equals(((WrapEmitter) obj).url);
    }
}
