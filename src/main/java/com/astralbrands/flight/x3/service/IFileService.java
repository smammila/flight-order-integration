package com.astralbrands.flight.x3.service;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.astralbrands.flight.x3.model.BillingAddress;
import com.astralbrands.flight.x3.model.OrderAddress;
import com.astralbrands.flight.x3.model.OrderDetails;
import com.astralbrands.flight.x3.model.OrderLine;
import com.astralbrands.flight.x3.model.OrderTaxTotal;
import com.astralbrands.flight.x3.model.Transaction;
import com.astralbrands.flight.x3.util.AppConstants;

@Service
public class IFileService implements AppConstants {

	private static final Map<String, String> SOLD_TO_MAP = new HashMap<>(5);
	private SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd");

	@Autowired
	private MailingService mailingService;

	static {
		SOLD_TO_MAP.put("US Direct to Corporate", "49999988");
		SOLD_TO_MAP.put("US Corp Home Office Warehouse", "49999999");
		SOLD_TO_MAP.put("Corp Warehouse CAD", "49999997");
	}

	@Value("${ifile.fileName}")
	private String fileName;

	public String generateIFile(Map<String, OrderDetails> orders) {
		StringBuilder fileData = new StringBuilder();
		for (Map.Entry<String, OrderDetails> entry : orders.entrySet()) {
			List<OrderLine> ordLine = entry.getValue().getOrderLines();
			System.out.println("Order display processing is : " + entry.getValue().getDisplayID());
			String header = null;
			if (ordLine != null) {
				for (OrderLine orderLine : ordLine) {
					if (header == null) {
						header = getHeader(orderLine, entry.getValue(), entry.getKey());
						fileData.append(header).append(NEW_LINE);
					}
					fileData.append(getOrderLineData(orderLine)).append(NEW_LINE);
				}
			}
		}
		String fileDataStr = fileData.toString();
		System.out.println("file Data :" + fileData);
		exportFile(fileDataStr);
		sendMailFileData(fileDataStr, "flight_Order_Summary");
		return fileData.toString();

	}

	private void sendMailFileData(String fileData, String fileName) {
		mailingService.sendMailWithAttachment(fileData, "Flight order details ", fileName);
	}

	private String getOrderLineData(OrderLine orderLine) {
		StringJoiner sj = new StringJoiner(IFILE_DATA_SEPERATOR);
		sj.add("L");
		sj.add(orderLine.getSKU());
		// sj.add(updateSKU(orderLine.getSKU()));
		sj.add(orderLine.getDisplayName());
		sj.add("EA"); // Sales Unit
		sj.add(String.valueOf(orderLine.getQuantity()));
		sj.add(String.valueOf(orderLine.getPrice()));
		return sj.toString();
	}

	public String getHeader(OrderLine line, OrderDetails orderDetails, String orderShippingId) {
		StringJoiner sj = new StringJoiner(IFILE_DATA_SEPERATOR);
		boolean isUsSite = isUSSite(line.getWarehouseName());
		System.out.println("printing header for display id: " + orderDetails.getDisplayID());
		sj.add("E");
		sj.add(getSalesSite(isUsSite));
		sj.add(getOrderType(isUsSite));
		sj.add(orderDetails.getDisplayID());
		sj.add(SOLD_TO_MAP.get(line.getWarehouseName()));
		sj.add(getDateInYYYYMMDD(orderDetails.getOrderDate()));
		sj.add(orderShippingId);
		sj.add(getSalesSite(isUsSite));
		sj.add(getCurrency(isUsSite));
		sj.add(EMPTRY_STR);
		sj.add(EMPTRY_STR);
		sj.add(EMPTRY_STR);
		sj.add(EMPTRY_STR);
		sj.add(EMPTRY_STR);
		if (orderDetails.getAddressCount() == 1) {
			sj.add(getBillingAddress(orderDetails));
			sj.add(getAddress(orderDetails));
		}
		/*
		 * else if(orderDetails.getAddressCount()==2) { getAddress(orderDetails);
		 * getAddress(orderDetails); }
		 */
		sj.add(String.valueOf(orderDetails.getShippingPrice()));
		sj.add(String.valueOf(orderDetails.getDiscountTotal()));
		List<OrderTaxTotal> taxTotal = orderDetails.getOrderTaxTotals();
		if (taxTotal != null) {
			double totalTax = taxTotal.stream().map(tax -> {
				return tax.getTax();
			}).mapToDouble(Double::doubleValue).sum(); // (entry)->sj.add(String.valueOf(entry.getTax()))
			sj.add(String.valueOf(totalTax));
		} else {
			sj.add(EMPTRY_STR);
		}
		sj.add("USPSP"); // Delivery Mode
		sj.add("USPS"); // Carrier
		return sj.toString();
	}

	private String getStrValue(Object value) {
		return value != null ? value.toString() : EMPTRY_STR;
	}

	public String getDateInYYYYMMDD(Date date) {
		return inputFormat.format(date);
	}

	private String getBillingAddress(OrderDetails orderAddress) {
		ArrayList<Transaction> transaction = orderAddress.getTransactions();
		if (isBillingAddressPresent(transaction)) {
			String billingAdd = getAddress(transaction.get(0).getBillingAddress());
			return billingAdd != null ? billingAdd : getAddress(orderAddress);
		} else {
			return getAddress(orderAddress);
		}
	}

	private boolean isBillingAddressPresent(ArrayList<Transaction> transaction) {
		return transaction != null && !transaction.isEmpty() && transaction.get(0).getBillingAddress() != null;
	}

	private String getAddress(BillingAddress address) {
		if (address.getFirstName() != null && address.getLastName() != null) {
			return null;
		} else {
			StringJoiner sj = new StringJoiner(IFILE_DATA_SEPERATOR);
			sj.add(getStrValue(address.getFirstName()));
			sj.add(getStrValue(address.getLastName()));
			sj.add(address.getStreet1());
			sj.add(getStrValue(address.getStreet2()));
			sj.add(address.getPostalCode());
			sj.add(address.getCity());
			sj.add(address.getProvinceAbbreviation());
			return sj.toString();
		}
	}

	private String getAddress(OrderDetails orderAddress) {
		StringJoiner sj = new StringJoiner(IFILE_DATA_SEPERATOR);
		OrderAddress address = orderAddress.getOrderAddress();
		if (address != null) {
			sj.add(address.getFirstName());
			sj.add(address.getLastName());
			sj.add(address.getStreet1());
			sj.add(getStrValue(address.getStreet2()));
			sj.add(address.getPostalCode());
			sj.add(address.getCity());
			sj.add(address.getProvince().getAbbreviation());
			// sj.add(add.get); please add all the header and values of line
		} else {
			for (int i = 0; i < 7; i++) {
				sj.add(EMPTRY_STR);
			}
		}
		return sj.toString();
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

	public void exportFile(String fileData) {
		Path path = Paths.get(fileName);
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(fileName+".txt")))) {

			writer.write(fileData);
			System.out.println("ifile written successfully to " + fileName);
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("Error while writing ifile");
		}
		Path csvPath = Paths.get(fileName + ".csv");
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(fileName+".csv")))) {

			writer.write(fileData.replaceAll(IFILE_DATA_SEPERATOR, COMMA));
			System.out.println("Csv file written successfully to " + csvPath.getFileName());
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("Error while writing Csv file");
		}

	}

}
