package com.pavan.exporter.metrics;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.springframework.stereotype.Component;

import com.moandjiezana.toml.Toml;

import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;

@Component
public class DefineMetrics {
	
	public Map<String,List<PrometheusMetric>> createMetrics2(CollectorRegistry register, File file, Map<String,List<PrometheusMetric>> list) {
		String fileName = file.getName();
		List<PrometheusMetric> prometheusmetric = new ArrayList<>();
		Toml toml = new Toml().read(file);
		int i = 0;
		while (toml.containsTable("metric[" + i + "]")) {
			String table = "metric[" + i + "]";
			String time = toml.getString(table + ".schedule");
			Double min_time = 0.0;
			if (time == null) {
				System.out.println("add schedule column in metric	" + toml.getString(table + ".context"));
				i++;
			}
			if (time.contains("s")) {
				min_time = Double.parseDouble(time.replaceAll("[^\\d.]", ""));
			} else if (time.contains("m")) {
				min_time = Double.parseDouble(time.replaceAll("[^\\d.]", "")) * 60000;
			} else if (time.contains("h")) {
				min_time = Double.parseDouble(time.replaceAll("[^\\d.]", "")) * 3600000;
			}

			if (min_time >= 60000) {
				if (toml.getString(table + ".type").equalsIgnoreCase("COUNTER")) {
					List<String> labelsList = toml.getList(table + ".labels");
					List<String> metricColumns = new ArrayList<String>();
					Object[] objectArray = labelsList.toArray();
					String[] stringArray = Arrays.copyOf(objectArray, objectArray.length, String[].class);
					List<Collector.Describable> metricList = new ArrayList<Collector.Describable>();
					for (Entry<String, Object> element : toml.getTable(table + ".metricsdesc").entrySet()) {
						String name = "oracledb" + "_" + toml.getString(table + ".context") + "_" + element.getKey();
						Counter counter = Counter.build().name(name).help((String) element.getValue())
								.labelNames(stringArray).register(register);
						metricList.add(counter);
						metricColumns.add(element.getKey());
					}
					PrometheusMetric metric = new PrometheusMetric(metricList, labelsList, metricColumns, "COUNTER",
							toml.getString(table + ".request"), min_time);
					prometheusmetric.add(metric);
					i++;
				} else if (toml.getString(table + ".type").equalsIgnoreCase("GAUGE")) {
					List<String> labelsList = toml.getList(table + ".labels");
					List<String> metricColumns = new ArrayList<String>();
					Object[] objectArray = labelsList.toArray();
					String[] stringArray = Arrays.copyOf(objectArray, objectArray.length, String[].class);
					List<Collector.Describable> metricList = new ArrayList<Collector.Describable>();
					for (Entry<String, Object> element : toml.getTable(table + ".metricsdesc").entrySet()) {
						String name = "oracledb_" + toml.getString(table + ".context") + "_" + element.getKey();
						Gauge gauge = Gauge.build().name(name).help((String) element.getValue()).labelNames(stringArray)
								.register(register);
						metricList.add(gauge);
						metricColumns.add(element.getKey());
					}
					PrometheusMetric metric = new PrometheusMetric(metricList, labelsList, metricColumns, "GAUGE",
							toml.getString(table + ".request"), min_time);
					prometheusmetric.add(metric);
					i++;
				}

			} else {
				System.out.println("min_time less than 1 min   " + toml.getString(table + ".context"));
				i++;
			}
		}
		if(!(list.containsKey(fileName))) {
			list.put(fileName, prometheusmetric);
		}else {
			List<PrometheusMetric> ls = list.get(fileName);
			ls.addAll(prometheusmetric);
			list.put(fileName,ls);
		}
		return list;
	}
}
