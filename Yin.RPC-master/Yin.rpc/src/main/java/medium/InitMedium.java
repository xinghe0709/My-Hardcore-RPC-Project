package medium;

import java.lang.reflect.Method;
import java.util.HashMap;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;

import annotation.Remote;
import annotation.RemoteInvoke;
import controller.UserController;

@Component
public class InitMedium implements BeanPostProcessor{
	//中介者

	//在第三关里，客户端把调用的方法名（command="testUser"）和参数（content=User对象）打包成了 JSON 发给了服务器。
	//现在，服务器的 Netty 线程收到了这串 JSON，但服务器内存里有成百上千个对象，它怎么知道这个 "testUser" 到底该交给谁去执行？
	//如果不用 Spring MVC 的 @RequestMapping，我们就得自己手写一套**“路由分发器”**。
	// 这就是 medium.InitMedium 和 medium.Medium 这两个类在做的事情。


	@Override
	//在bean的实例化后进行aop
	public Object postProcessAfterInitialization(Object bean, String arg1) throws BeansException {
		// 1. 搜寻猎物：谁的头上戴了 @Remote 注解？（比如你的 TestRemoteImpl）
		if(bean.getClass().isAnnotationPresent(Remote.class)){
			// 2. 拿到这个真实业务类的所有方法
			Method[] methods = bean.getClass().getDeclaredMethods();//客户端那里用的是接口，所以getSuperClass
			for(Method m : methods){
//				String key = bean.getClass().getInterfaces()[0].getName()+"."+m.getName();
				String key = m.getName(); //拿到方法名，比如 "testUser"
				HashMap<String, BeanMethod> map = Medium.mediamap;

				// 4. 把这个对象实例 (bean) 和方法反射对象 (m)，打包存进 BeanMethod 里
				BeanMethod beanMethod = new BeanMethod();
				beanMethod.setBean(bean);
				beanMethod.setMethod(m);
				// 5. 存入全局静态大字典 mediamap！
				map.put(key,beanMethod);
				System.out.println(key);
			}
		}
		return bean;
	}

	@Override
	public Object postProcessBeforeInitialization(Object bean, String arg1) throws BeansException {
		
		
		return bean;
	}

}
