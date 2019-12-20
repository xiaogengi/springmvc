package com.xg.demo.controller;

import com.xg.mvc.annotation.XgController;
import com.xg.mvc.annotation.XgRequestMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.Charset;

/**
 * @program: springmvc
 * @description:p
 * @author: gzk
 * @create: 2019-12-18 18:43
 **/
@XgController
public class OrderController {


    @XgRequestMapping("order")
    public void order(String param, HttpServletResponse resp) throws IOException {
        resp.getWriter().write("hi ~ " + new String(param));
        System.out.println("OrderController .. order hi ~ " + param);
    }

}
