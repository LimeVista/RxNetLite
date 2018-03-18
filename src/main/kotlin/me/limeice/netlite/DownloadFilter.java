package me.limeice.netlite;

import me.limeice.netlite.internal.WrapEmitter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DownloadFilter {

    /* 覆盖上个重复任务文件 */
    public static final int OVERLAY = 0;

    /* 如果该任务已经存在，阻塞当前任务，当之前任务完成时和之前任务一样，执行后续操作 */
    public static final int WAIT_AFTER = 1;

    /* 取消当前任务 */
    public static final int CANCEL = 2;

    @SuppressWarnings("WeakerAccess")
    protected List<WrapEmitter> tasks = Collections.synchronizedList(new ArrayList<WrapEmitter>());

    /* 过滤器 */
    public int filter = OVERLAY;

    /* 是否存在 */
    WrapEmitter contains(String url) {
        for (WrapEmitter emitter : tasks) {
            if (emitter.url.equals(url))
                return emitter;
        }
        return null;
    }

    /* 任务完成，结束任务 */
    void clearSuccess(String url) {
        for (WrapEmitter emitter : tasks) {
            if (!emitter.url.equals(url))
                continue;
            emitter.error = false;
            tasks.remove(emitter);
            if (filter == WAIT_AFTER)
                emitter.notifyAllCall();
            return;
        }
    }

    /* 任务终端，结束任务 */
    void clearBreak(String url) {
        for (WrapEmitter emitter : tasks) {
            if (!emitter.url.equals(url))
                continue;
            tasks.remove(emitter);
            emitter.error = true;
            tasks.remove(emitter);
            if (filter == WAIT_AFTER)
                emitter.notifyAllCall();
            return;
        }
    }

    void add(WrapEmitter wrapEmitter) {
        tasks.add(wrapEmitter);
    }
}
