package com.dept.mgmt.tender.collect;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内嵌 Mock 招标平台服务器（R7 全链路测试基座）
 *
 * <p>提供 3 种不同页面/接口结构的模拟平台，覆盖采集链路全部分支：
 * <ol>
 *   <li><b>JSON-API 平台</b>（免登录）：/jsonapi/list?page=N 返回 JSON 数组 + /jsonapi/detail/{id} JSON 详情</li>
 *   <li><b>HTML 表格平台</b>（免登录）：/htmllist?page=N 表格行 + 详情 .content 选择器；含缺字段/二次核验/重复项样本</li>
 *   <li><b>FORM 登录平台</b>：登录表单（会话 Cookie 校验，列表页未登录返回 401）+ 卡片式列表 + 详情页</li>
 * </ol>
 *
 * <p>同时内置反爬观测：每次请求记录到 requestLog，供限频/拟人间隔断言使用。
 *
 * @author dept-mgmt
 */
public class MockPlatformServer {

    /** FORM 平台正确凭据 */
    public static final String FORM_USER = "admin";
    public static final String FORM_PASS = "secret123";

    private HttpServer server;
    /** 请求观测日志（method path） */
    public final List<String> requestLog = new ArrayList<>();
    /** 登录失败次数观测 */
    public final Map<String, Integer> loginFails = new ConcurrentHashMap<>();
    /** flaky 端点命中计数（前 2 次 500，第 3 次起 200，验证重试机制） */
    public final Map<String, Integer> flakyHits = new ConcurrentHashMap<>();
    public int port;
    public String base;

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        base = "http://127.0.0.1:" + port;
        server.createContext("/", this::route);
        server.start();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void route(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String query = ex.getRequestURI().getQuery() == null ? "" : ex.getRequestURI().getQuery();
        requestLog.add(ex.getRequestMethod() + " " + path + "?" + query);
        try {
            if (path.startsWith("/jsonapi/list")) {
                json(ex, jsonList(Integer.parseInt(param(query, "page", "1"))));
            } else if (path.startsWith("/jsonapi/detail/")) {
                json(ex, jsonDetail(path.substring("/jsonapi/detail/".length())));
            } else if (path.startsWith("/htmllist")) {
                html(ex, htmlList(Integer.parseInt(param(query, "page", "1"))));
            } else if (path.startsWith("/htmldetail/")) {
                html(ex, htmlDetail(path.substring("/htmldetail/".length())));
            } else if (path.equals("/formlogin") && "POST".equals(ex.getRequestMethod())) {
                handleFormLogin(ex);
            } else if (path.equals("/formlogin")) {
                html(ex, "<html><body><form method='post' action='/formlogin'>"
                        + "<input name='username'/><input name='password'/>"
                        + "<button>登录</button></form></body></html>");
            } else if (path.startsWith("/formlist")) {
                handleFormList(ex, Integer.parseInt(param(query, "page", "1")));
            } else if (path.startsWith("/formdetail/")) {
                html(ex, formDetail(path.substring("/formdetail/".length())));
            } else if (path.startsWith("/flaky/")) {
                String key = path.substring("/flaky/".length());
                int hits = flakyHits.merge(key, 1, Integer::sum);
                if (hits <= 2) {
                    send(ex, 500, "text/plain", "simulated server error #" + hits);
                } else {
                    html(ex, "<html><body><div class=\"content\">恢复成功样本 MARK-FLAKY-" + key + "</div></body></html>");
                }
            } else {
                send(ex, 404, "text/plain", "not found");
            }
        } finally {
            ex.close();
        }
    }

    // ===== 平台1：JSON API（免登录，2 页共 8 条） =====

    private String jsonList(int page) {
        StringBuilder rows = new StringBuilder();
        int total = page == 1 ? 5 : page == 2 ? 3 : 0;
        for (int i = 1; i <= total; i++) {
            int id = (page - 1) * 10 + i;
            if (rows.length() > 0) {
                rows.append(",");
            }
            rows.append("{\"id\":").append(id)
                    .append(",\"title\":\"风电机组塔筒采购项目J-").append(id).append("\"")
                    .append(",\"url\":\"/jsonapi/detail/").append(id).append("\"")
                    .append(",\"publishDate\":\"2026-11-").append(id % 28 + 1 < 10 ? "0" : "").append(id % 28 + 1).append("\"}");
        }
        return "{\"code\":0,\"data\":{\"rows\":[" + rows + "]}}";
    }

    private String jsonDetail(String id) {
        return "{\"code\":0,\"data\":{\"content\":\"" + jsonContent(Integer.parseInt(id)) + "\"}}";
    }

    private String jsonContent(int id) {
        // 标准全字段样本（金额/日期/联系人口径真实）
        return "招标公告。项目名称：风电机组塔筒采购项目J-" + id + "。招标编号：TDBM-J-" + id
                + "。采购方：国电联合动力技术有限公司。预算金额：3500000元。"
                + "投标截止时间：2026-12-20 17:00。开标时间：2026-12-22 09:00。"
                + "联系人：王工 1380000" + String.format("%04d", id) + " wang@jd-power.com。"
                + "资质要求：电力工程施工总承包一级；ISO9001质量体系认证。"
                + "投标要求：密封递交；缴纳投标保证金20万元。";
    }

    // ===== 平台2：HTML 表格（免登录，1 页 4 条：全字段/REPAIRED/PENDING_VERIFY/重复URL） =====

    private String htmlList(int page) {
        if (page > 1) {
            return "<html><body><table><tr><td>无数据</td></tr></table></body></html>";
        }
        return """
                <html><body>
                <table class="news-list">
                <tr><td><a href="/htmldetail/101">风电场道路工程施工招标H-101</a></td><td class="date">2026-11-10</td></tr>
                <tr><td><a href="/htmldetail/102">风电场道路工程施工招标H-102</a></td><td class="date">2026-11-11</td></tr>
                <tr><td><a href="/htmldetail/103">风电场道路工程施工招标H-103</a></td><td class="date">2026-11-12</td></tr>
                <tr><td><a href="/htmldetail/101">风电场道路工程施工招标H-101（同URL重复发布）</a></td><td class="date">2026-11-13</td></tr>
                </table></body></html>""";
    }

    private String htmlDetail(String id) {
        String content = switch (id) {
            case "101" -> "招标公告。项目名称：风电场道路工程施工招标H-101。招标编号：TDBM-H-101。"
                    + "采购方：华远新能源建设集团。预算金额：2800000元。投标截止时间：2026-12-25。"
                    + "开标时间：2026-12-26 09:30。联系人：李工 13900001111。"
                    + "资质要求：公路工程施工总承包二级。投标要求：现场踏勘后密封递交。";
            case "102" ->
                    // 二次核验成功样本：初提取按『预算金额：』口径取不到，二次核验按『最高限价』补全
                    "招标公告。项目名称：风电场道路工程施工招标H-102。招标编号：TDBM-H-102。"
                            + "采购方：华远新能源建设集团。预算金额（最高限价）：人民币叁佰万元整。最高限价：3200000元。"
                            + "投标截止时间：2026-12-28。开标时间：2026-12-29 10:00。联系人：赵工 13900002222。"
                            + "资质要求：公路工程施工总承包二级。";
            case "103" ->
                    // PENDING_VERIFY 样本：无金额无截止日期
                    "招标公告。项目名称：风电场道路工程施工招标H-103。招标编号：TDBM-H-103。"
                            + "采购方：华远新能源建设集团。联系人：孙工 13900003333。"
                            + "资质要求：公路工程施工总承包三级。";
            default -> "未知内容";
        };
        return "<html><body><div class=\"content\">" + content + "</div></body></html>";
    }

    // ===== 平台3：FORM 登录 + 卡片列表（1 页 2 条） =====

    private void handleFormLogin(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        boolean ok = body.contains("username=" + FORM_USER) && body.contains("password=" + FORM_PASS);
        if (ok) {
            ex.getResponseHeaders().add("Set-Cookie", "SID=form-session-ok; Path=/");
            send(ex, 200, "text/html", "<html><body>登录成功，欢迎回到投标系统 <a href='/formlist'>进入列表</a> <a>退出登录</a></body></html>");
        } else {
            loginFails.merge("form", 1, Integer::sum);
            send(ex, 200, "text/html", "<html><body>登录失败：用户名或密码错误</body></html>");
        }
    }

    private void handleFormList(HttpExchange ex, int page) throws IOException {
        String cookie = ex.getRequestHeaders().getFirst("Cookie");
        if (cookie == null || !cookie.contains("SID=form-session-ok")) {
            send(ex, 401, "text/html", "<html><body>未登录，请先<a href='/formlogin'>登录</a></body></html>");
            return;
        }
        if (page > 1) {
            html(ex, "<html><body><div class='cards'>没有更多</div></body></html>");
            return;
        }
        html(ex, """
                <html><body><div class="cards">
                <div class="card"><a class="t" href="/formdetail/201">升压站电气设备采购F-201</a><span class="d">2026-11-14</span></div>
                <div class="card"><a class="t" href="/formdetail/202">升压站电气设备采购F-202</a><span class="d">2026-11-15</span></div>
                </div><a class="logout">退出登录</a></body></html>""");
    }

    private String formDetail(String id) {
        String content = "201".equals(id)
                ? "招标公告。项目名称：升压站电气设备采购F-201。招标编号：TDBM-F-201。"
                        + "采购方：北方风电运营有限公司。预算金额：5200000元。投标截止时间：2026-12-30。"
                        + "开标时间：2026-12-31 09:00。联系人：周工 13700002001。"
                        + "资质要求：电力设备销售资质；Type试验报告。投标要求：线上递交。"
                : "招标公告。项目名称：升压站电气设备采购F-202。招标编号：TDBM-F-202。"
                        + "采购方：北方风电运营有限公司。预算金额：1800000元。投标截止时间：2026-12-31。"
                        + "开标时间：2027-01-02 14:00。联系人：吴工 13700002002。"
                        + "资质要求：ISO14001环境体系认证。投标要求：两轮报价。";
        return "<html><body><div class=\"detail-main\">" + content + "</div></body></html>";
    }

    // ===== 基础工具 =====

    private void handleFormListCheck() {
    }

    private void json(HttpExchange ex, String body) throws IOException {
        send(ex, 200, "application/json", body);
    }

    private void html(HttpExchange ex, String body) throws IOException {
        send(ex, 200, "text/html", body);
    }

    private void send(HttpExchange ex, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String param(String query, String key, String dft) {
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) {
                return kv[1];
            }
        }
        return dft;
    }
}
