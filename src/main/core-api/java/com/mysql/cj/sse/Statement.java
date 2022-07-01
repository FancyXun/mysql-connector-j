package com.mysql.cj.sse;

import org.junit.platform.commons.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Map;

public class Statement {
    public URI uri;

    public Statement (URI uri){
        this.uri = uri;
    }
    public ResultSet executeQuery(String sql) throws SQLException {
        return null;
    }

    public void close() throws SQLException {

    }


    /**
     * * 向指定 URL 发送POST方法的请求 * * @param url 发送请求的 URL * @param param 请求参数，请求参数应该是
     * name1=value1&name2=value2 的形式。 * @return 所代表远程资源的响应结果
     */
    public static String sendPost(String url, String param, Map<String, String> headMap) {
        OutputStreamWriter out = null;
        BufferedReader in = null;
        String result = "";
        if (StringUtils.isNotBlank(url)) {
            try {
                URL realUrl = new URL(url);
                // 打开和URL之间的连接
                URLConnection connection = realUrl.openConnection();
                // 设置通用的请求属性
                connection.setRequestProperty("accept", "*/*");
                connection.setRequestProperty("connection", "Keep-Alive");
                connection.setRequestProperty("Content-Type", "application/json;charset=utf-8");
                if (null != headMap && headMap.size() > 0) {
                    for (Map.Entry<String, String> entry : headMap.entrySet()) {
                        connection.setRequestProperty(entry.getKey(), entry.getValue());
                    }
                }
                // 发送POST请求必须设置如下两行
                connection.setDoOutput(true);
                connection.setDoInput(true);
                // 获取URLConnection对象对应的输出流
                out = new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8);
                // 发送请求参数
                if (null != param) {
                    out.write(param);
                }
                // flush输出流的缓冲
                out.flush();
                // 定义BufferedReader输入流来读取URL的响应
                in = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while ((line = in.readLine()) != null) {
                    result += line;
                }
            } catch (Exception e) {
                System.out.println("HTTP_LOG_INFO:" + e);
            } finally {
                // 使用finally块来关闭输出流、输入流
                try {
                    if (in != null) {
                        in.close();
                    }
                    if (out != null) {
                        out.close();
                    }
                } catch (IOException ex) {
                    System.out.println("HTTP_LOG_INFO:" + ex);
                }
            }
        }
        System.out.println("post请求返回值:" + result);
        return result;
    }

}
