package com.mockcontroller.dto;

public class ConfigParamDto {
    private String key;
    private String value;
    private String startValue;
    private String type; // "int", "string", "logLevel"

    public ConfigParamDto() {
    }

    public ConfigParamDto(String key, String value, String startValue, String type) {
        this.key = key;
        this.value = value;
        this.startValue = startValue;
        this.type = type;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getStartValue() {
        return startValue;
    }

    public void setStartValue(String startValue) {
        this.startValue = startValue;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}

