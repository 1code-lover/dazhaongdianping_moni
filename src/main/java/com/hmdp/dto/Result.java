package com.hmdp.dto;

import java.util.List;

public class Result {
    private Boolean success;
    private String errorMsg;
    private Object data;
    private Long total;

    public Result() {
    }

    public Result(Boolean success, String errorMsg, Object data, Long total) {
        this.success = success;
        this.errorMsg = errorMsg;
        this.data = data;
        this.total = total;
    }

    public static Result ok(){
        return new Result(Boolean.TRUE, null, null, null);
    }
    public static Result ok(Object data){
        return new Result(Boolean.TRUE, null, data, null);
    }
    public static Result ok(List<?> data, Long total){
        return new Result(Boolean.TRUE, null, data, total);
    }
    public static Result fail(String errorMsg){
        return new Result(Boolean.FALSE, errorMsg, null, null);
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }
}
