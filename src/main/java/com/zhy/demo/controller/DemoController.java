package com.zhy.demo.controller;

import com.zhy.demo.service.IDemoService;
import com.zhy.demo.service.impl.DemoService;
import com.zhy.mcvframework.annotation.GpAutowired;
import com.zhy.mcvframework.annotation.GpController;
import com.zhy.mcvframework.annotation.GpRequestMapping;
import com.zhy.mcvframework.annotation.GpRequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@GpController
@GpRequestMapping(value = "/demo")
public class DemoController {
    @GpAutowired
    private IDemoService demoService;

    @GpRequestMapping(value = "/query")
    public void query(HttpServletRequest req, HttpServletResponse resp,
                      @GpRequestParam(value = "name")String name){
            System.out.println("#########"+demoService.toString());
            String result = demoService.get(name);
            try {
                resp.getWriter().write(result);
            }catch (Exception e){
                e.printStackTrace();
            }

    }

    @GpRequestMapping(value = "/add")
    public void add(HttpServletRequest req, HttpServletResponse resp,
                      @GpRequestParam(value = "a")String a,@GpRequestParam(value = "b")String b){
        try {
            resp.getWriter().write(a+"+"+b +"="+(a+b));
        }catch (Exception e){
            e.printStackTrace();
        }
    }


}
