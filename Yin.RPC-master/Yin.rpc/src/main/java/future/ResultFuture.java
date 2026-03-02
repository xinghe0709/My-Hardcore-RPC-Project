package future;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import model.ClientRequest;
import model.Response;




public class ResultFuture {
	//这就是那个“储物柜”。因为它是 static 修饰的，所以不管客户端有多少个业务线程并发调用，大家都共用这一个 Map。
	// Key 是请求的唯一 ID，Value 就是当前这个 ResultFuture 对象（空盒子）。
	public final static ConcurrentHashMap<Long,ResultFuture> map = new ConcurrentHashMap<Long,ResultFuture>();

	final Lock lock = new ReentrantLock();
	private Condition condition = lock.newCondition();
	private Response response;
	private Long timeOut = 2*60*1000l;
	private Long start = System.currentTimeMillis();
	
	//当客户端准备发请求时，会 new 一个 ResultFuture。对象刚一诞生，就立刻把自己（this）和刚才那个请求的唯一 ID 绑定，并且塞进了全局大 Map 里。
	// 这相当于你拿到了 1001 的号牌，并顺手霸占了 1001 号空盒子。
	public ResultFuture(ClientRequest request){
		map.put(request.getId(), this);
	}


	//把数据发给 Netty 后，业务线程立刻调用 get() 方法要结果。这里是多线程并发控制的灵魂：
	public Response get(){
		lock.lock();
		try {
			//这里用的是 while(!done()) 而不是 if。这就是咱们理论篇讲的防止“虚假唤醒（Spurious Wakeup）”！
			// 就算线程意外醒了，一看 done() 还是 false，就会乖乖继续执行 await() 睡死过去。
			while(!done()){
				condition.await();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}finally {
			lock.unlock();
		}
		
		return this.response;
	}


	public Response get(Long time){
		lock.lock();
		try {
			while(!done()){
				condition.await(time,TimeUnit.SECONDS);
				if((System.currentTimeMillis()-start)>timeOut){
					System.out.println("Future中的请求超时");
					break;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}finally {
			lock.unlock();
		}
		
		return this.response;
		
	}

	//这几行代码，完美衔接了底层异步网络和上层同步业务！
	//这是一个静态方法
	// Netty 线程通过 response.getId() 瞬间找到了正在休眠的那个业务线程留下的 future。
	// 把数据放进去后，调用 signal() 唤醒了刚才在 await() 的业务线程。
	public static void receive(Response response){
		if(response != null){
			ResultFuture future = map.get(response.getId());//1. 拿着返回的ID去开储物柜
			if(future != null){
				Lock lock = future.lock;
				lock.lock();// 2. 锁住这个盒子
				try {
					future.setResponse(response); // 3. 把服务器返回的真数据塞进去
					future.condition.signal();// 4. 大喊一声：醒醒！数据来了！
					map.remove(future);//别忘记remove
				} catch (Exception e) {
					e.printStackTrace();
				}finally {
					lock.unlock();
				}
			}

		}
	} 

	private boolean done() {
		if(this.response != null){
			return true;
		}
		return false;
	}

	public Long getTimeOut() {
		return timeOut;
	}

	public void setTimeOut(Long timeOut) {
		this.timeOut = timeOut;
	}

	public Long getStart() {
		return start;
	}


	public Response getResponse() {
		return response;
	}

	public void setResponse(Response response) {
		this.response = response;
	}
	
	//清理线程
	static class ClearFutureThread extends Thread{

		//这个清道夫线程就是救世主。它在后台死循环盯着那个全局 Map。
		// 一旦发现某个请求待在里面的时间超过了 timeOut（默认设了 2 分钟），它就直接伪造一个状态码为 "33333" 的假 Response，
		// 然后主动调用 receive(res) 把那个苦苦等待的业务线程给唤醒！告诉它：“别等了，服务器挂了，拿这个报错抛异常去吧！”
		@Override
		public void run() {
			// 1. 加上死循环，让它成为真正的后台“永动机”
			while (true) {
				try {
					// 2. 必须休眠！这里设置每隔 5 秒执行一次扫描，避免疯狂空转把 CPU 打满
					Thread.sleep(5000);

					Set<Long> ids = map.keySet();
					for(Long id : ids){
						ResultFuture f = map.get(id);
						if(f==null){
							map.remove(id); // 注意：这里原代码 map.remove(f) 有小瑕疵，建议改成 remove(id)
						}else if(f.getTimeOut()<(System.currentTimeMillis()-f.getStart()))
						{//链路超时
							System.out.println("检测到请求超时，ID: " + id + "，准备强制唤醒业务线程！"); // 加个日志方便观测
							Response res = new Response();
							res.setId(id);
							res.setCode("33333");
							res.setMsg("链路超时");
							receive(res); // 伪造 Response，去唤醒正在 await() 的死等线程
						}
					}
				} catch (InterruptedException e) {
					// 3. 捕捉中断异常：如果程序要关闭，优雅退出循环
					System.out.println("清理线程被中断，安全退出。");
					break;
				} catch (Exception e) {
					// 防止出现其他异常导致整个线程崩溃死亡
					e.printStackTrace();
				}
			}
		}
	}
	
	static{
		ClearFutureThread clearThread = new ClearFutureThread();
		clearThread.setDaemon(true);
		clearThread.start();
	}
	
	
	
}
