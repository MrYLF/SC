版本：struts2-json-plugin-2.2.1

解压：struts2-json-plugin-2.2.1.jar
	文件：struts-plugin.xml
		内容：
		
			<?xml version="1.0" encoding="UTF-8" ?>
			<!DOCTYPE struts PUBLIC
					"-//Apache Software Foundation//DTD Struts Configuration 2.0//EN"
					"http://struts.apache.org/dtds/struts-2.0.dtd">

			<struts>
				<package name="json-default" extends="struts-default">
					<result-types>
						<result-type name="json" class="org.apache.struts2.json.JSONResult"/>
					</result-types>
				<interceptors>
						<interceptor name="json" class="org.apache.struts2.json.JSONInterceptor"/>
					</interceptors>
				</package>
			</struts>
			
			
说明：org.apache.struts2.json.JSONResult为结果类型（此源码重点）；org.apache.struts2.json.JSONInterceptor为拦截器：负责拦截客户端到json-default包下的所有请求，并检查客户端提交的数据是否是JSON类型，如果是则根据指定配置来反序列化JSON数据到action中的bean中		
使用：1.JSONResult.java->2.JSONUtil.java->3.JSONWriter.java