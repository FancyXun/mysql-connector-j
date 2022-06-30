package com.mysql.cj.sse;

import java.sql.SQLException;

public class Connection {

    public URI uri;

    public Connection( URI uri){
        this.uri = uri;
    }

    public Statement createStatement(){
        return new Statement(this.uri);
    }
    public void close() throws SQLException {

    }
}
