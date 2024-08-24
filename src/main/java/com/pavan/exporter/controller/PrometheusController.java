package com.pavan.exporter.controller;

import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pavan.exporter.service.PrometheusService;

@RestController
//@RequestMapping("/exporter")
public class PrometheusController {
	@Autowired
	PrometheusService service;

	@GetMapping("/metrics/{database}")
	public CompletableFuture<String> endPointForPrometheus(@PathVariable String database) throws SQLException, InterruptedException, ExecutionException {
		return service.fetch(database);
	}

	@PutMapping("/reset")
	public String reset() {
		service.reset();
		return "reset successful";
	}
}
