package com.zhy.mcvframework.v1.servlet;

import com.zhy.demo.service.IDemoService;
import com.zhy.mcvframework.annotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

public class GpDispathcherServlet extends HttpServlet {
    //配置文件
    private  Properties contextConfig = new Properties();
    //简化版的ioc容器
    private Map<String,Object> ioc = new HashMap<String, Object>();
    //保存所有类名
    private List<String> classNames = new ArrayList<String>();
    //
    private Map<String,Method> handerMapping = new HashMap<String, Method>();
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            doDispatch(req,resp);
        }catch (Exception e){
            e.printStackTrace();
            resp.getWriter().write("500 Exception,Detail is"+ Arrays.toString(e.getStackTrace()));
        }
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replaceAll(contextPath,"").replaceAll("/+","/");
        System.out.println(handerMapping.toString());
        if (!this.handerMapping.containsKey(url)) {
            resp.getWriter().write("404 Not Found!!!");
            return;
        }
        Method method = this.handerMapping.get(url);
        Map<String,String[]> parameterMap = req.getParameterMap();
        Class<?>[] parameterTypes = method.getParameterTypes();
        Object[] paramValues = new Object[parameterTypes.length];

        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> parameterType = parameterTypes[i];
            if (parameterType == HttpServletRequest.class) {
                paramValues[i] = req;
                continue;
            }else if (parameterType == HttpServletResponse.class) {
                paramValues[i] = resp;
                continue;
            } else if (String.class == parameterType) {
                //提取方法中注解的参数
                Annotation[][] pas = method.getParameterAnnotations();
                for (int j = 0; j < pas.length; j++) {
                    for (Annotation annotation:pas[j]) {
                        if (annotation instanceof GpRequestParam) {
                            String value = ((GpRequestParam) annotation).value();
                            if (!"".equals(value.trim())) {
                                String paramValue = Arrays.toString(parameterMap.get(value))
                                        .replaceAll("\\[|\\]", "")
                                        .replaceAll("\\s", "");
                                paramValues[i] = paramValue; //这个地方视乎是失败的
                            }
                        }
                    }
                }
            }

        }
        //投机取巧的方式
        //通过反射拿到method所在class，拿到class之后还是拿到class的名称
        //再调用toLowerFirstCase获得beanName
        String beanName = toLowerFirstCase(method.getDeclaringClass().getSimpleName());
        method.invoke(this.ioc.get(beanName),paramValues);
    }


    /**
     * 使用了模板模式-步骤固定
     *  //1.加载配置文件
     *  //2.扫描类
     *  //3.将类添加到ioc容器
     *  //4.依赖注入
     *  //5.初始化handerMapping
     *  //6.开始调用方法
     * @param config
     * @throws ServletException
     */
    @Override
    public void init(ServletConfig config) throws ServletException {

        doloadConfig(config.getInitParameter("contextConfigLocation"));
        System.out.println("##############"+contextConfig.getProperty("scanPackage"));
        doScanner(contextConfig.getProperty("scanPackage"));

        doInstance();

        doAutowire();

        inithanderMapping();

    }

    /**
     * 初始化url 和 Method的一对一对应关系
     */
    private void inithanderMapping() {
        if (ioc.isEmpty()){return;}

        for (Map.Entry<String,Object> entry:ioc.entrySet()
             ) {
            Class<?> clazz = entry.getValue().getClass();

            if (!clazz.isAnnotationPresent(GpController.class)){continue;}

            //保存写在类上边的@GpRequestMapping("/demo")
            String baseUrl = "";
            if (clazz.isAnnotationPresent(GpRequestMapping.class)) {
                GpRequestMapping controllerRequestMapping = clazz.getAnnotation(GpRequestMapping.class);
                baseUrl = controllerRequestMapping.value();

                for (Method method:
                        clazz.getMethods()) {
                    if (!method.isAnnotationPresent(GpRequestMapping.class)) {continue;}
                    GpRequestMapping gpRequestMapping = method.getAnnotation(GpRequestMapping.class);
                    String value = gpRequestMapping.value();
                    System.out.println("##########"+baseUrl);

                    StringBuffer stringBuffer = new StringBuffer();
                    stringBuffer.append("/");
                    stringBuffer.append(baseUrl);

                    stringBuffer.append("/");
                    stringBuffer.append(value);
                    String url = stringBuffer.toString().replaceAll("/+", "/");
                    System.out.println("#########"+url);
                    handerMapping.put(url,method);
                    System.out.println("Mapped url is "+url+"; method is "+method);
                }
            }
        }
    }


    /**
     * 依赖注入
     */
    private void doAutowire() {
        if (ioc.isEmpty()) {
            return;
        }

        for (Map.Entry<String,Object> entry: ioc.entrySet()) {
            //Declared 所有的,特定的,字段,包括private/protected/private
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field:
                 fields) {
                if (!field.isAnnotationPresent(GpAutowired.class)){continue;}
                GpAutowired gpAutowired = field.getAnnotation(GpAutowired.class);
                String beanName = gpAutowired.value().trim();
                if ("".equals(beanName)) {
                    beanName = field.getType().getName();
                }
                //如果是public以外的修饰符,只要加了@Autowire注解 都要强制赋值
                //反射中的叫做暴力访问
                field.setAccessible(true);

                try {
                    //反射机制,动态给字段赋值
                    System.out.println("####doAutowire####"+beanName);
                    System.out.println("####doAutowire####"+ioc.get(beanName));
                    System.out.println("####doAutowire####"+entry.getValue());
                    field.set(entry.getValue(),ioc.get(beanName));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 初始化ioc容器
     *  为DI做准备
     */
    private void doInstance() {
        if (classNames.isEmpty()) {
            return;
        }
        try {
            //需要判断什么样的类需要初始化
            for (String className : classNames) {
                Class<?> clazz = Class.forName(className);
                if (clazz.isAnnotationPresent(GpController.class)) {
                    Object instance = clazz.newInstance();
                    String simpleName = clazz.getSimpleName();
                    System.out.println("##########"+simpleName);
                    String beanName = toLowerFirstCase(clazz.getSimpleName());
                    ioc.put(beanName,instance);
                }else if (clazz.isAnnotationPresent(GpService.class)){
                    GpService gpservice = clazz.getAnnotation(GpService.class);
                    String beanName = gpservice.value();
                    if ("".equals(beanName)) {
                        beanName = toLowerFirstCase(clazz.getSimpleName());
                    }

                    Object instance = clazz.newInstance();

                    ioc.put(beanName,instance);
                    //根据类型自动赋值,投机取巧的方式
                    for (Class<?> c:clazz.getInterfaces()) {
                        if (ioc.containsKey(c.getName())) {
                            throw new Exception("The "+c.getName()+"is exists");
                        }
                        ioc.put(c.getName(),instance);
                    }
                }else {
                    continue;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    //将首字母小写
    private String toLowerFirstCase(String className) {
        char[] chars = className.toCharArray();
        chars[0]+=32;
        return String.valueOf(chars);
    }

    /**
     * 扫描相关类
     * @param scanPackage
     */
    private void doScanner(String scanPackage) {
        System.out.println("############"+scanPackage);
        //将包路径转换文件路径就可以了
        URL url = this.getClass().getClassLoader().getResource(
                "/" + scanPackage.replaceAll("\\.", "/"));
        File classPath = new File(url.getFile());
        for (File f :
                classPath.listFiles()) {
            if (f.isDirectory()) {
                doScanner(scanPackage+"."+f.getName());
            } else {
                if (!f.getName().endsWith(".class")){
                    continue;
                }
                String className = scanPackage+"."+f.getName().replace(".class","");
                classNames.add(className);
            }
        }

    }

    /**
     * 加载配置文件方法
     * @param contextConfigLocation
     */
    private void doloadConfig(String contextConfigLocation) {
        InputStream fis = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
        try {
            contextConfig.load(fis);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (null!=fis) {
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
