package com.example.demogateway.filter;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.factory.rewrite.CachedBodyOutputMessage;
import org.springframework.cloud.gateway.support.BodyInserterContext;
import org.springframework.cloud.gateway.support.DefaultServerRequest;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;

import com.alibaba.fastjson.JSON;
import com.example.demogateway.response.ResponseCodeEnum;
import com.example.demogateway.response.ResponseResult;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 废弃本类
 * @author moyanxia
 *
 */
//@Component
@Slf4j
public class AuthorizeFilter implements GlobalFilter, Ordered {
	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		ServerHttpRequest serverHttpRequest = exchange.getRequest();
		ServerHttpResponse serverHttpResponse = exchange.getResponse();

		ServerRequest serverRequest = new DefaultServerRequest(exchange); // mediaType
		MediaType mediaType = exchange.getRequest().getHeaders().getContentType();
		Map mapCheckInfo = new HashMap<>();
		// read & modify body
		Mono<String> modifiedBody = serverRequest.bodyToMono(String.class).flatMap(body -> {
			if (MediaType.APPLICATION_FORM_URLENCODED.isCompatibleWith(mediaType)) {
				// origin body map
				Map<String, Object> bodyMap = decodeBody(body);
				// TODO decrypt & auth
				log.info("bodyMap:" + bodyMap);
				// new body map
				Map<String, Object> newBodyMap = new HashMap<>();
				return Mono.just(encodeBody(newBodyMap));
			} else if (MediaType.APPLICATION_JSON.isCompatibleWith(mediaType)) {
				// origin body map
				Map bodyMap = JSON.parseObject(body, Map.class);
				// TODO decrypt & auth
				log.info("bodyMap:" + bodyMap);
				bodyMap.put("newKey", "newValue");
				//exchange.getAttributes().put("cachedRequestBody", body);
//				if (!checkSign(bodyMap)) {
//					mapCheckInfo.put("header", serverHttpRequest.getHeaders());
//					mapCheckInfo.put("md5CheckResult", "0");
//					mapCheckInfo.put("bodyStr", JSON.toJSONString(bodyMap));
//				}
				// new body map
				return Mono.just(body);
			}
			return Mono.empty();
		});
		if ("0".equals(mapCheckInfo.get("md5CheckResult"))) {
			serverHttpResponse.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
			ResponseResult responseResult = ResponseResult.error(400, mapCheckInfo.toString());
			DataBuffer dataBuffer = serverHttpResponse.bufferFactory()
					.wrap(JSON.toJSONString(responseResult).getBytes());
			return serverHttpResponse.writeWith(Flux.just(dataBuffer));
		}

		BodyInserter bodyInserter = BodyInserters.fromPublisher(modifiedBody, String.class);
		HttpHeaders headers = new HttpHeaders();
		headers.putAll(exchange.getRequest().getHeaders());
		// the new content type will be computed by bodyInserter
		// and then set in the request decorator
		headers.remove(HttpHeaders.CONTENT_LENGTH);
		CachedBodyOutputMessage outputMessage = new CachedBodyOutputMessage(exchange, headers);
		return bodyInserter.insert(outputMessage, new BodyInserterContext()).then(Mono.defer(() -> {
			ServerHttpRequestDecorator decorator = new ServerHttpRequestDecorator(exchange.getRequest()) {
				@Override
				public HttpHeaders getHeaders() {
					long contentLength = headers.getContentLength();
					HttpHeaders httpHeaders = new HttpHeaders();
					httpHeaders.putAll(super.getHeaders());
					if (contentLength > 0) {
						httpHeaders.setContentLength(contentLength);
					} else {
						httpHeaders.set(HttpHeaders.TRANSFER_ENCODING, "chunked");
					}
					return httpHeaders;
				}

				@Override
				public Flux<DataBuffer> getBody() {
					return outputMessage.getBody();
				}
			};
			return chain.filter(exchange.mutate().request(decorator).build());
		}));
	}

	private Mono<Void> getVoidMono(ServerHttpResponse serverHttpResponse, ResponseCodeEnum responseCodeEnum) {
		serverHttpResponse.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
		ResponseResult responseResult = ResponseResult.error(responseCodeEnum.getCode(), responseCodeEnum.getMessage());
		DataBuffer dataBuffer = serverHttpResponse.bufferFactory().wrap(JSON.toJSONString(responseResult).getBytes());
		return serverHttpResponse.writeWith(Flux.just(dataBuffer));
	}

	/**
	 * 校验签名
	 * 
	 * @param bodyMap
	 * @return
	 */
	private boolean checkSign(Map bodyMap) {
		if ("qhtest123".equals(bodyMap.get("name"))) {
			return true;
		}
		return false;
	}

	@Override
	public int getOrder() {
		return -50;
	}

	private Map<String, Object> decodeBody(String body) {
		return Arrays.stream(body.split("&")).map(s -> s.split("="))
				.collect(Collectors.toMap(arr -> arr[0], arr -> arr[1]));
	}

	private String encodeBody(Map<String, Object> map) {
		return map.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("&"));
	}
}