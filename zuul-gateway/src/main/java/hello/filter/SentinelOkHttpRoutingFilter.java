package hello.filter;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.adapter.servlet.callback.RequestOriginParser;
import com.alibaba.csp.sentinel.adapter.servlet.callback.UrlCleaner;
import com.alibaba.csp.sentinel.adapter.servlet.callback.WebCallbackManager;
import com.alibaba.csp.sentinel.adapter.servlet.util.FilterUtil;
import com.alibaba.csp.sentinel.context.ContextUtil;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.util.StringUtil;
import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import okhttp3.*;
import okhttp3.internal.http.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.netflix.zuul.filters.ProxyRequestHelper;
import org.springframework.cloud.netflix.zuul.util.ZuulRuntimeException;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StreamUtils;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import static org.springframework.cloud.netflix.zuul.filters.support.FilterConstants.*;

/**
 * @author tiger
 */
public class SentinelOkHttpRoutingFilter extends ZuulFilter {

    @Autowired
    private DiscoveryClient discoveryClient;

    private static final String EMPTY_ORIGIN = "";

    private Logger logger = LoggerFactory.getLogger(SentinelRibbonFilter.class);

    @Autowired
    private ProxyRequestHelper helper;

    @Override
    public String filterType() {
        return ROUTE_TYPE;
    }

    @Override
    public int filterOrder() {
        // run before ribbon filter
        return RIBBON_ROUTING_FILTER_ORDER - 1;
    }

    @Override
    public boolean shouldFilter() {
        RequestContext ctx = RequestContext.getCurrentContext();
        return (ctx.getRouteHost() == null && ctx.get(SERVICE_ID_KEY) != null
                && ctx.sendZuulResponse());
    }

    @Override
    public Object run() {
        RequestContext ctx = RequestContext.getCurrentContext();
        Entry entry = null;
        try {
            // service target
            String serviceTarget = (String) ctx.get(SERVICE_ID_KEY);
            String serviceOrigin = "origin";
            logger.info("serviceTarget:{} , serviceOrigin:{}", serviceTarget, serviceOrigin);
            ContextUtil.enter(serviceTarget, serviceOrigin);
            entry = SphU.entry(serviceTarget, EntryType.IN);
            // url target
            String urlTarget = FilterUtil.filterTarget(ctx.getRequest());
            // Clean and unify the URL.
            // For REST APIs, you have to clean the URL (e.g. `/foo/1` and `/foo/2` -> `/foo/:id`), or
            // the amount of context and resources will exceed the threshold.
            UrlCleaner urlCleaner = WebCallbackManager.getUrlCleaner();
            if (urlCleaner != null) {
                urlTarget = urlCleaner.clean(urlTarget);
            }
            // Parse the request origin using registered origin parser.
            String urlOrigin = parseOrigin(ctx.getRequest());
            logger.info("urlTarget:{}, urlOrigin:{}", urlTarget, urlOrigin);
            ContextUtil.enter(urlTarget, urlOrigin);
            entry = SphU.entry(urlTarget, EntryType.IN);
            forwardHttpRequest();
        } catch (BlockException | IOException e1) {
            throw new ZuulRuntimeException(e1);
        } catch (Exception ex) {
            Tracer.trace(ex);
            throw new ZuulRuntimeException(ex);
        } finally {
            if (entry != null) {
                entry.exit();
            }
            ContextUtil.exit();
        }
        return null;
    }

    private void forwardHttpRequest() throws IOException {
        RequestContext context = RequestContext.getCurrentContext();
        HttpServletRequest request = context.getRequest();
        OkHttpClient httpClient = new OkHttpClient.Builder()
                // customize
                .build();

        String method = request.getMethod();

        String uri = this.helper.buildZuulRequestURI(request);
        List<ServiceInstance> instances = discoveryClient.getInstances((String) context.get(SERVICE_ID_KEY));
        String host = "http://" + String.valueOf(instances.get(0).getHost()) + ":" + String.valueOf(instances.get(0).getPort());
        String url = host + uri;
        logger.info("discovery url:{}", url);
        Headers.Builder headers = new Headers.Builder();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            Enumeration<String> values = request.getHeaders(name);

            while (values.hasMoreElements()) {
                String value = values.nextElement();
                headers.add(name, value);
            }
        }
        InputStream inputStream = request.getInputStream();
        RequestBody requestBody = null;
        if (inputStream != null && HttpMethod.permitsRequestBody(method)) {
            MediaType mediaType = null;
            if (headers.get("Content-Type") != null) {
                mediaType = MediaType.parse(headers.get("Content-Type"));
            }
            requestBody = RequestBody.create(mediaType, StreamUtils.copyToByteArray(inputStream));
        }

        Request.Builder builder = new Request.Builder()
                .headers(headers.build())
                .url(url)
                .method(method, requestBody);

        Response response = httpClient.newCall(builder.build()).execute();

        LinkedMultiValueMap<String, String> responseHeaders = new LinkedMultiValueMap<>();

        for (Map.Entry<String, List<String>> entry : response.headers().toMultimap().entrySet()) {
            responseHeaders.put(entry.getKey(), entry.getValue());
        }

        this.helper.setResponse(response.code(), response.body().byteStream(),
                responseHeaders);
        // prevent SimpleHostRoutingFilter from running
        context.setRouteHost(null);
    }


    private String parseOrigin(HttpServletRequest request) {
        RequestOriginParser originParser = WebCallbackManager.getRequestOriginParser();
        String origin = EMPTY_ORIGIN;
        if (originParser != null) {
            origin = originParser.parseOrigin(request);
            if (StringUtil.isEmpty(origin)) {
                return EMPTY_ORIGIN;
            }
        }
        return origin;
    }
}
