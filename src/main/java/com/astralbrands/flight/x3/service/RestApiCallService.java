package com.astralbrands.flight.x3.service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

public abstract class RestApiCallService {

	@Autowired
	private RestTemplate restTemplate;

	public <T> T call(String url, HttpMethod method, Object request, Class<T> response, HttpHeaders headers) throws RestClientException {
		HttpEntity<Object> requestEntity = new HttpEntity<>(request, headers);
		System.out.println("input"+requestEntity);
		ResponseEntity<T> result = restTemplate.exchange(url, method, requestEntity, response);
		System.out.println("Response from API: "+result);
		return result.getBody();
	}

	public <T> T call(String url, HttpMethod method, Object request, Class<T> response, HttpHeaders headers,
			Map<String, String> params) throws RestClientException {
		HttpEntity<Object> requestEntity = new HttpEntity<>(request, headers);
		ResponseEntity<T> result = restTemplate.exchange(url, method, requestEntity, response, params);
		return result.getBody();
	}

}
