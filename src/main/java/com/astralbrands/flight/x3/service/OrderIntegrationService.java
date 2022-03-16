package com.astralbrands.flight.x3.service;

import org.springframework.web.multipart.MultipartFile;

public interface OrderIntegrationService {

	default void integrateOrders(MultipartFile file) {
	}

}
