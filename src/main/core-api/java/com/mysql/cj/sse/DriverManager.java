package com.mysql.cj.sse;

public class DriverManager {

    public static Connection getConnection(String url, String username, String password){

        Connection connection = null;
        String ip_port = "";
        if (url.startsWith("jdbc:mysql")){
            ip_port = url.split("/")[2];
        }
        System.out.println(ip_port);


        return connection;
    }

    public static void registerDriver( Driver driver){

    }
}
