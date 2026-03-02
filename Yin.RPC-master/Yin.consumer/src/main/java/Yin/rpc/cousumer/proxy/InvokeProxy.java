package Yin.rpc.cousumer.proxy;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.rmi.Remote;
import java.util.HashMap;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;
import org.springframework.stereotype.Component;

import Yin.rpc.cousumer.annotation.RemoteInvoke;
import Yin.rpc.cousumer.core.NettyClient;
import Yin.rpc.cousumer.param.ClientRequest;
import Yin.rpc.cousumer.param.Response;

@Component
public class InvokeProxy implements BeanPostProcessor {
	public static Enhancer enhancer = new Enhancer();

	public Object postProcessAfterInitialization(Object bean, String arg1) throws BeansException {
		return bean;
	}
	//对属性的所有方法和属性类型放入到HashMap中
	private void putMethodClass(HashMap<Method, Class> methodmap, Field field) {
		Method[] methods = field.getType().getDeclaredMethods();
		for(Method method : methods){
			methodmap.put(method, field.getType());
		}
		
	}


	public Object postProcessBeforeInitialization(Object bean, String arg1) throws BeansException {
//		System.out.println(bean.getClass().getName());

		//拿到当前 Bean 的所有属性字段（fields）
		//遍历这些字段，看看谁头上戴着 @RemoteInvoke 这个注解
		// （在你的 RemoteInvokeTest 测试类里，TestRemote userremote 就戴着这个注解）。
		Field[] fields = bean.getClass().getDeclaredFields();
		for(Field field : fields){
			if(field.isAnnotationPresent(RemoteInvoke.class)){
				field.setAccessible(true);
				//找到后，调用 field.setAccessible(true) 强行打破 Java 的 private 封装限制，准备给这个字段塞一个假对象进去
//
//				final HashMap<Method, Class> methodmap = new HashMap<Method, Class>();
//				putMethodClass(methodmap,field);
//				Enhancer enhancer = new Enhancer();

				enhancer.setInterfaces(new Class[]{field.getType()});
				enhancer.setCallback(new MethodInterceptor() {
					
					public Object intercept(Object instance, Method method, Object[] args, MethodProxy proxy) throws Throwable {
						ClientRequest clientRequest = new ClientRequest();//拿集装箱

						clientRequest.setContent(args[0]);//装货：把入参装进去（注意这里只取了第一个参数 args[0]）
//
//						String command= methodmap.get(method).getName()+"."+method.getName();

						String command = method.getName();//贴标签：告诉服务器我要调 "testUser" 方法
//
//						System.out.println("InvokeProxy中的Command是:"+command);

						clientRequest.setCommand(command);
						
						Response response = NettyClient.send(clientRequest);
						return response;
					}
				});
				try {
					field.set(bean, enhancer.create());
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		
		return bean;
	}

}
