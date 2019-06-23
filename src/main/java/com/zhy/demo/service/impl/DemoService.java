package com.zhy.demo.service.impl;

import com.zhy.demo.service.IDemoService;
import com.zhy.mcvframework.annotation.GpService;

@GpService
public class DemoService implements IDemoService {

    @Override
    public String get(String name) {
        return "My name is "+name;
    }
}
