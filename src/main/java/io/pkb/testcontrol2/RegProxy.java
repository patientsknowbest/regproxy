package io.pkb.testcontrol2;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.apache.camel.builder.FlexibleAggregationStrategy;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.netty.http.NettyHttpComponent;
import org.apache.camel.component.netty.http.NettyHttpMessage;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Registering reverse proxy
 * 
 * HTTP requests to this server will be forwarded to _all_ upstreams in parallel and expected to succeed.
 * Server provides an endpoint for upstreams to register themselves with a JSON payload like
 * 
 * {"name": "appname", "callback": "http://somehost:1234"}
 * 
 * name is expected to be unique, registering the same name will replace the existing callback.
 * 
 * Only the last response is returned to the client. Nothing special is done to aggregate or compare responses.
 */
public class RegProxy {
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private Map<String, String> applicationCallbacks = new HashMap<>();
    
    public String register(Startup startup) throws IOException {
        var oldCB = applicationCallbacks.put(startup.name(), startup.callback());
        if (oldCB != null) {
            logger.info("replaced {} with {}", oldCB, startup);
        } else {
            logger.info("registered {}", startup);
        }
        return "";
    }
    
    public List<String> getRecipientList() {
        var httpComponentUrl = System.getProperty("http.component.uri", "netty-http");
        return applicationCallbacks.values().stream().map(it -> httpComponentUrl + ":" + it).collect(Collectors.toList());
    }
    public int getRecipientCount() {
        return applicationCallbacks.size();
    }

    void processResponse(Exchange exchange) {
        NettyHttpMessage message = exchange.getIn(NettyHttpMessage.class);
        var request = message.getHttpRequest();
        var response = new DefaultFullHttpResponse(request.protocolVersion(), HttpResponseStatus.valueOf(message.getHeader("CamelHttpResponseCode", Integer.class)));
        response.content().writeBytes(message.getBody(byte[].class));
        message.getHeaders().forEach((k, v) -> response.headers().add(k, v));
        message.setHttpResponse(response);
    }
    
    public static void main(String[] args) throws Exception {
        var options = new Options();
        options.addOption("h", "host", true, "The host to bind to, default \"0.0.0.0\"");
        options.addOption("p", "reg-port", true, "The port to bind to, default \"9876\"");
        options.addOption("c", "connect-timeout", true, "The connection timeout in milliseconds for upstreams, default 1000");
        options.addOption("r", "read-timeout", true, "The read timeout in milliseconds for upstreams, default 40,000");
        var parser = new DefaultParser();
        CommandLine commandLine;
        try {
            commandLine = parser.parse(options, args);
        } catch (ParseException ignored) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( "regproxy", options );
            return;
        }
        
        var host = commandLine.getOptionValue("h", "0.0.0.0");
        var port = commandLine.getOptionValue("p", "9876");
        
        var connectTimeout = Integer.parseInt(commandLine.getOptionValue("c", "1000"));
        var requestTimeout = Integer.parseInt(commandLine.getOptionValue("r", "40000"));
        var httpComponentUrl = "netty-http"; // not a configuration option yet
        var rrp = new RegProxy();
        var ctx = new DefaultCamelContext();
        ctx.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // Enable Jackson
                getContext().getGlobalOptions().put("CamelJacksonEnableTypeConverter", "true");
                getContext().getGlobalOptions().put("CamelJacksonTypeConverterToPojo", "true");

                // Configure netty
                var nettyComponent = (NettyHttpComponent)getContext().getComponent("netty-http");
                var nettyConfig = nettyComponent.getConfiguration();
                nettyConfig.setConnectTimeout(connectTimeout);
                nettyConfig.setRequestTimeout(requestTimeout);
                
                // Endpoint for other applications to register themselves with us
                // Maybe replace this with a proper service discovery in future
                restConfiguration()
                    .component(httpComponentUrl)
                    .host(host).port(port)
                    .bindingMode(RestBindingMode.json);
                rest()
                    .put("register")
                    .route()  
                    .bean(rrp, "register")
                    .endRest();
                
                // HTTP Proxy all requests to all upstreams, fail if any fail
                from(String.format("%s:%s:%s?exchangePattern=InOut&matchOnUriPrefix=true", httpComponentUrl, host, port))
                    .recipientList(method(rrp, "getRecipientList"))
                    .parallelProcessing()
                    .stopOnException()
                    .process(rrp::processResponse);
            }
        });
        ctx.start();
    }
}
