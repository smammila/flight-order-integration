package com.astralbrands.flight.x3.service;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import com.astralbrands.flight.x3.model.OrderExportResponse;
import com.astralbrands.flight.x3.model.OrderLine;
import com.astralbrands.flight.x3.model.OrderShipmentID;
import com.astralbrands.flight.x3.model.SearchCriteriaResponse;
import com.astralbrands.flight.x3.model.SearchCriteriaResult;
import com.astralbrands.flight.x3.util.ApiUtil;
import com.astralbrands.flight.x3.util.AppConstants;
import com.astralbrands.flight.x3.util.FlightOrderLineCache;

@Service
public class FlightServices extends RestApiCallService implements AppConstants {

	/*
	 * @Autowired private RestTemplate restTemplate;
	 */

	private static final Map<String, String> SOLD_TO_MAP = new HashMap<>(5);
	private SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd");

	static {
		SOLD_TO_MAP.put("US Direct to Corporate", "49999988");
		SOLD_TO_MAP.put("US Corp Home Office Warehouse", "49999999");
		SOLD_TO_MAP.put("Corp Warehouse CAD", "49999997");
	}

	@Autowired
	private AccessTokenService restApiCallService;

	@Value("${flight.getDisplayIdUrl}")
	private String getDisplayIdUrl;

	@Value("${flight.search.criteria.url}")
	private String searchCriteriaUrl;

	@Value("${flight.getOrderShipmentsUrl}")
	private String orderShipmentsUrl;

	@Value("${flight.search.criteria.values}")
	private String searchValues;

	@Value("${flight.orders.detailsUrl}")
	private String orderDetailsUrl;

	@Value("${flight.orders.exportDetails}")
	private String exportDetailsUrl;

	@Autowired
	private IFileService iFileService;

	public Map<String, OrderDetails> getOrderDetails() {

		Instant start = Instant.now();
		Map<String, String> displayIds = new HashMap<>();
		Map<String, OrderDetails> runIdOrders = new HashMap<>();
		Map<Integer, String> exportOrders = new HashMap<>();
		// To get the access token by invoking an API
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
		} finally {
			displayIds = null;
			// send mail
		}
		System.out.println(" export order Map :"+exportOrders);
		for (Map.Entry<Integer, String> entry : exportOrders.entrySet()) {
			sb.append(entry.getValue()).append(NEW_LINE);
		}
		System.out.println("i file data :"+sb.toString());
		iFileService.exportFile(sb.toString());
		return runIdOrders;

	}

	private void getExportData(int runId, String wareHouseName, String tokenId, Map<Integer, String> orders) {

		String urlTemplate = UriComponentsBuilder.fromHttpUrl(exportDetailsUrl)
				.queryParam("shippingFileRunID", "{shippingFileRunID}")
				.queryParam("fileExportConfigurationID", "{fileExportConfigurationID}").queryParam("asJSON", "{asJSON}")
				.encode().toUriString();

		Map<String, String> params = new HashMap<>();
		params.put("shippingFileRunID", runId + EMPTRY_STR);
		params.put("fileExportConfigurationID", "7");
		params.put("asJSON", "true");
		StringBuilder iFileData = new StringBuilder();
		OrderExportResponse[] response = call(urlTemplate, HttpMethod.GET, null, OrderExportResponse[].class,
				getHeader(tokenId), params);
		System.out.println("Export API response is : " + response.toString());
		Map<String, List<OrderExportResponse>> orderNumberMap = Stream.of(response)
				.collect(Collectors.groupingBy(OrderExportResponse::getOrderNumber));
		System.out.println("Map is : " + orderNumberMap);
		for (Map.Entry<String, List<OrderExportResponse>> entry : orderNumberMap.entrySet()) {
			StringBuilder lines = new StringBuilder();
			// double taxPrice = getOrderLines(entry.getValue(), lines);
			System.out.println("Executing for order number : " + entry.getKey());
			Map<String, String> taxOrders = getOrderLines(entry.getValue());
			//System.out.println("Order lines are : " + taxOrders.get("orderLine"));
			String header = getHeader(entry.getValue().get(0), wareHouseName);
			header = header + IFILE_DATA_SEPERATOR + taxOrders.get("tax");
			iFileData.append(header).append(IFILE_DATA_SEPERATOR).append(getDeliveryMode(wareHouseName)).append(IFILE_DATA_SEPERATOR)
					.append(getCarrier(wareHouseName)).append(IFILE_DATA_SEPERATOR).append(getPaymentTerms(entry.getKey(), tokenId)).append(NEW_LINE).append(taxOrders.get("orderLine"));
		}
		System.out.println("Import data is : " + iFileData.toString());
		orders.put(runId, iFileData.toString());
	}

	private String getPaymentTerms(String orderDisplayId, String tokenId) {
		String urlTemplate = UriComponentsBuilder.fromHttpUrl(orderDetailsUrl)
				.queryParam(ORDER_DISPLAY_ID, "{orderDisplayId}").encode().toUriString();
		Map<String, String> params = new HashMap<>();
		params.put(ORDER_DISPLAY_ID, orderDisplayId);
		OrderDetails orderDetails = call(urlTemplate, HttpMethod.GET, null, OrderDetails.class, getHeader(tokenId),
				params);
		params = null;
		String paymentTerms = "";
		
		try {
			if(orderDetails.getTransactions().get(0).getPaymentTypeIdentifier() != null)
				paymentTerms = orderDetails.getTransactions().get(0).getPaymentTypeIdentifier().toString();
		}catch (Exception e) {
			return "WEB";
		}
		if(paymentTerms.equalsIgnoreCase("CC"))
			return "WEB";
		else if(paymentTerms.equalsIgnoreCase("paypal")) {
			return "PAYPAL";
		}
		else
			return paymentTerms;
	}

	private String getCarrier(String wareHouseName) {
		if (isUSSite(wareHouseName)) {
			return "USPS";
		} else {
			return "CPOST";
		}
	}

	private String getDeliveryMode(String wareHouseName) {
		if (isUSSite(wareHouseName)) {
			return "USPSP";
		} else {
			return "CPEPA";
		}
	}

	private Map<String, String> getOrderLines(List<OrderExportResponse> orderExportResponse) {

		double taxPrice = 0;

		StringBuilder sb = new StringBuilder();
		Map<String, String> taxOrders = new HashMap<>();
		// Map<String, Integer> kits = new HashMap<>();
		// int count =0;
		Map<String, Double> kits = getKitsCount(orderExportResponse);
		System.out.println("Number kits and their price :"+kits);

		for (OrderExportResponse order : orderExportResponse) {

			sb.append(getLines(order, kits));
			sb.append(NEW_LINE);
			System.out.println("Order lines for order number are : " + sb.toString());
		}
		System.out.println("Order lines for order number are : " + sb.toString());
		taxOrders.put("tax", String.valueOf(taxPrice));
		taxOrders.put("orderLine", sb.toString());
		return taxOrders;
	}

	private Map<String, Double> getKitsCount(List<OrderExportResponse> orderExportResponse) {

		Map<String, Integer> kits = new HashMap<>();
		Map<String, Double> childPriceMap = new HashMap<>();
		for (OrderExportResponse res : orderExportResponse) {
			if (res.getKitBuildableParentSKU() == null) {
				continue;
			}
			Integer count = kits.get(res.getKitBuildableParentSKU());
			if (res.getGrossPrice() != 0.0) {
				continue;
			}
			if (count == null) {
				kits.put(res.getKitBuildableParentSKU(), 1);
			} else {
				kits.put(res.getKitBuildableParentSKU(), count + 1);
			}
		}
		System.out.println("Number kits and their price :"+kits);
		for (OrderExportResponse res : orderExportResponse) {
			Integer count = kits.get(res.getProduct());
			if (count != null && count > 0) {
				double price = res.getGrossPrice() / count;
				childPriceMap.put(res.getProduct(), price);
				res.setGrossPrice(0);
			}
		}

		return childPriceMap;
	}

	private String getLines(OrderExportResponse order, Map<String, Double> kits) {
		StringJoiner sj = new StringJoiner(IFILE_DATA_SEPERATOR);

		String skuId = order.getProduct();
		
		sj.add("L");
		sj.add(updateSku(skuId));
		if (order.getKitBuildableParentSKU() != null) {
			sj.add(order.getKitBuildableParentSKU() + SPACE + order.getProductName());
		}
		else if (skuId != null && checkSKU(skuId)) {
			sj.add(skuId + SPACE + order.getProductName());
		}
		else {
			sj.add(order.getProductName());
		}
		
		sj.add("EA");
		sj.add(String.valueOf(order.getQuantity()));
		sj.add(String.valueOf(order.getGrossPrice()));
//		Double price = kits.get(order.getKitBuildableParentSKU());
//		if (price != null && price > 0) {
//			if (order.getGrossPrice() == 0.0) {
//				sj.add(String.valueOf(price));
//			} else {
//				sj.add(String.valueOf(order.getGrossPrice()));
//			}
//		} else {
//			sj.add(String.valueOf(order.getGrossPrice()));
//		}
		return sj.toString();

	}

	private String getHeader(OrderExportResponse orderExportResponse, String wareHouseName) {
		StringJoiner sj = new StringJoiner(IFILE_DATA_SEPERATOR);
		boolean isUsSite = isUSSite(wareHouseName);
		sj.add("E");
		sj.add(getSalesSite(isUsSite));
		sj.add(getOrderType(isUsSite));
		sj.add(orderExportResponse.getOrderNumber());
		sj.add(SOLD_TO_MAP.get(wareHouseName));
		sj.add(getDateInYYYYMMDD(orderExportResponse.getOrderDate()));
		sj.add(orderExportResponse.getShipmentId());
		sj.add(getSalesSite(isUsSite));
		sj.add(getCurrency(isUsSite));
		sj.add(EMPTRY_STR);
		sj.add(EMPTRY_STR);
		sj.add(EMPTRY_STR);
		sj.add(EMPTRY_STR);
		sj.add(EMPTRY_STR);
		sj.add(getAddress(orderExportResponse)); // Billing address
		sj.add(getAddress(orderExportResponse)); // order Address
		sj.add(String.valueOf(orderExportResponse.getShipping()));
		sj.add(String.valueOf(orderExportResponse.getDiscount()));
		return sj.toString();

	}

	private String getAddress(OrderExportResponse orderExportResponse) {
		StringJoiner sj = new StringJoiner(IFILE_DATA_SEPERATOR);
		sj.add(orderExportResponse.getShipFirstName());
		sj.add(orderExportResponse.getShipLastName());
		sj.add(getStrValue(orderExportResponse.getShipAddr1()));
		sj.add(getStrValue(orderExportResponse.getShipAddr2()));
		sj.add(orderExportResponse.getShipPostalCode());
		sj.add(orderExportResponse.getShipCity());
		sj.add(orderExportResponse.getShipState());
		sj.add(orderExportResponse.getShipCountry());

		return sj.toString();
	}

	private String getStrValue(Object value) {
		return value != null ? value.toString() : EMPTRY_STR;
	}

	public String getDateInYYYYMMDD(Date date) {
		return inputFormat.format(date);
	}

	private String getDate(String date) {
		System.out.println("input Date is : " + date);
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("M/d/yy");
		LocalDate ld = LocalDate.parse(date, formatter);
		System.out.println("output Date is : " + ld.getMonthValue() + " " + ld.getDayOfMonth() + " " + ld.getYear());
		return ld.getYear() + "" + (ld.getMonthValue() < 10 ? ("0" + ld.getMonthValue()) : ld.getMonthValue()) + ""
				+ (ld.getDayOfMonth() < 10 ? ("0" + ld.getDayOfMonth()) : ld.getDayOfMonth());
	}

	private String getCurrency(boolean isUsSite) {
		if (isUsSite) {
			return "USD";
		} else {
			return "CAD";
		}
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

	private String getOrderType(boolean isUsSite) {
		if (isUsSite) {
			return "AWEB";
		} else {
			return "AWEBC";
		}
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
//	public String getOrderDisplayIds(Integer runId, String accessToken) {
//
//		String dataExportFilterRequest = ApiUtil.getDataExportFilterRequest(runId);
//		String response = call(getDisplayIdUrl, HttpMethod.POST, dataExportFilterRequest, String.class,
//				getHeader(accessToken));
//		return response;
//	}

	// To get all the orderDisplay Id's ans Shipment Display ID associated with
	// runId

	public Map<String, String> getOrderShipmentIds(Integer runId, String accessToken, StringBuilder sb) {

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
		} catch (Exception ex) {
			sb.append(runId).append(IFILE_DATA_SEPERATOR);
		}
		return Map.of();
	}

	public OrderDetails orderDetails(String orderDisplayId, String accessToken) {

		String urlTemplate = UriComponentsBuilder.fromHttpUrl(orderDetailsUrl)
				.queryParam(ORDER_DISPLAY_ID, "{orderDisplayId}").encode().toUriString();
		Map<String, String> params = new HashMap<>();
		params.put(ORDER_DISPLAY_ID, orderDisplayId);
		OrderDetails orderDetails = call(urlTemplate, HttpMethod.GET, null, OrderDetails.class, getHeader(accessToken),
				params);
		params = null;
		System.out.println("Received order details for display Id : " + orderDetails.getDisplayID()
				+ " with order lines of size :" + orderDetails.getOrderLines().size());
		updateSkuId(orderDetails);
		return orderDetails;
	}

	private void updateSkuId(OrderDetails orderDetails) {
		if (orderDetails != null && orderDetails.getOrderLines() != null) {
			List<OrderLine> orderLine = orderDetails.getOrderLines();
			int lineLen = orderLine.size();
			System.out.println("order line length while updating sku : " + lineLen);
			for (int i = 0; i < lineLen; i++) {
				OrderLine line = orderLine.get(i);
				String skuId = line.getSKU();
				if (skuId != null && checkSKU(skuId)) {
					line.setSKU("09739");
					line.setDisplayName(skuId + SPACE + line.getDisplayName());
				}
				System.out.println("SkuId before buildable is : " + skuId);
				if (line.isBuildable() && lineLen > 1) {
					double price = line.getPrice();
					int count = 0;
					for (int j = i + 1; j < lineLen; j++) {

						if (orderLine.get(j).getBuildableKitOrderLineID() != null
								&& line.getOrderLineID() == Integer
										.parseInt(orderLine.get(j).getBuildableKitOrderLineID().toString())
								&& orderLine.get(j).getPrice() == 0.0) {
							count++;
						}
					}
					int childOrderCount = FlightOrderLineCache.getChildOrderCount(skuId);
					System.out.println("Sku ID : " + skuId + "and count :" + childOrderCount);
					if (childOrderCount > 0) {
						count = count + childOrderCount;
					}
					System.out.println("Total count for sku is : " + count);
					double distPrice = 0.0;
					if (count > 0) {
						distPrice = price / count;
						distPrice = getTwoDigitPrice(distPrice);
						line.setPrice(0.0);
					}
					for (int j = i + 1; j < lineLen; j++) {
						OrderLine tmpOrderLine = orderLine.get(j);
						System.out.println("temporary order line : " + tmpOrderLine.getSKU());
						if (tmpOrderLine.getBuildableKitOrderLineID() != null && line.getOrderLineID() == Integer
								.parseInt(tmpOrderLine.getBuildableKitOrderLineID().toString())) {
							if (tmpOrderLine.getPrice() == 0.0 && distPrice != 0.0) {
								tmpOrderLine.setPrice(distPrice);
							}
							tmpOrderLine.setDisplayName(skuId + SPACE + tmpOrderLine.getDisplayName());
						}
					}
					populateChildOrder(distPrice, orderLine, skuId, line.getQuantity());
				}
			}
		}
	}

	private void populateChildOrder(double price, List<OrderLine> orderLine, String skuId, int quantity) {

		System.out.println("Populating for child orders from config ");
		Set<String> childOrders = FlightOrderLineCache.getChildOrders(skuId);

		for (String order : childOrders) {

			OrderLine line = new OrderLine();
			int indexOf = order.indexOf("-");
			String skId = order.substring(0, indexOf);
			String displayName = order.substring(indexOf + 1, order.length());
			if (checkSKU(skId)) {
				line.setSKU("09739");
			} else {
				line.setSKU(skId);
			}
			line.setQuantity(quantity);
			line.setPrice(price);
			line.setDisplayName(displayName);
			orderLine.add(line);
		}
		System.out.println("Order line from child order for sku : " + skuId + "is : " + orderLine);
	}

	private double getTwoDigitPrice(double price) {
		DecimalFormat df = new DecimalFormat("#.##");
		return Double.valueOf(df.format(price));
	}

	private boolean checkSKU(String skuId) {
		return (!skuId.startsWith(EIGHT) || skuId.matches("^[a-zA-Z0-9]*$"));
	}
	
	private String updateSku(String skuId) {
		if(!skuId.startsWith(EIGHT) && !skuId.startsWith(NINE)) {
			return "09739";
		}
		else if(skuId.startsWith(EIGHT) || skuId.startsWith(NINE)) {
			return skuId.replaceAll("[a-zA-Z]", "").toString();
		}
		else {
			return skuId;
		}
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
