package me.shumei.open.oks.mopmp;

import java.io.IOException;
import java.util.HashMap;

import org.json.JSONObject;
import org.jsoup.Connection.Method;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;

import android.content.Context;

/**
 * 使签到类继承CommonData，以方便使用一些公共配置信息
 * @author wolforce
 *
 */
public class Signin extends CommonData {
	String resultFlag = "false";
	String resultStr = "未知错误！";
	
	/**
	 * <p><b>程序的签到入口</b></p>
	 * <p>在签到时，此函数会被《一键签到》调用，调用结束后本函数须返回长度为2的一维String数组。程序根据此数组来判断签到是否成功</p>
	 * @param ctx 主程序执行签到的Service的Context，可以用此Context来发送广播
	 * @param isAutoSign 当前程序是否处于定时自动签到状态<br />true代表处于定时自动签到，false代表手动打开软件签到<br />一般在定时自动签到状态时，遇到验证码需要自动跳过
	 * @param cfg “配置”栏内输入的数据
	 * @param user 用户名
	 * @param pwd 解密后的明文密码
	 * @return 长度为2的一维String数组<br />String[0]的取值范围限定为两个："true"和"false"，前者表示签到成功，后者表示签到失败<br />String[1]表示返回的成功或出错信息
	 */
	public String[] start(Context ctx, boolean isAutoSign, String cfg, String user, String pwd) {
		//把主程序的Context传送给验证码操作类，此语句在显示验证码前必须至少调用一次
		CaptchaUtil.context = ctx;
		//标识当前的程序是否处于自动签到状态，只有执行此操作才能在定时自动签到时跳过验证码
		CaptchaUtil.isAutoSign = isAutoSign;
		
		try{
			//存放Cookies的HashMap
			HashMap<String, String> cookies = new HashMap<String, String>();
			//Jsoup的Response
			Response res;
			
			//登录页面URL
			String loginPageUrl = "http://passport.mop.com/login";
			//登录成功就马上跳转到检查是否已经签到过的URL
			String loginSubmitUrl = "http://passport.mop.com/login?url=http%3A%2F%2Fpassport.mop.com%2Fpunch-the-clock%2Fstatus";
			//检查是否已经签过到的URL
			String checkSignedUrl = "http://passport.mop.com/punch-the-clock/status";
			//提交签到信息的URL
			String signinSubmitUrl = "http://passport.mop.com/punch-the-clock/punch";
			
			//开启重试模式
			for(int i=0;i<RETRY_TIMES;i++)
			{
				//登录账号
				res = Jsoup.connect(loginSubmitUrl)
						.data("user_name", user)
						.data("password", pwd)
						.data("logintime", "")
						.data("auto_login", "0")
						.userAgent(UA_CHROME)
						.timeout(TIME_OUT)
						.referrer(loginPageUrl)
						.ignoreContentType(true)
						.method(Method.POST)
						.execute();
				cookies.putAll(res.cookies());
				
				//检查今天是否已经签过到
				//{"message":"请先登录","status":404,"redirectUrl":"/login"}
				//{"keepLoginDays":1,"mp":1,"punchable":true,"status":200,"keepLoginDaysRecord":1} //还没签过到
				//{"keepLoginDays":1,"mp":6,"punchable":false,"status":200,"keepLoginDaysRecord":1} //已签过到
				JSONObject jsonObject = new JSONObject(res.body());
				if(jsonObject.getInt("status") == 200)
				{
					int keepLoginDays = jsonObject.getInt("keepLoginDays");
					int mp = jsonObject.getInt("mp");
					if(jsonObject.getBoolean("punchable"))
					{
						//访问签到链接
						//{"mpPlused":5,"status":200}
						//{"status":404}
						res = Jsoup.connect(signinSubmitUrl).cookies(cookies).userAgent(UA_CHROME).timeout(TIME_OUT).referrer(loginPageUrl).ignoreContentType(true).method(Method.GET).execute();
						cookies.putAll(res.cookies());
						JSONObject signinJsonObj = new JSONObject(res.body());
						//System.out.println(res.body());
						if(signinJsonObj.getInt("status") == 200)
						{
							StringBuilder sb = new StringBuilder();
							sb.append("签到成功，获得");
							sb.append(signinJsonObj.getInt("mpPlused"));
							sb.append("MP，已连续签到");
							sb.append(keepLoginDays);
							sb.append("天");
							this.resultFlag = "true";
							this.resultStr = sb.toString();
							break;
						}
					}
					else
					{
						this.resultFlag = "true";
						this.resultStr = "今天已签过到，共有" + mp + "MP，已连续签到" + keepLoginDays + "天";
						break;
					}
				}
				else
				{
					this.resultFlag = "false";
					this.resultStr = "登录网站出现错误，请检查账号密码是否正确";
				}
			}
			
			
		} catch (IOException e) {
			this.resultFlag = "false";
			this.resultStr = "连接超时";
			e.printStackTrace();
		} catch (Exception e) {
			this.resultFlag = "false";
			this.resultStr = "未知错误！";
			e.printStackTrace();
		}
		
		return new String[]{resultFlag, resultStr};
	}
	
	
}
