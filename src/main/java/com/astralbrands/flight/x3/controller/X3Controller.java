package com.astralbrands.flight.x3.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.astralbrands.flight.x3.service.FlightPayments;
import com.astralbrands.flight.x3.service.OrderIntegrationService;
import com.astralbrands.flight.x3.service.TrackingService;

@RestController
public class X3Controller {

	private static final Logger log = LoggerFactory.getLogger(X3Controller.class);

	@Autowired
	private OrderIntegrationService orderIntegrationService;
	
	@Autowired
	private FlightPayments flightPayments;


	@Autowired
	private TrackingService trackingService;

	@GetMapping("/health")
	public String healthCheck() {
		return "Flight order integration service is up and running!!";
	}

	@PostMapping(value = "/flightOrdersIntegration")
	public ResponseEntity<String> IntegrateFlightOrders() {
		return ResponseEntity.ok(orderIntegrationService.integrateOrders());
	}

	@PostMapping(value = "/getTrackingRecords")
	public ResponseEntity<String> exportX3ToCsv() {
		return ResponseEntity.ok(trackingService.importX3ToFlight());
	}
	
	@PostMapping(value = "/getPaymentUpdate")
	public ResponseEntity<String> integratePayments() {
		return ResponseEntity.ok(flightPayments.getPaymentsFromFlight());
	}

}
