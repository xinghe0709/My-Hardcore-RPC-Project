package model;

public class Response {

	//这个 id 极其关键！服务端在处理完请求后，必须把刚才 ClientRequest 传过来的那个 id 原封不动地塞到这里面。
	// 客户端拿到这个包，全靠这个 id 去那个 ConcurrentHashMap 里找对应的休眠线程
	private Long id;

	//如果你调用的方法返回了一个布尔值，或者返回了一个查询到的 User 对象，就会被放在这里。也是 Object 类型。
	private Object result;

	//网络调用随时会失败（比如服务器内部抛了 NullPointerException，或者数据库连不上了）。
	// 这时候 result 可能是空的，但服务器会把 code 改成错误码，把异常堆栈塞进 msg 里传回来，防止客户端永远死等。
	// 作者默认把 "00000" 定为成功状态。
	private String code = "00000";//00000表示成功，其他表示失败
	private String msg;//失败信息
	
	public String getCode() {
		return code;
	}
	public void setCode(String code) {
		this.code = code;
	}
	public String getMsg() {
		return msg;
	}
	public void setMsg(String msg) {
		this.msg = msg;
	}
	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}
	public Object getResult() {
		return result;
	}
	public void setResult(Object result) {
		this.result = result;
	}
	
	
}
