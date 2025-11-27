package com.mockcontroller.dto;

import java.util.ArrayList;
import java.util.List;

public class ConfigViewDto {
    private String systemName;
    private String configVersion;
    private List<ConfigParamDto> delays = new ArrayList<>();
    private List<ConfigParamDto> stringParams = new ArrayList<>();
    private List<ConfigParamDto> intParams = new ArrayList<>();
    private ConfigParamDto loggingLevel;

    public ConfigViewDto() {
    }

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public String getConfigVersion() {
        return configVersion;
    }

    public void setConfigVersion(String configVersion) {
        this.configVersion = configVersion;
    }

    public List<ConfigParamDto> getDelays() {
        return delays;
    }

    public void setDelays(List<ConfigParamDto> delays) {
        this.delays = delays;
    }

    public List<ConfigParamDto> getStringParams() {
        return stringParams;
    }

    public void setStringParams(List<ConfigParamDto> stringParams) {
        this.stringParams = stringParams;
    }

    public List<ConfigParamDto> getIntParams() {
        return intParams;
    }

    public void setIntParams(List<ConfigParamDto> intParams) {
        this.intParams = intParams;
    }

    public ConfigParamDto getLoggingLevel() {
        return loggingLevel;
    }

    public void setLoggingLevel(ConfigParamDto loggingLevel) {
        this.loggingLevel = loggingLevel;
    }
}

