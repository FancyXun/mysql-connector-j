package com.mysql.cj.sse;

public class DriverManager {

    public static Connection getConnection(String url, String username, String password){
        String ip_port = "";
        if (url.startsWith("jdbc:mysql")){
            ip_port = url.split("/")[2];
        }
        System.out.println(ip_port);
        URI uri = new URI();
        uri.setUrl(ip_port);
        uri.setUsername(username);
        uri.setPassword(password);
        Connection connection = new Connection(uri);
        return connection;
    }

    public static void registerDriver( Driver driver){

    }
}
