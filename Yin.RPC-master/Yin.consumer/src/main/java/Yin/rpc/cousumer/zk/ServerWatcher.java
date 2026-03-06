package Yin.rpc.cousumer.zk;

import java.util.HashSet;
import java.util.List;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.CuratorWatcher;
import org.apache.zookeeper.WatchedEvent;

import Yin.rpc.cousumer.core.ChannelManager;
import Yin.rpc.cousumer.core.NettyClient;
import io.netty.channel.ChannelFuture;

public class ServerWatcher implements CuratorWatcher {

	public void process(WatchedEvent event) throws Exception {
		System.out.println("process------------------------");
		CuratorFramework client = ZooKeeperFactory.getClient();

		//这行代码是从 Zookeeper 发过来的“报警信息”（event）中，提取出到底是**哪一个文件夹（节点）**发生了变化。
		String path = event.getPath();

		//client 是 Curator 框架的客户端实例，getChildren() 是它提供的一个方法，字面意思是**“获取所有的子节点”**。
		// 触发过一次之后，如果不重新注册，下次服务器再宕机就不报警了。
		// 所以这行代码的意思是：“报警器响了，我处理一下，顺便把报警器重新挂回去，下次有事继续叫我”
		client.getChildren().usingWatcher(this).forPath(path);

		// 既然服务器集群发生了变动，那就找 ZK 重新要一份当前还活着的服务器名单
		List<String> newServerPaths = client.getChildren().forPath(path);
		System.out.println(newServerPaths);

		// 清空本地旧的路由表
		ChannelManager.realServerPath.clear();
		for(String p :newServerPaths){
			String[] str = p.split("#");

			// 解析出 IP 和 端口，塞进 realServerPath（这是一个 HashSet，自带去重功能）
			ChannelManager.realServerPath.add(str[0]+"#"+str[1]);//去重
		}

		// 1. 极其果断的操作：把客户端现有的、连着旧服务器的所有 Netty 网线全部拔掉（清空连接池）
		ChannelManager.clearChnannel();
		for(String realServer:ChannelManager.realServerPath){
			String[] str = realServer.split("#");

			// 发起 Netty 异步连接
			ChannelFuture channnelFuture = NettyClient.b.connect(str[0], Integer.valueOf(str[1]));

			// 把新建立的健康连接，塞回连接池里供业务发请求使用
			ChannelManager.addChnannel(channnelFuture);		
		}
	}
}
