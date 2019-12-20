package com.xg.springmvc.v1;

import com.xg.mvc.annotation.XgAutowired;
import com.xg.mvc.annotation.XgController;
import com.xg.mvc.annotation.XgRequestMapping;
import com.xg.mvc.annotation.XgService;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;


/**
 * @program: springmvc
 * @description:
 * @author: gzk
 * @create: 2019-12-18 18:46
 **/
public class XgDispatcherServlet extends HttpServlet {

    private Map<String,Object> map = new ConcurrentHashMap<>();
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            doDispatcher(req,resp);
        } catch (Exception e){
            e.printStackTrace();
            resp.getWriter().write("XG , System Exception  - - - 500");
        }
    }

    private void doDispatcher(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String url = req.getRequestURI();
        String path = req.getContextPath();
        url = url.replace(path,"").replaceAll("/+","/");
        if(!map.containsKey(url)){
            resp.getWriter().write("XG , System Exception - - - 404");
        }
        Method method = (Method) this.map.get(url);
        Map parameterMap = req.getParameterMap();
        method.invoke(this.map.get(method.getDeclaringClass().getName()), new Object[]{req,resp,parameterMap});
    }


    @Override
    public void init(ServletConfig config) throws ServletException {
        InputStream in = null;
        try {
            Properties properties = new Properties();
            String contextConfigLocation = config.getInitParameter("contextConfigLocation");
            in = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
            properties.load(in);
            String scanPackage =  properties.getProperty("scanPackage");
            doScanner(scanPackage);
            for (String className : map.keySet()) {
                if(!className.contains(".")){
                    continue;
                }
                Class<?> clazz = Class.forName(className);
                if(clazz.isAnnotationPresent(XgController.class)){
                    map.put(className,clazz.newInstance());
                    String url = "";
                    if(clazz.isAnnotationPresent(XgRequestMapping.class)){
                        XgRequestMapping requestMapping = clazz.getAnnotation(XgRequestMapping.class);
                        url = requestMapping.value();
                    }
                    Method[] methods = clazz.getMethods();
                    for (Method method : methods) {
                        if(!method.isAnnotationPresent(XgRequestMapping.class)){
                            continue;
                        }
                        XgRequestMapping requestMapping = method.getAnnotation(XgRequestMapping.class);
                        String methodUrl = url + "/" + requestMapping.value().replaceAll("/+","/");
                        map.put(methodUrl, method);
                    }
                }else if(clazz.isAnnotationPresent(XgService.class)){
                    XgService service = clazz.getAnnotation(XgService.class);
                    String beanName = service.value();
                    if("".endsWith(beanName)){beanName = clazz.getName();}
                    Object o = clazz.newInstance();
                    map.put(beanName, o);
                    for (Class<?> i : clazz.getInterfaces()) {
                        map.put(i.getName() , o);
                    }
                } else{
                    continue;
                }
            }

            for (Object value : map.values()) {
                if(value == null){continue;}
                Class<?> clazz = value.getClass();
                if(clazz.isAnnotationPresent(XgAutowired.class)){
                    Field[] fields = clazz.getDeclaredFields();
                    for (Field field : fields) {
                        if(field.isAnnotationPresent(XgAutowired.class)){continue;}
                        XgAutowired xgAutowired = field.getAnnotation(XgAutowired.class);
                        String beanName = xgAutowired.value();
                        if("".endsWith(beanName)){beanName = field.getType().getName();}
                        field.setAccessible(true);
                        try {
                            field.set(map.get(clazz.getName()), map.get(beanName));
                        } catch (Exception e){
                            e.printStackTrace();
                        }
                    }
                }
            }

        } catch (Exception e){
            e.printStackTrace();
        } finally {
            if(in != null){
                try { in.close(); } catch (IOException e) { e.printStackTrace(); }
            }
        }
        System.out.println("XG MVC init ok ! !");
        System.out.println(map.toString());
    }

    private void doScanner(String scanPackage) {
        URL url = this.getClass().getClassLoader().getResource("/" + scanPackage.replaceAll("\\.","/"));
        File file = new File(url.getFile());
        for (File s : file.listFiles()) {
            if(s.isDirectory()){
                doScanner(scanPackage + "." + s.getName());
            }else{
                if(!s.getName().endsWith(".class")){
                    continue;
                }
                String clazz = scanPackage + "." + s.getName().replace(".class","");
                map.put(clazz,"");
            }
        }
    }


}
