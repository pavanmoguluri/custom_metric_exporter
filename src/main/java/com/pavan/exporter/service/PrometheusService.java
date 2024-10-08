package com.pavan.exporter.service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.pavan.exporter.DAO.PrometheusRepository;
import com.pavan.exporter.config.ConfigClass;
import com.pavan.exporter.metrics.PrometheusMetric;

import io.micrometer.core.instrument.Clock;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;

@Service
public class PrometheusService {

	@Autowired
	PrometheusRepository repo;

	@Autowired
	ConfigClass configClass;
	
	@Autowired
	ApplicationContext applicationContext;
	
	@Async
	public CompletableFuture<String> fetch(String database) throws SQLException {
		CollectorRegistry collectorRegistry = applicationContext.getBean(database, CollectorRegistry.class);
		Map<String, List<PrometheusMetric>> metricList = new HashMap<>();
		List<Collector.Describable> metrics = new ArrayList<Collector.Describable>();
		List<PrometheusMetric> ls = configClass.getMetrics().get(database);
		if(!(ls.get(ls.size()-1).getSql().equalsIgnoreCase("select 1 as DUMMY from dual"))) {
		Gauge gauge = Gauge.build().name("oracledb_up").help("oracledb_up Whether the oracle db is up").labelNames("DUMMY")
				.register(collectorRegistry);
		
		metrics.add(gauge);
		PrometheusMetric metric = new PrometheusMetric(metrics, Arrays.asList("DUMMY"), Arrays.asList("DUMMY"), "GAUGE",
				"select 1 as DUMMY from dual", 0.00);
		ls.add(metric);
		metricList.put(database,ls);
		}
		PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT,
				collectorRegistry, Clock.SYSTEM);
		return CompletableFuture.supplyAsync(()->{
			String resultStr = null;
			try {
				resultStr = repo.setMetrics(registry, database, configClass.getMetrics());
			} catch (SQLException e) {
				e.printStackTrace();
			}
			return resultStr;
		});
	}

	public void reset() {
		Map<String,List<PrometheusMetric>> metrics = configClass.getMetrics();
		Set<String> keys = metrics.keySet();
		for(String key: keys) {
			List<PrometheusMetric> values = metrics.get(key);
			for(PrometheusMetric value: values) {
				value.setInitial_exec(true);
			}
		}
	}
}
