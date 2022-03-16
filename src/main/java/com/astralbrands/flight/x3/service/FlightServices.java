package com.astralbrands.flight.x3.service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import com.astralbrands.flight.x3.model.OrderDetails;
import com.astralbrands.flight.x3.model.OrderLine;
import com.astralbrands.flight.x3.model.SearchCriteriaResponse;
import com.astralbrands.flight.x3.model.SearchCriteriaResult;
import com.astralbrands.flight.x3.util.ApiUtil;
import com.astralbrands.flight.x3.util.AppConstants;

@Service
public class FlightServices extends RestApiCallService implements AppConstants {

	/*
	 * @Autowired private RestTemplate restTemplate;
	 */

	@Autowired
	private AccessTokenService restApiCallService;

	@Value("${flight.getDisplayIdUrl}")
	private String getDisplayIdUrl;

	@Value("${flight.search.criteria.url}")
	private String searchCriteriaUrl;

	@Value("${flight.search.criteria.values}")
	private String searchValues;

	@Value("${flight.orders.detailsUrl}")
	private String orderDetailsUrl;

	public Map<String, OrderDetails> getOrderDetails() {
		Instant start = Instant.now();
		List<String> displayIds = new LinkedList<>();
		Map<String, OrderDetails> runIdOrders = new HashMap<>();
		// To get the access token by invoking an API
		String accessToken = restApiCallService.getAccessToken();
		Map<Integer, String> runIds = search(accessToken); // To store all the available runIds based on filtered search
		for (Map.Entry<Integer, String> entry : runIds.entrySet()) {
			displayIds.add(getOrderDisplayIds(entry.getKey(), accessToken));
		}
		runIdOrders.putAll(processDisplayIds(displayIds, accessToken));
		System.out.println(" Total order details :" + runIdOrders.size());
		Instant finish = Instant.now();
		long timeElapsed = Duration.between(start, finish).toSeconds();
		System.out.println(" Total api execution times in Seconds :" + timeElapsed);
		displayIds = null;
		runIds = null;
		return runIdOrders;

	}

	// To get the complete details of an orderDisplay ID
	private Map<String, OrderDetails> processDisplayIds(List<String> displayIds, String tokenId) {
		Map<String, OrderDetails> displayIdOrders = new HashMap<>();
		for (String displayId : displayIds) {
			String[] rows = displayId.split("\r\n");
			for (int i = 1; i < rows.length; i++) {
				String disId = extractDisplayId(rows[i]);
				if (disId != null) {
					displayIdOrders.put(disId, orderDetails(disId, tokenId));
				}
			}
		}
		return displayIdOrders;
	}

	// To get runIds for Bussiness Units: Aloette Corporate and US Direct to Deposit
	// from Flight
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

	// To get all the orderDisplay Id's associated with runId
	public String getOrderDisplayIds(Integer runId, String accessToken) {

		String dataExportFilterRequest = ApiUtil.getDataExportFilterRequest(runId);
		String response = call(getDisplayIdUrl, HttpMethod.POST, dataExportFilterRequest, String.class,
				getHeader(accessToken));
		return response;
	}

	public OrderDetails orderDetails(String orderDisplayId, String accessToken) {

		String urlTemplate = UriComponentsBuilder.fromHttpUrl(orderDetailsUrl)
				.queryParam(ORDER_DISPLAY_ID, "{orderDisplayId}").encode().toUriString();
		Map<String, String> params = new HashMap<>();
		params.put(ORDER_DISPLAY_ID, orderDisplayId);
		OrderDetails orderDetails = call(urlTemplate, HttpMethod.GET, null, OrderDetails.class, getHeader(accessToken),
				params);
		updateSkuId(orderDetails);
		return orderDetails;
	}

	private void updateSkuId(OrderDetails orderDetails) {
		if (orderDetails != null && orderDetails.getOrderLines() != null) {
			List<OrderLine> orderLine = orderDetails.getOrderLines();
			int lineLen = orderLine.size();
			for (int i = 0; i < lineLen; i++) {
				OrderLine line = orderLine.get(i);
				if (line.getSKU() != null && checkSKU(line.getSKU())) {
					line.setSKU("09739");
				}
				if (line.isBuildable()) {
					double price = line.getPrice();
					int count = 0;
					for (int j = i + 1; j < lineLen; j++) {
						if (orderLine.get(j).getBuildableKitOrderLineID() != null && line.getOrderLineID() == Integer
								.parseInt(orderLine.get(j).getBuildableKitOrderLineID().toString())) {
							count++;
						}
					}
					double distPrice = price / count;
					for (int j = i + 1; j < lineLen; j++) {
						if (orderLine.get(j).getBuildableKitOrderLineID() != null && line.getOrderLineID() == Integer
								.parseInt(orderLine.get(j).getBuildableKitOrderLineID().toString())) {
							orderLine.get(j).setPrice(distPrice);
						}
					}
				}
			}
		}
	}

	private boolean checkSKU(String skuId) {
		return (skuId.startsWith(EIGHT) && skuId.contains(BGO)) || !skuId.startsWith(EIGHT);
	}

	private HttpHeaders getHeader(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}

	private String extractDisplayId(String row) {
		String[] values = row.split(",");
		if (values.length > 2) {
			return values[2];
		}
		return null;
	}

}
