package com.astralbrands.flight.x3.service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.astralbrands.flight.x3.model.OrderDetails;

@Service
public class OrderIntegrationServiceImpl implements OrderIntegrationService{
	
	@Autowired
	private FlightServices flightServices;
	
	@Autowired
	private IFileService iFileService;
	
	@Override
	public void integrateOrders(MultipartFile file) {
		
		Map<String,OrderDetails> orderDetails = flightServices.getOrderDetails();
		if(orderDetails != null) {
			iFileService.generateIFile(orderDetails);
		}
	}

}
