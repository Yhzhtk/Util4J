package cn.yicha.tupo.http;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.MappedByteBuffer;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import cn.yicha.tupo.p2sp.distribute.bisect.BisectDistribute;

public class HttpClientUtil {

	static CloseableHttpClient httpclient;
	
	static {
		// http 连接池
//		PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager();
//		connManager.setMaxTotal(Integer.MAX_VALUE);
//		connManager.setDefaultMaxPerRoute(Integer.MAX_VALUE);
//		SocketConfig defaultSocketConfig = SocketConfig.custom()
//				.setTcpNoDelay(true).setSoKeepAlive(true)
//				.setSoReuseAddress(true).build();
//		connManager.setDefaultSocketConfig(defaultSocketConfig);
//
//		RequestConfig config = RequestConfig.custom().setConnectTimeout(5000)
//				.setConnectionRequestTimeout(5000).setSocketTimeout(5000)
//				.build();

//		httpclient = HttpClients
//				.custom()
//				//.setConnectionManager(connManager)
//				.setUserAgent(
//						"Mozilla/5.0 (Windows NT 5.2) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/29.0.1547.41 Safari/537.36")
//				.setDefaultRequestConfig(config).build();
		httpclient = HttpClients.createDefault();
		
	}

	/**
	 * 爬行URL内容
	 * 
	 * @param url
	 * @param charSet
	 * @return
	 * @date:2013-11-19
	 * @author:gudaihui
	 */
	public static String getUrlContent(String url, String charSet) {
		String content = null;
		try {
			HttpGet httpGet = new HttpGet(url);

			CloseableHttpResponse response1 = httpclient.execute(httpGet);
			System.out.println(response1.getStatusLine());
			HttpEntity entity = response1.getEntity();
			try {
				content = EntityUtils.toString(entity, charSet);
			} finally {
				response1.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return content;
	}

	/**
	 * 下载一个文件，返回下载文件的长度，如果返回-1则表示下载出错了
	 * 
	 * @param url
	 * @param fileName
	 * @return
	 * @date:2013-11-19
	 * @author:gudaihui
	 */
	public static long downloadFile(String url, String fileName) {
		long length = 0;
		try {
			HttpGet httpGet = new HttpGet(url);

			CloseableHttpResponse response1 = httpclient.execute(httpGet);
			System.out.println(response1.getStatusLine());
			HttpEntity entity = response1.getEntity();
			try {
				InputStream is = entity.getContent();
				FileOutputStream fos = new FileOutputStream(new File(fileName));
				int inByte;
				byte[] bytes = new byte[1024];
				while ((inByte = is.read(bytes)) != -1) {
					fos.write(bytes);
					length += inByte;
				}
				is.close();
				fos.close();
				return length;
			} finally {
				response1.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return -1;
	}

	/**
	 * 下载文件指定的位置
	 * @param url
	 * @param mbb
	 * @param startLoc
	 * @param endLoc
	 * @return
	 * @date:2013-11-20
	 * @author:gudaihui
	 */
	public static int downloadFile(String url, MappedByteBuffer mbb, long startLoc,
			long endLoc) {
		try {
			HttpGet httpGet = new HttpGet(url);

			// 拼接请求范围
			StringBuffer sb = new StringBuffer();
			sb.append("bytes=").append(startLoc).append("-");
			if (endLoc > startLoc) {
				sb.append(endLoc);
			}
			httpGet.setHeader("Range", sb.toString());

			CloseableHttpResponse response1 = httpclient.execute(httpGet);
			//System.out.println(response1.getStatusLine());
			if (response1.getStatusLine().getStatusCode() == 206) {
				HttpEntity entity = response1.getEntity();
				try {
					mbb.position((int) startLoc);
					//rf.seek(startLoc);
					InputStream is = entity.getContent();
					int length = 0;
					int blen = 0;
					byte[] bytes = new byte[1024];
					while ((blen = is.read(bytes)) != -1) {
						// rf.write(bytes, 0, blen);
						mbb.put(bytes, 0, blen);
						length += blen;
					}
					return length;
				} finally {
					// 是否需要关闭
					//response1.close();
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return -1;
	}

	/**
	 * 以二分空闲区块下载文件
	 * @param url
	 * @param bisect
	 * @param mbb
	 * @param startLoc
	 * @param endLoc
	 * @return
	 */
	public static int downloadFileJ(String url, BisectDistribute bisect, MappedByteBuffer mbb, long startLoc,
			long endLoc) {
		try {
			HttpGet httpGet = new HttpGet(url);

			// 拼接请求范围
			StringBuffer sb = new StringBuffer();
			sb.append("bytes=").append(startLoc).append("-");
			if (endLoc > startLoc) {
				sb.append(endLoc);
			}
			httpGet.setHeader("Range", sb.toString());

			CloseableHttpResponse response1 = httpclient.execute(httpGet);
			//System.out.println(response1.getStatusLine());
			if (response1.getStatusLine().getStatusCode() == 206) {
				HttpEntity entity = response1.getEntity();
				try {
					int loc = (int) startLoc;
					mbb.position(loc);
					//rf.seek(startLoc);
					InputStream is = entity.getContent();
					int length = 0;
					int blen = 0;
					byte[] bytes = new byte[bisect.getBaseSize()];
					long s = System.currentTimeMillis();
					int i = 0;
					while ((blen = is.read(bytes)) != -1) {
						// rf.write(bytes, 0, blen);
						mbb.put(bytes, 0, blen);
						length += blen;
						bisect.setBytesOk(loc, blen);
						loc += blen;
						if(bisect.hasBytesDown(loc)){
							System.out.println("Break --- " + loc);
							break;
						}
						long t = System.currentTimeMillis() - s;
						if(t != 0 && t / 1000 % 2 == i){
							System.out.println(Thread.currentThread().getName() + " loc:" + (loc + length) + " speed:" + (length * 1000 / t));
							i = 1 - i;
						}
					}
					return length;
				} finally {
					// 是否需要关闭
					//response1.close();
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return -1;
	}
	
	/**
	 * 获取文件大小
	 * 
	 * @param url
	 * @return
	 * @date:2013-11-19
	 * @author:gudaihui
	 */
	public static int getFileSize(String url) {
		int len = 0;

		HttpGet httpGet = new HttpGet(url);

		CloseableHttpResponse response1;
		try {
			response1 = httpclient.execute(httpGet);
			System.out.println(response1.getStatusLine());
			if (response1.getStatusLine().getStatusCode() == 200) {
				Header lenHeader = response1.getFirstHeader("Content-Length");
				if (lenHeader != null) {
					len = Integer.parseInt(lenHeader.getValue());
				}
			}
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return len;
	}
}
