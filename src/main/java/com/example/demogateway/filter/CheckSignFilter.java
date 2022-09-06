package com.example.demogateway.filter;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.apache.commons.lang3.StringUtils;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.factory.rewrite.CachedBodyOutputMessage;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.BodyInserterContext;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;

import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.common.utils.MD5Utils;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class CheckSignFilter implements GlobalFilter, Ordered {

	private final List<HttpMessageReader<?>> messageReaders = HandlerStrategies.withDefaults().messageReaders();

	@Override
	public int getOrder() {
		return -50;
	}

	@Override
	@SuppressWarnings("unchecked")
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		ServerHttpRequest serverHttpRequest = exchange.getRequest();
		ServerHttpResponse serverHttpResponse = exchange.getResponse();

		StringBuilder logBuilder = new StringBuilder();
		String method = serverHttpRequest.getMethodValue().toUpperCase();
		if ("POST".equals(method)) {
			Map<String, Object> params = parseRequest(exchange, logBuilder);
			boolean r = checkSignature(params, serverHttpRequest);
			if (!r) {
				Map map = new HashMap<>();
				map.put("code", 2);
				map.put("message", "签名验证失败");
				String resp = JSON.toJSONString(map);
				logBuilder.append(",resp=").append(resp);
				log.info(logBuilder.toString());
				DataBuffer bodyDataBuffer = serverHttpResponse.bufferFactory().wrap(resp.getBytes());
				serverHttpResponse.getHeaders().add("Content-Type", "text/plain;charset=UTF-8");
				return serverHttpResponse.writeWith(Mono.just(bodyDataBuffer));
			}
		}

		MediaType mediaType = serverHttpRequest.getHeaders().getContentType();

		if (MediaType.APPLICATION_FORM_URLENCODED.isCompatibleWith(mediaType)
				|| MediaType.APPLICATION_JSON.isCompatibleWith(mediaType)) {
			return writeBodyLog(exchange, chain);
		} else {
			return writeBasicLog(exchange, chain);
		}
	}

	private Map<String, Object> parseRequest(ServerWebExchange exchange, StringBuilder logBuilder) {
		ServerHttpRequest serverHttpRequest = exchange.getRequest();
		String method = serverHttpRequest.getMethodValue().toUpperCase();
		logBuilder.append(method).append(",").append(serverHttpRequest.getURI());
		MultiValueMap<String, String> query = serverHttpRequest.getQueryParams();
		Map<String, Object> params = new HashMap<>();
		query.forEach((k, v) -> {
			params.put(k, v.get(0));
		});
		if ("POST".equals(method)) {
			String body = exchange.getAttributeOrDefault("cachedRequestBody", "");
			Map bodyMap = JSON.parseObject(body, Map.class);
			return bodyMap;
		} else if ("GET".equals(method)) {
			Map queryParam = exchange.getRequest().getQueryParams();
		}
		return params;
	}

	private boolean checkSignature(Map<String, Object> params, ServerHttpRequest serverHttpRequest) {

		String sign = String.valueOf(params.get("sign"));
		if (StringUtils.isBlank(sign)) {
			return false;
		}

		Map dataMap = (Map) params.get("data");
		// 检查签名
		Map<String, Object> sorted = new TreeMap<>();
		params.forEach((k, v) -> {
			if (!"sign".equals(k)) {
				sorted.put(k, v);
			}
		});
		StringBuilder builder = new StringBuilder();
		sorted.forEach((k, v) -> {
			builder.append(k).append("=").append(v).append("&");
		});
		String value = builder.toString();
		value = value.substring(0, value.length() - 1);
		if (!sign.equalsIgnoreCase(MD5Utils.md5Hex(value, "UTF-8"))) {
			return false;
		}

		return true;
	}

	private Mono<Void> writeBasicLog(ServerWebExchange exchange, GatewayFilterChain chain) {
		// 获取响应体
		ServerHttpResponseDecorator decoratedResponse = recordResponseLog(exchange);

		return chain.filter(exchange.mutate().response(decoratedResponse).build()).then(Mono.fromRunnable(() -> {
			// 打印日志
//			writeAccessLog(accessLog);
		}));
	}

	/**
	 * 解决 request body 只能读取一次问题， 参考:
	 * org.springframework.cloud.gateway.filter.factory.rewrite.ModifyRequestBodyGatewayFilterFactory
	 * 
	 * @param exchange
	 * @param chain
	 * @param gatewayLog
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private Mono writeBodyLog(ServerWebExchange exchange, GatewayFilterChain chain) {
		ServerRequest serverRequest = ServerRequest.create(exchange, messageReaders);

		Mono<String> modifiedBody = serverRequest.bodyToMono(String.class).flatMap(body -> {
			// exchange.getAttributes().put("cachedRequestBody", body);
			return Mono.just(body);
		});

		// 通过 BodyInserter 插入 body(支持修改body), 避免 request body 只能获取一次
		BodyInserter bodyInserter = BodyInserters.fromPublisher(modifiedBody, String.class);
		HttpHeaders headers = new HttpHeaders();
		headers.putAll(exchange.getRequest().getHeaders());
		// the new content type will be computed by bodyInserter
		// and then set in the request decorator
		headers.remove(HttpHeaders.CONTENT_LENGTH);

		CachedBodyOutputMessage outputMessage = new CachedBodyOutputMessage(exchange, headers);

		return bodyInserter.insert(outputMessage, new BodyInserterContext()).then(Mono.defer(() -> {
			// 重新封装请求
			ServerHttpRequest decoratedRequest = requestDecorate(exchange, headers, outputMessage);

			// 记录响应日志
			ServerHttpResponseDecorator decoratedResponse = recordResponseLog(exchange);

			// 记录普通的
			return chain.filter(exchange.mutate().request(decoratedRequest).response(decoratedResponse).build())
					.then(Mono.fromRunnable(() -> {
						// 打印日志
//						writeAccessLog(gatewayLog);
					}));
		}));
	}

//	/**
//	 * 打印日志
//	 * 
//	 * @author javadaily
//	 * @date 2021/3/24 14:53
//	 * @param gatewayLog 网关日志
//	 */
//	private void writeAccessLog(GatewayLog gatewayLog) {
//		log.info(gatewayLog.toString());
//	}

	private Route getGatewayRoute(ServerWebExchange exchange) {
		return exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
	}

	/**
	 * 请求装饰器，重新计算 headers
	 * 
	 * @param exchange
	 * @param headers
	 * @param outputMessage
	 * @return
	 */
	private ServerHttpRequestDecorator requestDecorate(ServerWebExchange exchange, HttpHeaders headers,
			CachedBodyOutputMessage outputMessage) {
		return new ServerHttpRequestDecorator(exchange.getRequest()) {
			@Override
			public HttpHeaders getHeaders() {
				long contentLength = headers.getContentLength();
				HttpHeaders httpHeaders = new HttpHeaders();
				httpHeaders.putAll(super.getHeaders());
				if (contentLength > 0) {
					httpHeaders.setContentLength(contentLength);
				} else {
					// TODO: this causes a 'HTTP/1.1 411 Length Required' // on
					// httpbin.org
					httpHeaders.set(HttpHeaders.TRANSFER_ENCODING, "chunked");
				}
				return httpHeaders;
			}

			@Override
			public Flux<DataBuffer> getBody() {
				return outputMessage.getBody();
			}
		};
	}

	/**
	 * 记录响应日志 通过 DataBufferFactory 解决响应体分段传输问题。
	 */
	private ServerHttpResponseDecorator recordResponseLog(ServerWebExchange exchange) {
		ServerHttpResponse response = exchange.getResponse();
		DataBufferFactory bufferFactory = response.bufferFactory();

		return new ServerHttpResponseDecorator(response) {
			@Override
			public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
				if (body instanceof Flux) {
					Date responseTime = new Date();
					// 获取响应类型，如果是 json 就打印
					String originalResponseContentType = exchange
							.getAttribute(ServerWebExchangeUtils.ORIGINAL_RESPONSE_CONTENT_TYPE_ATTR);
					if (Objects.equals(this.getStatusCode(), HttpStatus.OK)
							&& StringUtils.isNotBlank(originalResponseContentType)
							&& originalResponseContentType.contains("application/json")) {
						Flux<? extends DataBuffer> fluxBody = Flux.from(body);
						return super.writeWith(fluxBody.buffer().map(dataBuffers -> {
							// 合并多个流集合，解决返回体分段传输
							DataBufferFactory dataBufferFactory = new DefaultDataBufferFactory();
							DataBuffer join = dataBufferFactory.join(dataBuffers);
							byte[] content = new byte[join.readableByteCount()];
							join.read(content);

							// 释放掉内存
							DataBufferUtils.release(join);
							return bufferFactory.wrap(content);
						}));
					}
				}
				// if body is not a flux. never got there.
				return super.writeWith(body);
			}
		};
	}

	@Data
	public class GatewayLog {
		/** 访问实例 */
		private String targetServer;
		/** 请求路径 */
		private String requestPath;
		/** 请求方法 */
		private String requestMethod;
		/** 协议 */
		private String schema;
		/** 请求体 */
		private String requestBody;
		/** 响应体 */
		private String responseData;
		/** 请求ip */
		private String ip;
		/** 请求时间 */
		private Date requestTime;
		/** 响应时间 */
		private Date responseTime;
		/** 执行时间 */
		private long executeTime;
	}
}
