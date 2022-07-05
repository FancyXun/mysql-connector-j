package com.mysql.cj.sse;


import com.alibaba.fastjson.JSONArray;

import java.sql.SQLException;

public class ResultSet {

    public boolean next(){
        if (this.current > 0){
            this.current = this.current - 1;
            return true;
        }
        else{
            return false;
        }
    }

    public int getCurrent() {
        return current;
    }

    public void setCurrent(int current) {
        this.current = current;
    }

    private int current = 0;

    public JSONArray getValues() {
        return values;
    }

    public void setValues(JSONArray values) {
        this.values = values;
    }

    public JSONArray getColumns() {
        return columns;
    }

    public void setColumns(JSONArray columns) {
        this.columns = columns;
    }

    private JSONArray values;
    private JSONArray columns;


    public String getString(String str){
        int index = -1;
        for (int i=0 ; i< this.getColumns().size(); i++){
            if (str.equals(this.getColumns().get(i))){
                index = i;
                break;
            }
        }
        if (index< 0){
            return null;
        }
        else{
            return this.getValues().getJSONArray(this.current).getString(index);
        }
    }

    public void close() throws SQLException {

    }


}
