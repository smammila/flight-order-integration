package com.astralbrands.flight.x3.service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

public abstract class RestApiCallService {

	@Autowired
	private RestTemplate restTemplate;

	public <T> T call(String url, HttpMethod method, Object request, Class<T> response, HttpHeaders headers) {
		HttpEntity<Object> requestEntity = new HttpEntity<>(request, headers);
		ResponseEntity<T> result = restTemplate.exchange(url, method, requestEntity, response);
		return result.getBody();
	}

	public <T> T call(String url, HttpMethod method, Object request, Class<T> response, HttpHeaders headers,
			Map<String, String> params) {
		HttpEntity<Object> requestEntity = new HttpEntity<>(request, headers);
		ResponseEntity<T> result = restTemplate.exchange(url, method, requestEntity, response, params);
		return result.getBody();
	}

}
