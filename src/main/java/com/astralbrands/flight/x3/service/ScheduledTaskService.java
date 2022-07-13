package com.astralbrands.flight.x3.service;

import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledTaskService {

	@Autowired
	private FlightServices flightServices;

	@Scheduled(cron = "0 0/1 * * * *")
	public void updateOrderToX3() {
		//System.out.println("updating order from flight to X3 :" + new Date());
	}
	
	@Scheduled(cron = "0 0/1 * * * *")
	public void updateTrackingRecord() {
		//System.out.println("Get tracking record from X3 and publish to Flight system :" + new Date());
	}

}
