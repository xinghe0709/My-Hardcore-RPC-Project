package factory;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;

public class ZooKeeperFactory {
	public static CuratorFramework client;
	
	public static CuratorFramework getClient(){
		if(client == null){
			//配置重试策略 (RetryPolicy)：它定义了 ExponentialBackoffRetry(1000, 3)。
			// 这意味着如果网络闪断了，它会尝试重连 3 次，且等待时间会越来越长（第一次 1 秒，第二次更久），非常智能。
			RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);//重试机制
			client = CuratorFrameworkFactory.newClient("localhost:2181", retryPolicy);
			//刚创建出来的 client 是“死”的，必须调用 client.start()，它才会真正去握手并建立连接。
			client.start();
			
		}
		
		return client;
	}
	
	public static void main(String[] args) {
		try {
			String s = "balabala";
			CuratorFramework client = ZooKeeperFactory.getClient();
			if(client != null){
				client.create().forPath("/netty1",s.getBytes());
			}
		} catch (Exception e) {
			e.printStackTrace();
			}
		}
		
}
