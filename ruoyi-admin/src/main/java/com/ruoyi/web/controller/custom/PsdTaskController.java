package com.ruoyi.web.controller.custom;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletResponse;

import cn.hutool.core.lang.UUID;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.ttl.TransmittableThreadLocal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qiniu.common.QiniuException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.coze.CozeRequestJsonUtils;
import com.ruoyi.system.coze.utils.CozeWorkflowClient;
import com.ruoyi.system.domain.PsdTask;
import com.ruoyi.system.mapper.PSDMapper;
import com.ruoyi.system.service.IPsdTaskService;
import com.ruoyi.system.service.impl.DeepSeekService;
import com.ruoyi.web.controller.Queue.PhotoshopTaskQueue;
import com.ruoyi.web.controller.Queue.PushGZHTaskQueue;
import com.ruoyi.web.controller.Queue.TemPhotoshopJsxQuenu;
import com.ruoyi.web.controller.util.qiniuyun.QiNiuYunUtil;
import org.apache.commons.lang3.StringEscapeUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.common.core.page.TableDataInfo;
import com.jacob.activeX.ActiveXComponent;
import com.jacob.com.Dispatch;

/**
 * psd任务Controller
 *
 * @author ruoyi
 * @date 2025-03-12
 */
@RestController
@RequestMapping("/psd/task")
public class PsdTaskController extends BaseController
{
    @Autowired
    private IPsdTaskService psdTaskService;

    @Autowired
    private PSDMapper psdMapper;

    @Autowired
    private PushGZHTaskQueue pushGZHTaskQueue;

    /**
     * 查询psd任务列表
     */
    @PreAuthorize("@ss.hasPermi('psd:task:list')")
    @GetMapping("/list")
    public TableDataInfo list(PsdTask psdTask)
    {
        startPage();
        List<PsdTask> list = psdTaskService.selectPsdTaskList(psdTask);
        return getDataTable(list);
    }

    /**
     * 导出psd任务列表
     */
    @PreAuthorize("@ss.hasPermi('psd:task:export')")
    @Log(title = "psd任务", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, PsdTask psdTask)
    {
        List<PsdTask> list = psdTaskService.selectPsdTaskList(psdTask);
        ExcelUtil<PsdTask> util = new ExcelUtil<PsdTask>(PsdTask.class);
        util.exportExcel(response, list, "psd任务数据");
    }

    /**
     * 获取psd任务详细信息
     */
    @PreAuthorize("@ss.hasPermi('psd:task:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(psdTaskService.selectPsdTaskById(id));
    }

    @GetMapping(value = "/byUuid/{uuid}")
    public AjaxResult getInfoByUuid(@PathVariable("uuid") String uuid)
    {
        return success(psdTaskService.selectPsdTaskByUuid(uuid));
    }

    /**
     * 新增psd任务
     */
    @PreAuthorize("@ss.hasPermi('psd:task:add')")
    @Log(title = "psd任务", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody PsdTask psdTask) throws JsonProcessingException {

        // 初始化ObjectMapper（推荐作为静态成员）
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode config = (ObjectNode) mapper.readTree(psdTask.getConfig());  // [3](@ref)
        JsonNode psdPathNode = config.path("baseConfig").path("psdLocalPath");

        if (psdPathNode.isMissingNode()) {
            throw new RuntimeException("配置中缺少 baseConfig.psdLocalPath 字段");
        }

        String psdPath = psdPathNode.asText();
        File psdFile = new File(psdPath);

        if (!psdFile.exists()) {
            throw new RuntimeException("PSD文件不存在，路径: " + psdPath);
        }

        if (!psdFile.isFile()) {
            throw new RuntimeException("指定路径不是文件: " + psdPath);
        }

        if (!psdFile.canRead()) {
            throw new RuntimeException("PSD文件不可读，请检查权限: " + psdPath);
        }

        // 保存任务到数据库
        psdTask.setCreateDate(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
        psdTask.setUuid(String.valueOf(UUID.randomUUID()));
        psdTask.setStatus("2");
        psdTask.setCreateBy(SecurityUtils.getAuthentication().getName());
        psdTaskService.insertPsdTask(psdTask);

        // 将任务放入队列，等待 Photoshop 线程执行
        PhotoshopTaskQueue.addTask(psdTask);

        return toAjax(1);
    }


    @Log(title = "psd任务", businessType = BusinessType.INSERT)
    @PostMapping("/getCoze")
    public AjaxResult getCoze(@RequestBody PsdTask psdTask) throws JsonProcessingException {
        String configString = psdTask.getConfig();
        ObjectMapper mapper = new ObjectMapper();
        // 配置解析改造
        JsonNode configNode = mapper.readTree(configString);  // [5](@ref)
        ObjectNode config = (ObjectNode) configNode;
        String accountName = config.get("baseConfig").get("accountName").asText();
        System.out.println(accountName);
        List<String> nameList = psdMapper.selectAccountByName(accountName);
        // 去重nameList
        nameList = nameList.stream().distinct().collect(Collectors.toList());
        ArrayNode historyArray = mapper.createArrayNode();
        nameList.forEach(historyArray::add);
        config.set("historyName", historyArray);

        System.err.println("历史名字： " + nameList);
//        String answer = CozeRequestJsonUtils.test_chat_completions(String.valueOf(config));
        try {
//            System.err.println("Coze API 请求开始。。。。。。");
            JsonNode jsonResponse = CozeWorkflowClient.executeWithRetry(config, psdMapper.getCheckInfo().getToken());
//            System.err.println("Coze API 请求结束。。。。。。");
            return success(jsonResponse);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 修改psd任务
     */
    @PreAuthorize("@ss.hasPermi('psd:task:edit')")
    @Log(title = "psd任务", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody PsdTask psdTask)
    {
        return toAjax(psdTaskService.updatePsdTask(psdTask));
    }

    /**
     * 删除psd任务
     */
    @PreAuthorize("@ss.hasPermi('psd:task:remove')")
    @Log(title = "psd任务", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(psdTaskService.deletePsdTaskByIds(ids));
    }

    /**
     * 审核任务
     */
    @Log(title = "psd任务", businessType = BusinessType.UPDATE)
    @PostMapping("/checkTask")
    public AjaxResult checkTask(@RequestBody PsdTask psdTask)
    {
        psdTask.setStatus("2"); // 更新为进行中
        psdTaskService.updatePsdTask(psdTask);
        TemPhotoshopJsxQuenu.addTask(psdTask);
        return toAjax(1);
    }

    @PostMapping("/pushOfficialAccount")
    public AjaxResult pushOfficialAccount(@RequestBody PsdTask psdTask){
        String realPath = psdTask.getRealPath();
        File outputDir = new File(realPath);
        File[] images = outputDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".jpg"));

        if (images == null || images.length == 0) {
            System.out.println("目录中无 JPG 文件，跳过上传。");
            throw new RuntimeException("目录中无 JPG 文件");
        }

//        File urlFile = new File(outputDir, "url.txt");
//
//        try (
//                FileWriter fw = new FileWriter(urlFile, true);
//                BufferedWriter writer = new BufferedWriter(fw)
//        ) {
//
//            for (File img : images) {
//                if (img.getName().contains("封面")) {
//                    // 不上传标题
//                    continue;
//                }
//                // 调用上传工具，返回图片 URL
//                String imageUrl = QiNiuYunUtil.uploadFile(img);
//
//                // 追加写入 url.txt，并换行
//                writer.write(imageUrl);
//                writer.newLine();  // BufferedWriter.newLine()
//
//                System.out.println("上传成功: " + img.getName() + " → " + imageUrl);
//            }
//        } catch (IOException e) {
//            System.err.println("无法打开或写入 url.txt: " + urlFile.getAbsolutePath());
//            e.printStackTrace();
//        }
//
//        System.out.println("所有图片处理完成，URL 已追加到：" + urlFile.getAbsolutePath());

        List<String> imageUrls = new ArrayList<>();

        for (File img : images) {
            if (img.getName().contains("封面")) {
                // 不上传标题
                continue;
            }
            try {
                imageUrls.add(QiNiuYunUtil.uploadFile(img));
            } catch (QiniuException e) {
                throw new RuntimeException(e);
            }
            System.out.println("上传成功: " + img.getName());
        }

        String executeId = psdTaskService.pushOfficialAccount(psdTask, imageUrls);
        psdTask.setGzhStatus("2");
        psdTaskService.updatePsdTask(psdTask);
        if (executeId != null && !executeId.isEmpty()) {
            pushGZHTaskQueue.addTask(executeId, psdTask);
        }
        return toAjax(1);
    }
}
