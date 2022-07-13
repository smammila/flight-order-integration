package com.astralbrands.flight.x3.service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.astralbrands.flight.x3.model.Tracking;
import com.astralbrands.flight.x3.util.AppConstants;

@Component
public class TrackingService extends RestApiCallService implements AppConstants {

	@Autowired
	private DataSource x3DataSource;

	private Connection connection;

	@Value("${flight.import.shippingfile}")
	private String importShippingFileUrl;

	@Autowired
	private AccessTokenService accessTokenService;

	@Value("${x3.tracking.query}")
	private String trackingQuery;

	public List<Tracking> getTrackingDataInCsv() {
		if (x3DataSource != null) {
			try {
				if (connection == null) {
					connection = x3DataSource.getConnection();
				}
				try (Statement statement = connection.createStatement();) {
					String query = trackingQuery.replace("#currDate", getPreviousDate());
					//String query = trackingQuery.replace("#currDate", "07/01/2022");
					System.out.println("query :" + query);
					ResultSet trackingRecord = statement.executeQuery(query);
					System.out.println("result from x3 : " + trackingRecord);
					List<Tracking> trackingList = new ArrayList<>();
					if (!trackingRecord.next()) {
						System.out.println("Empty tracking record");
					} else {
						
						do {
							trackingList.add(new Tracking(trackingRecord.getString("CUSTOMER_ORDER_NUMBER"),
									trackingRecord.getString("TRACKING_NUMBER")));
						} while (trackingRecord.next());
					}
					if (!trackingList.isEmpty()) {
						return trackingList;
					}

				} catch (Exception e) {
					e.printStackTrace();
					System.err.println(" Exception " + e.getMessage());
				}

			} catch (SQLException ex) {
				ex.printStackTrace();
				System.err.println(ex.getMessage());
			}

		} else {
			System.err.println("Bit boot data source is null");
		}

		return null;
	}

	public String importX3ToFlight() {
		return importShippingFileToFlight(getTrackingDataInCsv());
	}

	public String importShippingFileToFlight(List<Tracking> trackingList) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setBearerAuth(accessTokenService.getAccessToken());
		System.out.println("Token is : " + accessTokenService.getAccessToken());
		System.out.println("Importing the tracking list to Flight, list :" + trackingList);
		if (!trackingList.isEmpty()) {
			return call(importShippingFileUrl, HttpMethod.POST, trackingList, String.class, headers);
		} else {
			return "No tracking list found";
		}
	}

	// Getting one day before date, if Monday get Friday's date
	private String getPreviousDate() {

		SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/YYYY");
		SimpleDateFormat dayFormatter = new SimpleDateFormat("EEEE");
		Calendar cal = Calendar.getInstance();
		Date date = new Date();
		if (dayFormatter.format(date).equals("Sunday")) {
			cal.add(Calendar.DATE, -2);
		} else if (dayFormatter.format(date).equals("Saturday")) {
			cal.add(Calendar.DATE, -1);
		} else if (dayFormatter.format(date).equals("Monday")) {
			cal.add(Calendar.DATE, -3);
		} else {
			cal.add(Calendar.DATE, -1);
		}
		System.out.println("Date is : " + formatter.format(new Date(cal.getTimeInMillis())));
		return formatter.format(new Date(cal.getTimeInMillis()));
	}

}
