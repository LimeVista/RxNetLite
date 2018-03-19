package me.limeice.netlite;

import me.limeice.netlite.internal.WrapEmitter;

import java.util.ArrayList;

public class DownloadFilter {

    /* 覆盖上个重复任务文件,不参与调剂，不删除之前任务 */
    public static final int DEFAULT = 0;

    /* 如果该任务已经存在，阻塞当前任务，当之前任务完成时和之前任务一样，执行后续操作 */
    public static final int WAIT_AFTER = 1;

    /* 取消当前任务 */
    public static final int CANCEL = 2;

    static class WrapData {

        static final short TASK_NONE = 1;

        static final short TASK_FIRST = 2;

        static final short TASK_WAIT_AFTER = 3;

        static final short TASK_OVERLAY = 4;

        WrapEmitter emitter;

        short type;
    }


    private final Object mutex;

    @SuppressWarnings("WeakerAccess")
    protected ArrayList<WrapEmitter> tasks = new ArrayList<>();

    /* 过滤器 */
    public int filter = DEFAULT;

    public DownloadFilter() {
        mutex = this;
    }

    WrapData checkout(String url) {
        synchronized (mutex) {
            WrapData data = new WrapData();
            for (WrapEmitter emitter : tasks) {
                if (emitter.url.equals(url)) {
                    data.emitter = emitter;
                    switch (filter) {
                        case DEFAULT:
                            data.type = WrapData.TASK_OVERLAY;
                            return data;
                        case WAIT_AFTER:
                            data.type = WrapData.TASK_WAIT_AFTER;
                            return data;
                        case CANCEL:
                            data.type = WrapData.TASK_NONE;
                            return data;
                        default:
                            data.type = WrapData.TASK_NONE;
                            return data;
                    }
                }
            }
            data.type = WrapData.TASK_FIRST;
            data.emitter = new WrapEmitter(url);
            tasks.add(data.emitter);
            return data;
        }
    }

    /* 任务完成，结束任务 */
    void clearSuccess(String url) {
        synchronized (mutex) {
            for (int i = 0; i < tasks.size(); i++) {
                WrapEmitter emitter = tasks.get(i);
                if (!emitter.url.equals(url))
                    continue;
                tasks.remove(i);
                emitter.error = false;
                tasks.remove(emitter);
                if (filter == WAIT_AFTER)
                    emitter.notifyAllCall();
                return;
            }
        }
    }

    /* 任务终端，结束任务 */
    void clearBreak(String url) {
        synchronized (mutex) {
            for (int i = 0; i < tasks.size(); i++) {
                WrapEmitter emitter = tasks.get(i);
                if (!emitter.url.equals(url))
                    continue;
                tasks.remove(i);
                emitter.error = true;
                tasks.remove(emitter);
                if (filter == WAIT_AFTER)
                    emitter.notifyAllCall();
                return;
            }
        }
    }
}
