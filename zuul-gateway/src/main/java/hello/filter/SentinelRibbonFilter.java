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
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.alibaba.csp.sentinel.util.StringUtil;
import com.netflix.zuul.context.RequestContext;
import com.netflix.zuul.exception.ZuulException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.netflix.ribbon.support.RibbonRequestCustomizer;
import org.springframework.cloud.netflix.zuul.filters.ProxyRequestHelper;
import org.springframework.cloud.netflix.zuul.filters.route.RibbonCommandContext;
import org.springframework.cloud.netflix.zuul.filters.route.RibbonCommandFactory;
import org.springframework.cloud.netflix.zuul.filters.route.RibbonRoutingFilter;
import org.springframework.cloud.netflix.zuul.util.ZuulRuntimeException;
import org.springframework.http.client.ClientHttpResponse;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

import static org.springframework.cloud.netflix.zuul.filters.support.FilterConstants.SERVICE_ID_KEY;

/**
 * @author tiger
 */
public class SentinelRibbonFilter extends RibbonRoutingFilter {

    private static final String EMPTY_ORIGIN = "";

    private Logger logger = LoggerFactory.getLogger(SentinelRibbonFilter.class);

    public SentinelRibbonFilter(ProxyRequestHelper helper, RibbonCommandFactory<?> ribbonCommandFactory,
                                List<RibbonRequestCustomizer> requestCustomizers) {
        super(helper, ribbonCommandFactory, requestCustomizers);
    }

    public SentinelRibbonFilter(RibbonCommandFactory<?> ribbonCommandFactory) {
        super(ribbonCommandFactory);
    }

    @Override
    public Object run() {
        Entry serviceEntry = null;
        Entry uriEntry = null;
        RequestContext ctx = RequestContext.getCurrentContext();
        this.helper.addIgnoredHeaders();
        try {
            // service target
            String serviceTarget = (String) ctx.get(SERVICE_ID_KEY);
            String serviceOrigin = "origin";
            logger.info("serviceTarget:{} , serviceOrigin:{}", serviceTarget, serviceOrigin);
            ContextUtil.enter(serviceTarget, serviceOrigin);
            serviceEntry = SphU.entry(serviceTarget, EntryType.IN);

            // url target
            String uriTarget = FilterUtil.filterTarget(ctx.getRequest());
            // Clean and unify the URL.
            // For REST APIs, you have to clean the URL (e.g. `/foo/1` and `/foo/2` -> `/foo/:id`), or
            // the amount of context and resources will exceed the threshold.
            UrlCleaner urlCleaner = WebCallbackManager.getUrlCleaner();
            if (urlCleaner != null) {
                uriTarget = urlCleaner.clean(uriTarget);
            }
            // Parse the request origin using registered origin parser.
            String urlOrigin = parseOrigin(ctx.getRequest());
            logger.info("uriTarget:{}, urlOrigin:{}", uriTarget, urlOrigin);
            uriEntry = SphU.entry(uriTarget, EntryType.IN);
            RibbonCommandContext commandContext = buildCommandContext(ctx);
            ClientHttpResponse response = forward(commandContext);
            setResponse(response);
            return response;
        } catch (ZuulException ex) {
            Tracer.trace(ex);
            throw new ZuulRuntimeException(ex);
        } catch (BlockException ex) {
            Tracer.trace(ex);
            // do the logic when flow control happens.
            throw new ZuulRuntimeException(ex);
        } catch (Exception ex) {
            Tracer.trace(ex);
            throw new ZuulRuntimeException(ex);
        } finally {
            if (uriEntry != null) {
                uriEntry.exit();
            }
            if (serviceEntry != null) {
                serviceEntry.exit();
            }

            ContextUtil.exit();
        }
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
