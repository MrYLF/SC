/*
 * $Id$
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.struts2.json;

import com.opensymphony.xwork2.ActionContext;
import com.opensymphony.xwork2.ActionInvocation;
import com.opensymphony.xwork2.Result;
import com.opensymphony.xwork2.inject.Inject;
import com.opensymphony.xwork2.util.ValueStack;
import com.opensymphony.xwork2.util.WildcardUtil;
import com.opensymphony.xwork2.util.logging.Logger;
import com.opensymphony.xwork2.util.logging.LoggerFactory;
import org.apache.struts2.StrutsConstants;
import org.apache.struts2.StrutsStatics;
import org.apache.struts2.json.smd.SMDGenerator;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * <!-- START SNIPPET: description --> <p/> This result serializes an action
 * into JSON. <p/> <!-- END SNIPPET: description --> <p/> <p/> <u>Result
 * parameters:</u> <p/> <!-- START SNIPPET: parameters --> <p/>
 * <ul>
 * <p/>
 * <li>excludeProperties - list of regular expressions matching the properties
 * to be excluded. The regular expressions are evaluated against the OGNL
 * expression representation of the properties. </li>
 * <p/>
 * </ul>
 * <p/> <!-- END SNIPPET: parameters --> <p/> <b>Example:</b> <p/>
 * <p/>
 * <pre>
 * &lt;!-- START SNIPPET: example --&gt;
 * &lt;result name=&quot;success&quot; type=&quot;json&quot; /&gt;
 * &lt;!-- END SNIPPET: example --&gt;
 * </pre>
 */
public class JSONResult implements Result {

    private static final long serialVersionUID = 8624350183189931165L;

    private static final Logger LOG = LoggerFactory.getLogger(JSONResult.class);

    /**
     * This result type doesn't have a default param, null is ok to reduce noise in logs
     */
    public static final String DEFAULT_PARAM = null;

    private String encoding;
    private String defaultEncoding = "ISO-8859-1";
    private List<Pattern> includeProperties;		//被包含的属性的正则表达式，这些属性的值将被序列化成JSON字符串传送到客户端解析
    private List<Pattern> excludeProperties;		//被排除的属性的正则表达式，这些属性的值在对象序列化时将被忽略
    private String root;			//根对象，即要被序列化的对象，如不指定，将序列化action中所有可被序列化的数据 
    private boolean wrapWithComments;		//是否包装成注释
    private boolean prefix;		//前缀
    private boolean enableSMD = false;
    private boolean enableGZIP = false;		//是否压缩
    private boolean ignoreHierarchy = true;		//是否忽略层次关系，即是否序列化对象父类中的属性
    private boolean ignoreInterfaces = true;	//是否忽略接口
    private boolean enumAsBean = JSONWriter.ENUM_AS_BEAN_DEFAULT;	//是否将枚举类型作为一个bean处理
    private boolean noCache = false;
    private boolean excludeNullProperties = false;		//是否排除空的属性，即是否不序列化空值属性，默认不排除，要排除则可以在struts2中<param name="excludeNullProperties">true</param>
    private int statusCode;		//HTTP状态码
    private int errorCode;		//HTTP错误码
    private String callbackParameter;
    private String contentType;		//内容类型，通常为applicattion/json，在IE中会提示下载，可以通过参数设置<param name="contentType">text/html</param>，则不会提示下载
    private String wrapPrefix;		//包装前缀
    private String wrapSuffix;		//包装后缀

    @Inject(StrutsConstants.STRUTS_I18N_ENCODING)
    public void setDefaultEncoding(String val) {
        this.defaultEncoding = val;
    }

    /**
     * Gets a list of regular expressions of properties to exclude from the JSON
     * output.
     *
     * @return A list of compiled regular expression patterns
     */
    public List<Pattern> getExcludePropertiesList() {
        return this.excludeProperties;
    }

    /**
     * Sets a comma-delimited list of regular expressions to match properties
     * that should be excluded from the JSON output.
     *
     * @param commaDelim A comma-delimited list of regular expressions
     */
    public void setExcludeProperties(String commaDelim) {
        Set<String> excludePatterns = JSONUtil.asSet(commaDelim);
        if (excludePatterns != null) {
            this.excludeProperties = new ArrayList<Pattern>(excludePatterns.size());
            for (String pattern : excludePatterns) {
                this.excludeProperties.add(Pattern.compile(pattern));
            }
        }
    }

    /**
     * Sets a comma-delimited list of wildcard expressions to match properties
     * that should be excluded from the JSON output.
     *
     * @param commaDelim A comma-delimited list of wildcard patterns
     */
    public void setExcludeWildcards(String commaDelim) {
        Set<String> excludePatterns = JSONUtil.asSet(commaDelim);
        if (excludePatterns != null) {
            this.excludeProperties = new ArrayList<Pattern>(excludePatterns.size());
            for (String pattern : excludePatterns) {
                this.excludeProperties.add(WildcardUtil.compileWildcardPattern(pattern));
            }
        }
    }

    /**
     * @return the includeProperties
     */
    public List<Pattern> getIncludePropertiesList() {
        return includeProperties;
    }

    /**
     * Sets a comma-delimited list of regular expressions to match properties
     * that should be included in the JSON output.
     *
     * @param commaDelim A comma-delimited list of regular expressions
     */
    public void setIncludeProperties(String commaDelim) {
        includeProperties = JSONUtil.processIncludePatterns(JSONUtil.asSet(commaDelim), JSONUtil.REGEXP_PATTERN);
    }

    /**
     * Sets a comma-delimited list of wildcard expressions to match properties
     * that should be included in the JSON output.
     *
     * @param commaDelim A comma-delimited list of wildcard patterns
     */
    public void setIncludeWildcards(String commaDelim) {
        includeProperties = JSONUtil.processIncludePatterns(JSONUtil.asSet(commaDelim), JSONUtil.WILDCARD_PATTERN);
    }
	//核心方法
    public void execute(ActionInvocation invocation) throws Exception {		//ActionInvocation是Action的调用者。ActionInvocation在Action的执行过程中，负责Interceptor、Action和Result等一系列元素的调度。
        ActionContext actionContext = invocation.getInvocationContext();//ActionInvocation的实例invocation调用getInvocationContext() 获取 ActionContext实例
		//续上：　ActionContext是一个Action的上下文对象，Action运行期间所用到的数据都保存在ActionContext中，之所以会存在ActionContext，是因为Struts2的Action已经与Servlet API完全分离，但有时却需要访问Servlet中的对象，如Session、Application等。则Struts2提供了一个ActionContext类，通过此类可以获得Servlet API
        HttpServletRequest request = (HttpServletRequest) actionContext.get(StrutsStatics.HTTP_REQUEST);	//HttpServletRequest继承自ServletRequest，接受客户端发送的请求	
        HttpServletResponse response = (HttpServletResponse) actionContext.get(StrutsStatics.HTTP_RESPONSE);//HttpServletResponse是专用于HTTP协议的ServletResponse子接口，它用于封装HTTP响应消息。在此返回HTTP状态码

        try {
            Object rootObject;
            rootObject = readRootObject(invocation);		//查找要序列化的对象是Action指定的对象，还是Action中的所有对象
			/*以下方法是本类的成员方法，用于将JSON字符串根据指定参数包装后发送到客户端，其中createJSONString是最核心的部分*/
            writeToResponse(response, createJSONString(request, rootObject), enableGzip(request));
        } catch (IOException exception) {
            LOG.error(exception.getMessage(), exception);
            throw exception;
        }
    }
	//该方法用于查找需要序列化的对象，是否序列化整个对象还是序列化指定的对象
    protected Object readRootObject(ActionInvocation invocation) {
        if (enableSMD) {		//序列化指定的对象
            return buildSMDObject(invocation);
        }
        return findRootObject(invocation);//Action中整个对象
    }
	//查找Action中的所有对象
    protected Object findRootObject(ActionInvocation invocation) {
        Object rootObject;
        if (this.root != null) {
            ValueStack stack = invocation.getStack();
            rootObject = stack.findValue(root);
        } else {
            rootObject = invocation.getStack().peek(); // model overrides action
        }
        return rootObject;
    }
	//以下是最最核心代码，包括如何从rootObject抽取"可以"(即是否有特殊指定某些元素不被序列化)被序列化的属性，然后包装成JSON字符串返回
    protected String createJSONString(HttpServletRequest request, Object rootObject) throws JSONException {
		//以下调用JSONUuil中具体的序列化方法serialize
        String json = JSONUtil.serialize(rootObject, excludeProperties, includeProperties, ignoreHierarchy, enumAsBean, excludeNullProperties);
        json = addCallbackIfApplicable(request, json);
        return json;
    }
	//是否压缩
    protected boolean enableGzip(HttpServletRequest request) {
        return enableGZIP && JSONUtil.isGzipInRequest(request);
    }
	//用于将包装好了的JSON字符串根据指定参数包装后发送到客户端
    protected void writeToResponse(HttpServletResponse response, String json, boolean gzip) throws IOException {
        JSONUtil.writeJSONToResponse(new SerializationParams(response, getEncoding(), isWrapWithComments(),
                json, false, gzip, noCache, statusCode, errorCode, prefix, contentType, wrapPrefix,
                wrapSuffix));
    }

    @SuppressWarnings("unchecked")
    protected org.apache.struts2.json.smd.SMD buildSMDObject(ActionInvocation invocation) {
        return new SMDGenerator(findRootObject(invocation), excludeProperties, ignoreInterfaces).generate(invocation);
    }

    /**
     * Retrieve the encoding <p/>
     *
     * @return The encoding associated with this template (defaults to the value
     *         of param 'encoding', if empty default to 'struts.i18n.encoding' property)
     */
    protected String getEncoding() {
        String encoding = this.encoding;

        if (encoding == null) {
            encoding = this.defaultEncoding;
        }

        if (encoding == null) {
            encoding = System.getProperty("file.encoding");
        }

        if (encoding == null) {
            encoding = "UTF-8";
        }

        return encoding;
    }
	//以下方法用于针对JSON的一个成员方法，其实就是一个一个加入组成成最后的JSON字符串
    protected String addCallbackIfApplicable(HttpServletRequest request, String json) {
        if ((callbackParameter != null) && (callbackParameter.length() > 0)) {
            String callbackName = request.getParameter(callbackParameter);
            if ((callbackName != null) && (callbackName.length() > 0))
                json = callbackName + "(" + json + ")";
        }
        return json;
    }

    /**
     * @return OGNL expression of root object to be serialized
     */
    public String getRoot() {
        return this.root;
    }

    /**
     * Sets the root object to be serialized, defaults to the Action
     *
     * @param root OGNL expression of root object to be serialized
     */
    public void setRoot(String root) {
        this.root = root;
    }

    /**
     * @return Generated JSON must be enclosed in comments
     */
    public boolean isWrapWithComments() {
        return this.wrapWithComments;
    }

    /**
     * Wrap generated JSON with comments
     *
     * @param wrapWithComments
     */
    public void setWrapWithComments(boolean wrapWithComments) {
        this.wrapWithComments = wrapWithComments;
    }

    /**
     * @return Result has SMD generation enabled
     */
    public boolean isEnableSMD() {
        return this.enableSMD;
    }

    /**
     * Enable SMD generation for action, which can be used for JSON-RPC
     *
     * @param enableSMD
     */
    public void setEnableSMD(boolean enableSMD) {
        this.enableSMD = enableSMD;
    }

    public void setIgnoreHierarchy(boolean ignoreHierarchy) {
        this.ignoreHierarchy = ignoreHierarchy;
    }

    /**
     * Controls whether interfaces should be inspected for method annotations
     * You may need to set to this true if your action is a proxy as annotations
     * on methods are not inherited
     */
    public void setIgnoreInterfaces(boolean ignoreInterfaces) {
        this.ignoreInterfaces = ignoreInterfaces;
    }

    /**
     * Controls how Enum's are serialized : If true, an Enum is serialized as a
     * name=value pair (name=name()) (default) If false, an Enum is serialized
     * as a bean with a special property _name=name()
     *
     * @param enumAsBean
     */
    public void setEnumAsBean(boolean enumAsBean) {
        this.enumAsBean = enumAsBean;
    }

    public boolean isEnumAsBean() {
        return enumAsBean;
    }

    public boolean isEnableGZIP() {
        return enableGZIP;
    }

    public void setEnableGZIP(boolean enableGZIP) {
        this.enableGZIP = enableGZIP;
    }

    public boolean isNoCache() {
        return noCache;
    }

    /**
     * Add headers to response to prevent the browser from caching the response
     *
     * @param noCache
     */
    public void setNoCache(boolean noCache) {
        this.noCache = noCache;
    }

    public boolean isIgnoreHierarchy() {
        return ignoreHierarchy;
    }

    public boolean isExcludeNullProperties() {
        return excludeNullProperties;
    }

    /**
     * Do not serialize properties with a null value
     *
     * @param excludeNullProperties
     */
    public void setExcludeNullProperties(boolean excludeNullProperties) {
        this.excludeNullProperties = excludeNullProperties;
    }

    /**
     * Status code to be set in the response
     *
     * @param statusCode
     */
    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    /**
     * Error code to be set in the response
     *
     * @param errorCode
     */
    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }

    public void setCallbackParameter(String callbackParameter) {
        this.callbackParameter = callbackParameter;
    }

    public String getCallbackParameter() {
        return callbackParameter;
    }

    /**
     * Prefix JSON with "{} &&"
     *
     * @param prefix
     */
    public void setPrefix(boolean prefix) {
        this.prefix = prefix;
    }

    /**
     * Content type to be set in the response
     *
     * @param contentType
     */
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getWrapPrefix() {
        return wrapPrefix;
    }

    /**
     * Text to be inserted at the begining of the response
     */
    public void setWrapPrefix(String wrapPrefix) {
        this.wrapPrefix = wrapPrefix;
    }

    public String getWrapSuffix() {
        return wrapSuffix;
    }

    /**
     * Text to be inserted at the end of the response
     */
    public void setWrapSuffix(String wrapSuffix) {
        this.wrapSuffix = wrapSuffix;
    }

    /**
     * If defined will be used instead of {@link #defaultEncoding}, you can define it with result
     * &lt;result name=&quot;success&quot; type=&quot;json&quot;&gt;
     *     &lt;param name=&quot;encoding&quot;&gt;UTF-8&lt;/param&gt;
     * &lt;/result&gt;
     *
     * @param encoding valid encoding string
     */
    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }
}
