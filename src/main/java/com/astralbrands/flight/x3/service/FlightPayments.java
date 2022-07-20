package com.astralbrands.flight.x3.service;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import com.astralbrands.flight.x3.model.OrderDetails;
import com.astralbrands.flight.x3.model.OrderShipmentID;
import com.astralbrands.flight.x3.model.SearchCriteriaResponse;
import com.astralbrands.flight.x3.model.SearchCriteriaResult;
import com.astralbrands.flight.x3.util.ApiUtil;
import com.astralbrands.flight.x3.util.AppConstants;

@Service
public class FlightPayments extends RestApiCallService implements AppConstants{
	
	private static final Map<String, String> SOLD_TO_MAP = new HashMap<>(5);
	private SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd");
	static {
		SOLD_TO_MAP.put("US Direct to Corporate", "49999988");
		SOLD_TO_MAP.put("US Corp Home Office Warehouse", "49999999");
		SOLD_TO_MAP.put("Corp Warehouse CAD", "49999997");
	}
	
	@Autowired
	private AccessTokenService restApiCallService;
	
	@Value("${flight.search.criteria.url}")
	private String searchCriteriaUrl;
	
	@Value("${flight.search.criteria.values}")
	private String searchValues;
	
	@Value("${flight.getOrderShipmentsUrl}")
	private String orderShipmentsUrl;
	
	@Value("${flight.orders.detailsUrl}")
	private String orderDetailsUrl;
	
	public String getPaymentsFromFlight() {
		
		Instant start = Instant.now();
		
		Map<String, String> orderDisplayIds = new HashMap<>();
		Map<String, OrderDetails> orderDetails = new HashMap<>();
		StringBuilder sb = new StringBuilder(); // search
		String wareHouseName="";
		try {
			String accessToken = restApiCallService.getAccessToken();
			Map<Integer, String> runIds = searchWareHouseId(accessToken); // To store all the
			// available runIds and Warehouse names
			
			for (Map.Entry<Integer, String> entry : runIds.entrySet()) {
				//getExportData(entry.getKey(), entry.getValue(), accessToken, exportOrders);
				orderDisplayIds.putAll(getOrderShipmentIds(entry.getKey(),accessToken,sb));
			}
			orderDetails.putAll(processDisplayIds(orderDisplayIds, accessToken, sb));
//			for (Map.Entry<String, OrderDetails> entry : orderDetails.entrySet()) {
//				System.out.println("Order details Map key values are :" + entry.getKey());
//			}
			
			for (Map.Entry<String, OrderDetails> entry : orderDetails.entrySet()) {
				System.out.println("Order details for : "+entry.getValue().getDisplayID());
				if(!entry.getValue().getOrderPayments().isEmpty()) {
					if(entry.getValue().getOrderPayments().get(0).getAmount()>0) {
					wareHouseName=entry.getValue().getOrderLines().get(0).getWarehouseName();
					sb.append(getHeader(entry.getValue(), wareHouseName));
					sb.append(NEW_LINE);
					sb.append(getOrderLine(entry.getValue(), wareHouseName));
					sb.append(NEW_LINE);
				}
				}
			}
			Instant finish = Instant.now();
			long timeElapsed = Duration.between(start, finish).toSeconds();
			// runIds = null;
			System.out.println(" Total api execution times in Seconds :" + timeElapsed);
		} catch (Exception e) {
			System.err.println("Exception while calling service " + e.getMessage());
			e.printStackTrace();
		}
		System.out.println("Final Payment import file : "+sb.toString());
		System.out.println("Import process is done");
		return null;
	}
	
	private String getOrderLine(OrderDetails orderDetails, String wareHouseName) {
		System.out.println("ware house name is : "+ wareHouseName);
		boolean isUsSite = isUSSite(wareHouseName);
		StringJoiner sj = new StringJoiner(IFILE_DATA_SEPERATOR);
		sj.add("D");
		sj.add("PAYRC");
		sj.add("AST");
		sj.add("11000");
		sj.add(SOLD_TO_MAP.get(wareHouseName));
		sj.add("CCCLI");
		sj.add(orderDetails.getDisplayID());
		sj.add(EMPTRY_STR);
		sj.add(SOLD_TO_MAP.get(wareHouseName));
		sj.add(getDateInYYYYMMDD(orderDetails.getOrderPayments().get(0).getPaymentDate()));
		sj.add(getSalesSite(isUsSite));
		sj.add(getCurrency(isUsSite));
		sj.add(String.valueOf(orderDetails.getOrderPayments().get(0).getAmount()));
		sj.add("NT");
		sj.add("0");
		sj.add(EMPTRY_STR);
		
		return sj.toString();
	}

	private String getHeader(OrderDetails orderDetails, String wareHouseName) {
		System.out.println("ware house name is : "+ wareHouseName);
		boolean isUsSite = isUSSite(wareHouseName);
		StringJoiner sj = new StringJoiner(IFILE_DATA_SEPERATOR);
		sj.add("P");
		sj.add(EMPTRY_STR);
		sj.add(orderDetails.getTransactions().get(0).getTransactionID());
		sj.add("XX1XO");
		sj.add(EMPTRY_STR);
		sj.add(SOLD_TO_MAP.get(wareHouseName));
		sj.add("AST");
		sj.add("11000");
		sj.add(EMPTRY_STR);
		sj.add(EMPTRY_STR);
		sj.add(getSalesSite(isUsSite));
		sj.add("2"); // need to confirm this one
		sj.add(getCurrency(isUsSite));
		sj.add(String.valueOf(orderDetails.getOrderPayments().get(0).getAmount()));
		sj.add(getDateInYYYYMMDD(orderDetails.getOrderPayments().get(0).getPaymentDate()));
		
		return sj.toString();
	}

	public OrderDetails orderDetails(String orderDisplayId, String accessToken) {

		String urlTemplate = UriComponentsBuilder.fromHttpUrl(orderDetailsUrl)
				.queryParam(ORDER_DISPLAY_ID, "{orderDisplayId}").encode().toUriString();
		Map<String, String> params = new HashMap<>();
		params.put(ORDER_DISPLAY_ID, orderDisplayId);
		OrderDetails orderDetails = call(urlTemplate, HttpMethod.GET, null, OrderDetails.class, getHeader(accessToken),
				params);
		params = null;
		System.out.println("Received order details for display Id : " + orderDetails.getDisplayID()+" with order lines of size :"+orderDetails.getOrderLines().size());
		
		return orderDetails;
		
	}


	public Map<Integer, String> searchWareHouseId(String accessToken) {
		Map<Integer, String> runIds = new HashMap<>();
		SearchCriteriaResponse criteriaResult = call(searchCriteriaUrl, HttpMethod.POST,
				ApiUtil.getWareHouseIdSearchReq(), SearchCriteriaResponse.class, getHeader(accessToken));

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
	
	public Map<String, String> getOrderShipmentIds(Integer runId, String accessToken, StringBuilder sb) {

		System.out.println("Getting Shipments for run Id: "+runId);
		Map<String, String> orderDisplayIds = new HashMap<>();
		try {
			String urlTemplate = UriComponentsBuilder.fromHttpUrl(orderShipmentsUrl)
					.queryParam(SHIPPING_RUN_ID, "{shippingFileRunId}").encode().toUriString();
			Map<String, String> params = new HashMap<>();
			params.put(SHIPPING_RUN_ID, String.valueOf(runId));
			OrderShipmentID[] response = call(urlTemplate, HttpMethod.GET, null, OrderShipmentID[].class,
					getHeader(accessToken), params);
			params = null;
			if (response != null) {
				return Stream.of(response).collect(
						Collectors.toMap(OrderShipmentID::getOrderDisplayID, OrderShipmentID::getShipmentDisplayID));
			}
			System.out.println("Map is : "+ Map.of());
		} catch (Exception ex) {
			sb.append(runId).append(IFILE_DATA_SEPERATOR);
		}
		System.out.println("Shipments and display Id's map is : "+Map.of());
		return Map.of();
	}
	
	private Map<String, OrderDetails> processDisplayIds(Map<String, String> displayIds, String tokenId,
			StringBuilder sb) {
		Map<String, OrderDetails> displayIdOrders = new HashMap<>();
		for (Entry<String, String> displayId : displayIds.entrySet()) {
			if (displayId.getKey() != null) {
				try {
					System.out.println("Getting into order details for display Id : "+displayId.getKey());
					displayIdOrders.put(displayId.getValue(), orderDetails(displayId.getKey(), tokenId));
				} catch (Exception ex) {
					sb.append(displayId).append(IFILE_DATA_SEPERATOR);
				}
			}
		}
		System.out.println("Processed map display Id's : "+displayIdOrders.keySet());
		return displayIdOrders;
	}
	
	private boolean isUSSite(String wareHouseName) {
		return wareHouseName.contains(US);
	}
	
	private String getSalesSite(boolean isUsSite) {
		if (isUsSite) {
			return "ALOUS";
		} else {
			return "ALCCA";
		}
	}
	
	private String getCurrency(boolean isUsSite) {
		if (isUsSite) {
			return "USD";
		} else {
			return "CAD";
		}
	}
	
	public String getDateInYYYYMMDD(Date date) {
		return inputFormat.format(date);
	}

}
