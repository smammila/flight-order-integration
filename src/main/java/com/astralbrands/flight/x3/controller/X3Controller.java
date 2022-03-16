package com.astralbrands.flight.x3.controller;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.astralbrands.flight.x3.model.OrderDetails;
import com.astralbrands.flight.x3.service.FlightServices;
import com.astralbrands.flight.x3.service.OrderIntegrationService;

@RestController
public class X3Controller {
	
	private static final Logger log = LoggerFactory.getLogger(X3Controller.class);

	@Autowired
	private OrderIntegrationService orderIntegrationService;

	
	@Autowired
	private FlightServices flightServices;
	
	@GetMapping("/health")
	public Map<String,OrderDetails> healthCheck() {
		orderIntegrationService.integrateOrders(null);
		//return "Flight order integration service is up and running!! \n search criteria result : "+accessToken;
		return flightServices.getOrderDetails();
	}
	
	@PostMapping(value = "/flightOrdersIntegration")
	public ResponseEntity<String> uploadAttendee(@RequestParam("file") MultipartFile file) {

	    String message = "";
	    try {
	       log.info("File recieved :"+file.getName());

	        message = "You successfully uploaded " + file.getOriginalFilename() + "!";
	        String data = new BufferedReader(new InputStreamReader(file.getInputStream()))
	        .lines().parallel().collect(Collectors.joining("\n"));
	        orderIntegrationService.integrateOrders(file);
	        
	        log.info(message);
      
	        return ResponseEntity.status(HttpStatus.OK).body(data);
	    } catch (Exception e) {
	        message = "FAIL to upload " + file.getOriginalFilename() + "!";
	        return ResponseEntity.status(HttpStatus.EXPECTATION_FAILED).body(message);
	    }
	}

}
