package com.astralbrands.flight.x3.service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import com.astralbrands.flight.x3.model.OrderDetails;
import com.astralbrands.flight.x3.model.SearchCriteriaResponse;
import com.astralbrands.flight.x3.model.SearchCriteriaResult;
import com.astralbrands.flight.x3.util.ApiUtil;
import com.astralbrands.flight.x3.util.AppConstants;

@Service
public class FlightPayments extends RestApiCallService implements AppConstants{
	
	@Autowired
	private AccessTokenService restApiCallService;
	
	@Value("${flight.search.criteria.url}")
	private String searchCriteriaUrl;
	
	@Value("${flight.search.criteria.values}")
	private String searchValues;
	
	public String getPaymentsFromFlight() {
		
		Instant start = Instant.now();
		
		Map<String, OrderDetails> runIdOrders = new HashMap<>();
		StringBuilder sb = new StringBuilder(); // search
		try {
			String accessToken = restApiCallService.getAccessToken();
			//getExportData(35205, "US Direct to Corporate", accessToken, exportOrders);
			Map<Integer, String> runIds = search(accessToken); // To store all the
			// available runIds and Warehouse names

			for (Map.Entry<Integer, String> entry : runIds.entrySet()) {
				getExportData(entry.getKey(), entry.getValue(), accessToken, exportOrders);
			}
			Instant finish = Instant.now();
			long timeElapsed = Duration.between(start, finish).toSeconds();
			// runIds = null;
			System.out.println(" Total api execution times in Seconds :" + timeElapsed);
		} catch (Exception e) {
			System.err.println("Exception while calling service " + e.getMessage());
			e.printStackTrace();
		}
		return null;
	}
	
	public Map<Integer, String> search(String accessToken) {
		Map<Integer, String> runIds = new HashMap<>();
		String searchCriteriaRequest = ApiUtil.getSearchCriteriaRequest(searchValues);
		SearchCriteriaResponse criteriaResult = call(searchCriteriaUrl, HttpMethod.POST, searchCriteriaRequest,
				SearchCriteriaResponse.class, getHeader(accessToken));

		if (criteriaResult != null && criteriaResult.getResults() != null) {
			for (SearchCriteriaResult res : criteriaResult.getResults()) {
				if (res.getOrderCount() > 0) {
					runIds.put(res.getShippingFileRunID(), res.getWarehouses());
				}
			}
		}
		System.out.println(" run Ids :" + runIds);
		return runIds;
	}
	
	private HttpHeaders getHeader(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}

}
