package com.astralbrands.flight.x3.service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.astralbrands.flight.x3.model.OrderDetails;

@Service
public class OrderIntegrationServiceImpl implements OrderIntegrationService{
	
	@Autowired
	private FlightServices flightServices;
	
	@Autowired
	private IFileService iFileService;
	
	@Autowired
	private AwsLambdaService awsLambdaService;
	
	@Override
	public String integrateOrders() {
		String fileData = null;
		//Stores Order Details for each OrderID
		Map<String,OrderDetails> orderDetails = flightServices.getOrderDetails();
		if(orderDetails != null) {
			System.out.println("Final order details are: "+orderDetails.toString());
			fileData = iFileService.generateIFile(orderDetails);
			//awsLambdaService.integrateToX3(fileData);
		}
		return fileData;
	}

}
