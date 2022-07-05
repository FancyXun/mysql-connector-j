package com.mysql.cj.sse;

import com.alibaba.fastjson.JSON;
import org.junit.platform.commons.util.StringUtils;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class Statement {
    public URI uri;

    public Statement (URI uri){
        this.uri = uri;
    }
    public ResultSet executeQuery(String sql) throws SQLException {
        Map<String, String> headMap = new HashMap<>();
        this.sendPost1(this.uri.url, "query="+sql + "&" + "db=" +
                this.uri.db + "&" + "user=" + this.uri.username + "&" + "password=" + this.uri.password);
        return null;
    }

    public void close() throws SQLException {

    }


    /**
     * * 向指定 URL 发送POST方法的请求 * * @param url 发送请求的 URL * @param param 请求参数，请求参数应该是
     * name1=value1&name2=value2 的形式。 * @return 所代表远程资源的响应结果
     */
    public  String sendPost(String url, String param, Map<String, String> headMap) {
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

    public  Map sendPost1(String curl, String param) {
        String result = "";// 返回的结果
        BufferedReader in = null;// 读取响应输入流
        Map maps = null;
        try {
            //创建连接
            URL url = new URL(curl);
            HttpURLConnection connection = (HttpURLConnection) url
                    .openConnection();
            connection.setDoOutput(true); //是否打开outputStream 相对于程序，即我们向远程服务器写入数据，默认为false，不打开
            connection.setDoInput(true);  //输入流，获取到返回的响应内容， 默认为true，所以get请求时可以不设置这个连接信息
            connection.setRequestMethod("POST"); //发送请求的方式
            connection.setUseCaches(false); //不使用缓存
            connection.setInstanceFollowRedirects(true); //重定向，一般浏览器才需要
            connection.setRequestProperty("Content-Type",
                    "application/x-www-form-urlencoded;charset=utf-8"); //设置服务器解析数据的方式

            connection.connect();

            //POST请求
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream(),"UTF-8"));
            out.write(param);
            out.flush();
            out.close();

            //读取响应
            // 定义BufferedReader输入流来读取URL的响应,并设置编码方式
            in = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
            String line;
            // 读取返回的内容
            while ((line = in.readLine()) != null) {
                result += line;
                maps = (Map)JSON.parse(line);
            }
            System.out.println("111");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Http请求方法内部问题");
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        return maps;
    }

}
