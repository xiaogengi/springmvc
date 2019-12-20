package com.xg.demo.controller;

import com.xg.demo.service.IDemoService;
import com.xg.mvc.annotation.XgAutowired;
import com.xg.mvc.annotation.XgController;
import com.xg.mvc.annotation.XgRequestMapping;

/**
 * @program: springmvc
 * @description:
 * @author: gzk
 * @create: 2019-12-18 18:41
 **/
@XgController
public class DemoController {


    @XgAutowired
    private IDemoService iDemoService;

    @XgRequestMapping("test")
    public String test(){
        return "DemoController .. test";
    }


}
