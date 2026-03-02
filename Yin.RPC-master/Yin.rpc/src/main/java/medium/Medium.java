package medium;

import java.lang.reflect.Method;
import java.util.HashMap;

import com.alibaba.fastjson.JSONObject;

import model.Response;
import model.ServerRequest;

public class Medium {

	//静态全局字典 (mediamap)
	public static final HashMap<String, BeanMethod> mediamap = new HashMap<String,BeanMethod>();
	private static Medium media = null;
	
	
	private Medium(){}
	
	public static Medium newInstance(){
		if(media == null){
			media = new Medium();
		}
		
		return media;
	}


	//当服务端的 Netty 收到网络请求，并解析成 ServerRequest 对象后，
	//会把它扔给 Yin.rpc/src/main/java/medium/Medium.java 里的 process 方法：
	public Response process(ServerRequest request){
		Response result = null;
		try {
			String command = request.getCommand();//command是key.拆开快递，拿到取件码 "testUser"
			BeanMethod beanMethod = mediamap.get(command);// 2. 去刚才建好的电话本里查！
			if(beanMethod == null){
				return null;
			}
			
			Object bean = beanMethod.getBean();// 拿到真正的业务处理对象 (TestRemoteImpl)
			Method method = beanMethod.getMethod();// 拿到真正的方法

			// 3. 极其关键：参数类型转换！
			Class type = method.getParameterTypes()[0];//先只实现1个参数的方法,获取这个方法需要的参数类型
			Object content = request.getContent();
			// 因为网络传过来的 JSON 被默认解析成了普通的 Map，这里需要用 FastJson 把它强转回 User 对象
			Object args = JSONObject.parseObject(JSONObject.toJSONString(content), type);

			// 4. 见证奇迹的时刻：执行真实方法！
			result = (Response) method.invoke(bean, args);

			// 5. 把原本的 RequestID 塞进 Response，保证客户端能唤醒对应的线程
			result.setId(request.getId());
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return result;
		
	}
}
