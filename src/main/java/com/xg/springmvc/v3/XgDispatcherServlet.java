package com.xg.springmvc.v3;

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
import java.util.Map.Entry;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @program: springmvc
 * @description:
 * @author: gzk
 * @create: 2019-12-19 14:59
 **/
public class XgDispatcherServlet extends HttpServlet {
    
    
    private static final String LOCATION = "contextConfigLocation";
    
    //保存配置文件
    private Properties properties = new Properties();
    //保存类名称
    private List<String> classNames = new ArrayList<>();
    
    private Map<String,Object> ioc = new HashMap<String,Object>();
    //保存url和方法的映射关系
    private List<Handler> handlerMapping = new ArrayList<>();

    ClassLoader classLoader = this.getClass().getClassLoader();
    
    public XgDispatcherServlet(){super();}
    
    public void init(ServletConfig config){
       
        try {
            //1 加载配置文件
            doLoadConfig(config.getInitParameter(LOCATION));
            
            //2 扫描相关的类
            doScanner(properties.getProperty("scanPackage"));
            
            //3 初始化所有相关类的实例，并保存到ioc中
            doInstance();

            //4 依赖注入
            doAutowired();

            //5 构造HandlerMapping
            initHandlerMapping();

            System.out.println("XgDispatcherServlet init success !");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


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
            resp.getWriter().write("500 啦 啦 啦 啦 啦 啦 啦 啦 啦 啦 啦 啦 ");
        }
    }

    private void doDispatcher(HttpServletRequest req, HttpServletResponse resp)throws Exception  {
        Handler handler = getHandler(req);
        if(handler == null){
            resp.getWriter().write("doDispathcher Handler handel is null");
            return;
        }
        Class<?>[] paramTypes = handler.method.getParameterTypes();
        Object [] paramValue = new Object[paramTypes.length];

        Map<String,String []> params = req.getParameterMap();
        for (Entry<String,String [] > param : params.entrySet()) {
            String value = Arrays.toString(param.getValue()).replaceAll("\\[|\\]", "").replaceAll(",\\s", ",");

            if(!handler.paramIndexMapping.containsKey(param.getKey())){continue;}
            Integer index = handler.paramIndexMapping.get(param.getKey());
            paramValue[index] = convert(paramTypes[index],value);
        }
        Integer reqIndex = handler.paramIndexMapping.get(HttpServletRequest.class.getName());
        Integer respIndex = handler.paramIndexMapping.get(HttpServletResponse.class.getName());
        if(reqIndex != null){
            paramValue[reqIndex] = req;
        }
        if(respIndex != null){
            paramValue[respIndex] = resp;
        }


        Map<Object, Object> map = new HashMap<>();
        for (int i = 0; i < paramValue.length; i++) {
            if(paramValue[i] != null){continue;}
            String value = "";
            for (Entry<String,String []> param : params.entrySet()) {
                if(map.containsKey(param.getKey())){continue;}
                value = Arrays.toString(param.getValue()).replaceAll("\\[|\\]", "").replaceAll(",\\s", ",");
                map.put(param.getKey(),"");
            }
            paramValue[i] = convert(paramTypes[i],value);
        }


        handler.method.invoke(handler.controller, paramValue);

    }

    private Object convert(Class<?> paramType, String value) {
        if(paramType == Integer.class){
            return Integer.valueOf(value);
        }
        return value;
    }

    private Handler getHandler(HttpServletRequest req) {
        if(handlerMapping.isEmpty()){return null;}

        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        System.out.println("contextPath : { "+ contextPath +" }");
        url = url.replace(contextPath,"").replace("/+","/");
        for (Handler handler : handlerMapping) {
            try {
                Matcher matcher = handler.pattern.matcher(url);
                if(!matcher.matches()){ continue; }
                return handler;
            } catch (Exception e){
                e.printStackTrace();
            }
        }
        return null;
    }

    private void initHandlerMapping() {
        if(ioc.isEmpty()){return;}

        for (Entry<String, Object> entry : ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();
            if(!clazz.isAnnotationPresent(XgController.class)){continue;}

            String url = "";
            if(clazz.isAnnotationPresent(XgRequestMapping.class)){
                XgRequestMapping xgRequestMapping = clazz.getAnnotation(XgRequestMapping.class);
                url = xgRequestMapping.value();
            }

            Method[] methods = clazz.getMethods();
            for (Method method : methods) {

                if(!method.isAnnotationPresent(XgRequestMapping.class)){
                    continue;
                }

                XgRequestMapping xgRequestMapping = method.getAnnotation(XgRequestMapping.class);
                String regex = ("/" + url + xgRequestMapping.value()).replaceAll("/+","/");
                Pattern pattern = Pattern.compile(regex);
                handlerMapping.add(new Handler(pattern, entry.getValue() , method));

            }

        }
    }

    private void doAutowired() throws Exception{
        if(ioc.isEmpty()){return;}

        for (Entry<String, Object> entry : ioc.entrySet()) {
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields) {
                if(!field.isAnnotationPresent(XgAutowired.class)){continue;}
                XgAutowired xgAutowired = field.getAnnotation(XgAutowired.class);
                String beanName = xgAutowired.value().trim();
                if("".equals(beanName)){
                    beanName = field.getType().getName();
                }
                field.setAccessible(true);
                field.set(entry.getValue() , ioc.get(beanName));
            }
        }
    }

    private void doInstance() throws Exception {
        if(classNames.size() == 0){return;}

        for (String className : classNames) {
            Class<?> clazz = Class.forName(className);
            if(clazz.isAnnotationPresent(XgController.class)){
                String beanName = lowerFirst(clazz.getSimpleName());
                ioc.put(beanName, clazz.newInstance());
            } else if(clazz.isAnnotationPresent(XgService.class)){
                XgService xgService = clazz.getAnnotation(XgService.class);
                String beanName = xgService.value();
                if(!"".equals(beanName.trim())){
                    ioc.put(beanName, clazz.newInstance());
                    continue;
                }
                Class<?>[] interfaces = clazz.getInterfaces();
                for (Class<?> anInterface : interfaces) {
                    ioc.put(anInterface.getName(), clazz.newInstance());
                }

            }else{
                continue;
            }
        }
    }

    private String lowerFirst(String str) {
        char [] chars = str.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }


    private void doScanner(String scanPackage) {
      URL url=  classLoader.getResource("/" + scanPackage.replaceAll("\\.","/"));
        File dir = new File(url.getFile());
        for (File file : dir.listFiles()) {
            if(file.isDirectory()){
                doScanner(scanPackage + "." + file.getName());
            } else {
                classNames.add(scanPackage + "." + file.getName().replace(".class", ""));
            }
        }
        
    }

    private void doLoadConfig(String initParameter) throws Exception{
        InputStream in = classLoader.getResourceAsStream(initParameter);
        properties.load(in);
        if(in != null){ in.close();}
    }

    private class Handler{
        protected Object controller;
        protected Method method;
        protected Pattern pattern;
        protected Map<String , Integer> paramIndexMapping;

        protected Handler(Pattern pattern, Object controller, Method method) {
            this.controller = controller;
            this.method = method;
            this.pattern = pattern;

            paramIndexMapping = new HashMap<String,Integer>();
            putParamIndexMapping(method);
        }

        private void putParamIndexMapping(Method method) {




            Class<?>[] parameterTypes = method.getParameterTypes();
            for (int i = 0; i < parameterTypes.length; i++) {
                Class<?> type = parameterTypes[i];
//                if(type == HttpServletRequest.class ||
//                   type == HttpServletResponse.class){
                    paramIndexMapping.put(type.getName(),i);
//                }
            }
        }
    }

}
