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
            String loginPageUrl = "http://passport.mop.com";
            
            //登录成功就马上跳转到检查是否已经签到过的URL，可节省一个页面的流量（在有些机型出出现，暂时不使用这个）
            //String loginSubmitUrl = "http://passport.mop.com?targetUrl=http%3a%2f%2fpassport.mop.com%2fajax%2fpunchInfo";
            
            //检查是否已经签过到的URL
            String checkSignedUrl = "http://passport.mop.com/ajax/punchInfo?date=" + System.currentTimeMillis() + "&_=" + System.currentTimeMillis();
            //提交签到信息的URL
            String signinSubmitUrl = "http://passport.mop.com/ajax/punch";
            
            //开启重试模式
            for(int i=0;i<RETRY_TIMES;i++)
            {
                //登录账号
                res = Jsoup.connect(loginPageUrl)
                        .data("loginName", user)
                        .data("loginPasswd", pwd)
                        .userAgent(UA_CHROME)
                        .timeout(TIME_OUT)
                        .referrer(loginPageUrl)
                        .followRedirects(true)
                        .ignoreContentType(true)
                        .method(Method.POST)
                        .execute();
                cookies.putAll(res.cookies());
                
                if (res.body().contains("该昵称不存在")) {
                    return new String[]{"false", "该昵称不存在"};
                } else if (res.body().contains("您输入的密码有误")) {
                    return new String[]{"false", "您输入的密码有误"};
                }
                
                res = Jsoup.connect(checkSignedUrl).cookies(cookies).userAgent(UA_CHROME).timeout(TIME_OUT).referrer(loginPageUrl).followRedirects(true).ignoreContentType(true).method(Method.GET).execute();
                cookies.putAll(res.cookies());
                System.out.println(res.body());
                
                //检查今天是否已经签过到
                //{"-1": "操作失败，系统内部错误","-5": "用户未登陆","-6": "用户没有激活","-7": "今日已打卡","-8": "MP不足","-9": "已是最高登陆天数-无需购买"};
                //{"mp":41086,"maxDays":88,"days":1,"errorCode":-7} //今日已打卡
                //{"mp":86,"maxDays":9,"days":0,"errorCode":1} //今日未打卡
                JSONObject jsonObject = new JSONObject(res.body());
                int errorCode = jsonObject.optInt("errorCode");
                //今日已打卡
                if(errorCode == -7)
                {
                    int days = jsonObject.optInt("days");
                    int mp = jsonObject.optInt("mp");
                    int maxDays = jsonObject.optInt("maxDays");
                    return new String[]{"true", String.format("今日已打卡\n连续打卡%d天\n最大连续%d天\n共%dMP", days, maxDays, mp)};
                }
                //今日未打卡
                if (errorCode == 1) {
                    //{"mp":41086,"maxDays":88,"days":1,"errorCode":1}
                    res = Jsoup.connect(signinSubmitUrl).cookies(cookies).userAgent(UA_CHROME).timeout(TIME_OUT).referrer(loginPageUrl).ignoreContentType(true).method(Method.POST).execute();
                    jsonObject = new JSONObject(res.body());
                    int errCode = jsonObject.optInt("errorCode");
                    if (errCode == 1) {
                        int days = jsonObject.optInt("days");
                        int mp = jsonObject.optInt("mp");
                        int maxDays = jsonObject.optInt("maxDays");
                        return new String[]{"true", String.format("打卡成功\n连续打卡%d天\n最大连续%d天\n共%dMP", days, maxDays, mp)};
                    } else {
                        switch (errCode) {
                            case -1:
                                resultStr = "操作失败，系统内部错误";
                                break;

                            case -5:
                                resultStr = "用户未登陆";
                                break;

                            case -6:
                                resultStr = "用户没有激活";
                                break;

                            case -7:
                                resultFlag = "true";
                                resultStr = "今日已打卡";
                                break;

                            case -8:
                                resultStr = "MP不足";
                                break;

                            case -9:
                                resultStr = "已是最高登陆天数-无需购买";
                                break;
                        }
                        return new String[]{resultFlag, resultStr};
                    }
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
