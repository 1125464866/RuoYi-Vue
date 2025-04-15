package com.ruoyi.web.controller.Queue;

import com.jacob.activeX.ActiveXComponent;
import com.jacob.com.Dispatch;
import com.ruoyi.system.domain.PsdTask;
import com.ruoyi.system.mapper.PSDMapper;
import com.ruoyi.system.mapper.PsdTaskMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;


/**
 * @author 11254$
 * @packageName:$
 * @class:$
 * @date 2025/4/15$
 */
@Component
public class TemPhotoshopJsxQuenu {

    private static final BlockingQueue<PsdTask> TASK_QUEUE = new LinkedBlockingQueue<>();
    private static volatile boolean running = true;

    private static ActiveXComponent ps;  // Photoshop 共享实例

    @Autowired
    private PsdTaskMapper psdTaskMapperTemp;

    private static PsdTaskMapper psdTaskMapper;
    @PostConstruct
    public void init() {
        psdTaskMapper = psdTaskMapperTemp;
    }

    static {
        // 启动一个单独的线程处理 Photoshop 任务
        new Thread(() -> {
            while (running) {
                try {
                    PsdTask task = TASK_QUEUE.take(); // 阻塞式获取任务
                    processTask(task);  // 执行任务
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "PhotoshopTaskProcessor").start();
    }

    public static void addTask(PsdTask task) {
        TASK_QUEUE.offer(task); // 将任务加入队列
    }

    private static synchronized ActiveXComponent getPhotoshopInstance() {
        if (ps == null) {
            ps = new ActiveXComponent("Photoshop.Application");
        }
        return ps;
    }

    private static void processTask(PsdTask task) {
        try {
            // 调用 Photoshop
            ActiveXComponent ps = new ActiveXComponent("Photoshop.Application");
            Dispatch.invoke(ps, "DoJavaScript", Dispatch.Method, new Object[]{task.getConfig()}, new int[1]);
            task.setStatus("0"); // 更新为成功
            psdTaskMapper.updatePsdTask(task);
        } catch (Exception e) {
            // 任务失败，更新状态
            task.setStatus("1");
            psdTaskMapper.updatePsdTask(task);
            System.err.println("JSX 执行失败：" + e.getMessage());
        }
    }
}
