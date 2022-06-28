package com.mysql.cj;


import com.mysql.cj.sse.Connection;
import com.mysql.cj.sse.ResultSet;
import com.mysql.cj.sse.Statement;
import com.mysql.cj.sse.DriverManager;

import java.sql.SQLException;


public class SSETest {
    public static void main(String[] args) {

        Connection conn = null;
        Statement st = null;
        ResultSet rt = null;
        try {
            //注册连接(可以写到一行里面)
            //com.mysql.cj.jdbc.Driver Driver = new com.mysql.cj.jdbc.Driver();
            DriverManager.registerDriver(new com.mysql.cj.sse.Driver());
            //获取数据库连接
            conn=DriverManager.getConnection("jdbc:mysql://localhost:3306/liu2?serverTimezone=GMT%2B8","用户名","密码");
            //获取数据库操作对象
            st = conn.createStatement();
            //执行sql语句
            String sql = "select name,sal from emp order by sal desc";
            rt = st.executeQuery(sql);
            //处理查询语句
            while(rt.next()){
                String ename = rt.getString("ename");
                String sal = rt.getString("sal");
                System.out.println(ename + "," + sal);
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }finally {
            //关闭连接
            if (rt != null) {
                try {
                    rt.close();
                } catch (SQLException throwables) {
                    throwables.printStackTrace();
                }
            }

            if (st != null) {
                try {
                    st.close();
                } catch (SQLException throwables) {
                    throwables.printStackTrace();
                }
            }

            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException throwables) {
                    throwables.printStackTrace();
                }
            }


        }
    }
}

