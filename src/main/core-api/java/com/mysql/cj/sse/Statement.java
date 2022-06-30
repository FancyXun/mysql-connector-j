package com.mysql.cj.sse;

import java.sql.SQLException;

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

}
