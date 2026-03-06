package Yin.rpc.cousumer.core;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.channel.ChannelFuture;

//连接池和负载均衡器
public class ChannelManager {

	//这里面装着客户端和所有服务端建立的 Netty TCP 长连接
	public static CopyOnWriteArrayList<ChannelFuture>  channelFutures = new CopyOnWriteArrayList<ChannelFuture>();

	//记录着当前所有活着的服务端的 IP 和端口
	public static  CopyOnWriteArrayList<String> realServerPath=new CopyOnWriteArrayList<String>();

	//一个从 0 开始的计数器
	public static AtomicInteger  position = new AtomicInteger(0);//先采用轮询的方式使用send

//为什么用 CopyOnWriteArrayList 和 AtomicInteger？
//因为在真实的高并发场景下，可能有几百个线程同时在发请求，也可能 Zookeeper 突然通知要清空连接池。
// 使用这两个并发安全的类，是为了保证多线程环境下的绝对安全，防止抛出并发修改异常（ConcurrentModificationException）或指针算错。

	public static void removeChnannel(ChannelFuture channel){
		channelFutures.remove(channel);
	}
	
	public static void addChnannel(ChannelFuture channel){
		channelFutures.add(channel);
	}
	public static void clearChnannel(){
		channelFutures.clear();
	}

	//当业务代码想要发请求时，它会调用这个 get 方法来“借”一根网线。这段代码实现的是经典的 轮询（Round-Robin）负载均衡算法。
	public static ChannelFuture get(AtomicInteger i) {
		
		//目前采用轮循机制
		ChannelFuture channelFuture = null;
		int size = channelFutures.size();
		if(i.get()>=size){
			channelFuture = channelFutures.get(0);
			ChannelManager.position= new AtomicInteger(1);
		}else{
			channelFuture = channelFutures.get(i.getAndIncrement());
		}
		return channelFuture;
	}
	
}
