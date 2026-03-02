package model;

import java.util.concurrent.atomic.AtomicLong;

public class ClientRequest {
	private Long id ;

	//这里声明的是 Object。比如你发了一个 User 对象过来，就会放在这里。等过会儿被 FastJson 序列化时，它会被变成一串 JSON 文本。
	private Object content;//方法参数

	//用了一个被 static 修饰的 AtomicLong（线程安全的原子长整型）。因为是 static 的，所以整个 JVM 进程里只有这一份。
	// 每次 new ClientRequest() 时，构造方法就会调用 incrementAndGet()。
	// 这完美实现了咱们之前聊的：并发再高，每个请求的 id（也就是 requestID）都绝对不会重复！
	private static AtomicLong realID = new AtomicLong(0);

	//这是个字符串。当客户端想调 UserService 的 saveUser 方法时，就会把这个方法名（或者某种规则的 key）塞进 command 里。
	// 服务器端全靠这个字符串去定位真正的执行代码。
	private String command;//media.map里的key
	
	
	public String getCommand() {
		return command;
	}

	public void setCommand(String command) {
		this.command = command;
	}

	public ClientRequest(){
		id =  realID.incrementAndGet();
	}

	public Object getContent() {
		return content;
	}

	public void setContent(Object content) {
		this.content = content;
	}
	
	public Long getId() {
		return id;
	}
	
	
}
