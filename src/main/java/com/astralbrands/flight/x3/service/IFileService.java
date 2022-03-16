package com.astralbrands.flight.x3.service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.astralbrands.flight.x3.model.OrderAddress;
import com.astralbrands.flight.x3.model.OrderDetails;
import com.astralbrands.flight.x3.model.OrderLine;
import com.astralbrands.flight.x3.util.AppConstants;

@Service
public class IFileService implements AppConstants {

	private static final Map<String, String> SOLD_TO_MAP = new HashMap<>(5);
	private SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd");

	static {
		SOLD_TO_MAP.put("Us Direct to Corporate", "49999988");
		SOLD_TO_MAP.put("US Corp Home Office Warehouse", "49999999");
		SOLD_TO_MAP.put("Corp Warehouse CAD", "49999997");
	}

	@Value("${ifile.fileName}")
	private String fileName;

	public String generateIFile(Map<String, OrderDetails> orders) {
		StringBuilder fileData = new StringBuilder();
		for (Map.Entry<String, OrderDetails> entry : orders.entrySet()) {
			List<OrderLine> ordLine = entry.getValue().getOrderLines();
			String header = null;
			if (ordLine != null) {
				for (OrderLine orderLine : ordLine) {
					if (header == null) {
						header = getHeader(orderLine, entry.getValue());
						fileData.append(header).append(NEW_LINE);
					}
					fileData.append(getOrderLineData(orderLine)).append(NEW_LINE);
				}
			}
		}
		System.out.println("file Data :" + fileData);
		exportFile(fileData.toString());
		return fileData.toString();

	}

	private String getOrderLineData(OrderLine orderLine) {
		StringJoiner sj = new StringJoiner(IFILE_DATA_SEPERATOR);
		sj.add("L");
		return sj.toString();
	}

	public String getHeader(OrderLine line, OrderDetails orderDetails) {
		StringJoiner sj = new StringJoiner(IFILE_DATA_SEPERATOR);
		boolean isUsSite = isUSSite(line.getWarehouseName());
		sj.add("E");
		sj.add(getSalesSite(isUsSite));
		sj.add(getOrderType(isUsSite));
		sj.add(orderDetails.getDisplayID());
		sj.add(SOLD_TO_MAP.get(line.getWarehouseName()));
		sj.add(getDateInYYYYMMDD(orderDetails.getOrderDate()));
		sj.add(EMPTRY_STR);
		sj.add(getSalesSite(isUsSite));
		sj.add(getCurrency(isUsSite));
		sj.add(EMPTRY_STR);
		sj.add(EMPTRY_STR);
		sj.add(EMPTRY_STR);
		sj.add(EMPTRY_STR);
		sj.add(EMPTRY_STR);
		OrderAddress add = orderDetails.getOrderAddress();
		if(add != null) {
			sj.add(add.getFirstName());
			sj.add(add.getLastName());
			sj.add(add.getStreet1());
			sj.add(getStrValue(add.getStreet2()));
			//sj.add(newElement)
		}else {
			
		}
		return sj.toString();
	}
	
	private String getStrValue(Object value) {
		return value != null ? value.toString() : EMPTRY_STR;
	}

	public String getDateInYYYYMMDD(Date date) {
		return inputFormat.format(date);
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

	private void exportFile(String fileData) {
		Path path = Paths.get(fileName);
		try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {

			writer.write(fileData);
			System.out.println("file written successfully");
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("Error while writing file");
		}

	}

}
