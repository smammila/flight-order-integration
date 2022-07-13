package com.astralbrands.flight.x3.service;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.astralbrands.flight.x3.controller.X3Controller;
import com.astralbrands.flight.x3.exception.X3IntegrationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

@Component
public class AwsLambdaService {
	private static final Logger logger = LoggerFactory.getLogger(X3Controller.class);

	@Value("${api.gateway.url}")
	private String gatewayUrl;

	public Object integrateToX3(String file) {
		if (file == null) {
			logger.info("File is empty");
			return null;
		}
		try {
			// prep request
			ObjectMapper mapper = new ObjectMapper();
			SimpleModule module = new SimpleModule();
			HttpClient client = HttpClient.newHttpClient();
			HttpRequest request = HttpRequest.newBuilder().uri(URI.create(gatewayUrl))
					.POST(HttpRequest.BodyPublishers.ofString("")).build();
			// POST
			boolean recievedByLambda = false;
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			logger.info("response from lambda function : "+response);
			// Evaluate response
			if (response.body().contains("200")) {
				logger.info("Recieved by Lambda");
				logger.info(response.body());
				recievedByLambda = true;
				return response.body();
			} else {
				throw new X3IntegrationException("Exception while invoking lambda ");
			}
		} catch (MalformedURLException e) {
			e.printStackTrace();
			throw new X3IntegrationException("Could not post flight orders due to malformed url");
		} catch (IOException e) {
			e.printStackTrace();
			throw new X3IntegrationException("Could not post flight orders due to IO Exception");
		} catch (InterruptedException e) {
			e.printStackTrace();
			throw new X3IntegrationException("Could not post flight orders due to InterruptedException");
		}
	}

}
