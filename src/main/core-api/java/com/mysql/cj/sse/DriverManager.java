package com.mysql.cj.sse;

public class DriverManager {

    public static Connection getConnection(String url, String username, String password){
        String ip_port = "";
        String db = "";
        if (url.startsWith("jdbc:mysql")){
            ip_port = url.split("/")[2];
            db = url.split("/")[3].split("\\?")[0];
        }
        System.out.println(ip_port);
        URI uri = new URI();
        uri.setUrl("http://" + ip_port + "/query_jar");
        uri.setUsername(username);
        uri.setPassword(password);
        uri.setDb(db);
        Connection connection = new Connection(uri);
        return connection;
    }

    public static void registerDriver( Driver driver){

    }
}
