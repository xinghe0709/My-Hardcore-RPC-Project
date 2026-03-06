package model;

public class ServerRequest {
	private Long id;
	private Object content;//content 字段扮演的是“集装箱”的角色，它装载的是你进行远程调用时传递的“具体参数”
	private String command;//media.map里的key
	
	public String getCommand() {
		return command;
	}
	public void setCommand(String command) {
		this.command = command;
	}
	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}
	public Object getContent() {
		return content;
	}
	public void setContent(Object content) {
		this.content = content;
	}
	
	
}
