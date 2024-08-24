package com.pavan.exporter.util;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;

import com.moandjiezana.toml.Toml;
import com.pavan.exporter.metrics.PrometheusMetric;

import io.micrometer.core.instrument.Clock;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.exporter.common.TextFormat;

public class PluggableDB {

	public Map<String, String> connectPdb(String url, String username, String password, List<String> pdbSidList,
			String database, String directry) throws SQLException {
		String result = "";
		Map<String, String> scrapeResp = new HashMap<>();
		String[] UrlArray = url.split(":");
		List<String> list = new ArrayList<String>(Arrays.asList(UrlArray));
		list.remove(list.size() - 1);
		String withoutSid = String.join(":", list);
		CollectorRegistry register = new CollectorRegistry();
		List<PrometheusMetric> metrics = this.pdbmetrics(register, database, directry);
		for (String servName : pdbSidList) {
			String newUrl = withoutSid + ":" + servName;
			PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT, register,
					Clock.SYSTEM);
			result = this.scrapeMetrics(registry, newUrl, username, password, metrics);
			result = result.replace("# EOF", "");
			if(result.isEmpty()) {
				result = null;
			}
			scrapeResp.put(servName, result);
		}
		return scrapeResp;
	}

	// Defining metrics to PDB with metrics defined to CDB
	public List<PrometheusMetric> pdbmetrics(CollectorRegistry register, String database, String directry) {
		List<PrometheusMetric> prometheusmetric = new ArrayList<>();

		File folder = new File(directry);
		for (File file : folder.listFiles()) {
			String fileName = file.getName();
			if (fileName.equals(database + ".toml")) {
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
								String name = "oraclepdb" + "_" + toml.getString(table + ".context") + "_"
										+ element.getKey();
								Counter counter = Counter.build().name(name).help((String) element.getValue())
										.labelNames(stringArray).register(register);
								metricList.add(counter);
								metricColumns.add(element.getKey());
							}
							PrometheusMetric metric = new PrometheusMetric(metricList, labelsList, metricColumns,
									"COUNTER", toml.getString(table + ".request"), min_time);
							prometheusmetric.add(metric);
							i++;
						} else if (toml.getString(table + ".type").equalsIgnoreCase("GAUGE")) {
							List<String> labelsList = toml.getList(table + ".labels");
							List<String> metricColumns = new ArrayList<String>();
							Object[] objectArray = labelsList.toArray();
							String[] stringArray = Arrays.copyOf(objectArray, objectArray.length, String[].class);
							List<Collector.Describable> metricList = new ArrayList<Collector.Describable>();
							for (Entry<String, Object> element : toml.getTable(table + ".metricsdesc").entrySet()) {
								String name = "oraclepdb_" + toml.getString(table + ".context") + "_"
										+ element.getKey();
								Gauge gauge = Gauge.build().name(name).help((String) element.getValue())
										.labelNames(stringArray).register(register);
								metricList.add(gauge);
								metricColumns.add(element.getKey());
							}
							PrometheusMetric metric = new PrometheusMetric(metricList, labelsList, metricColumns,
									"GAUGE", toml.getString(table + ".request"), min_time);
							prometheusmetric.add(metric);
							i++;
						}

					} else {
						System.out.println("min_time less than 1 min   " + toml.getString(table + ".context"));
						i++;
					}
				}
			}
		}

		return prometheusmetric;
	}

	@SuppressWarnings("unused")
	public String scrapeMetrics(PrometheusMeterRegistry registry, String url, String username, String password,
			List<PrometheusMetric> metrics) {
		Connection con = null;

		try {
			con = DriverManager.getConnection(url, username, password);

			Statement stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
			for (PrometheusMetric metric : metrics) {
				boolean init_exec = metric.isInitial_exec();
				Long last_time_execution = metric.getLast_time_executed();
				Long time_lapsed = System.currentTimeMillis() - last_time_execution;
				if (init_exec || time_lapsed >= metric.getMin_time()) {
					try {
						String sql = metric.getSql();
						ResultSet rs = stmt.executeQuery(sql);

						if (metric.getType().equalsIgnoreCase("COUNTER")) {
							while (rs.next()) {
								List<String> labels = metric.getLabels();
								List<Collector.Describable> metricList = metric.getMetricList();
								List<String> returnedLabels = new ArrayList<String>();

								for (String label : labels) {
									returnedLabels.add(rs.getString(label));
								}
								Object[] objectArray = returnedLabels.toArray();
								String[] labelArray = Arrays.copyOf(objectArray, objectArray.length, String[].class);
								int metricColumnParser = 0;
								for (Collector.Describable collector : metricList) {
									Counter counter = (Counter) collector;
									if (sql.equalsIgnoreCase("select 1 as DUMMY from dual")) {
										counter.clear();
									}
									counter.labels(labelArray).inc();
									metricColumnParser++;
								}
								metric.setLast_time_executed(System.currentTimeMillis());
							}
						} else if (metric.getType().equalsIgnoreCase("GAUGE")) {
							List<String> labels = metric.getLabels();
							List<String> metricColumns = metric.getMetricColumns();
							List<Collector.Describable> metricList = metric.getMetricList();
							while (rs.next()) {
								List<String> returnedLabels = new ArrayList<String>();
								for (String label : labels) {
									returnedLabels.add(rs.getString(label));
								}
								Object[] objectArray = returnedLabels.toArray();
								for (int i = 0; i < objectArray.length; i++) {
									if (objectArray[i] == null) {
										objectArray[i] = "<nill>";
									}
								}
								String[] labelArray = Arrays.copyOf(objectArray, objectArray.length, String[].class);
								int metricColumnParser = 0;
								for (Collector.Describable collector : metricList) {
									Gauge gauge = (Gauge) collector;
									if (sql.equalsIgnoreCase("select 1 as DUMMY from dual")) {
										gauge.clear();
									}
									gauge.labels(labelArray).set(rs.getDouble(metricColumns.get(metricColumnParser)));
									metricColumnParser++;
								}
							}
							metric.setLast_time_executed(System.currentTimeMillis());
						}
					} catch (IllegalArgumentException | SQLSyntaxErrorException e) {
						System.out.println("check the Query- " + metric.getSql());
//					e.printStackTrace();
					}
				}

				metric.setInitial_exec(false);
			}
		} catch (SQLException e1) {
			// TODO Auto-generated catch block
			CollectorRegistry regist = registry.getPrometheusRegistry();
			regist.clear();
			e1.printStackTrace();
		}
		return registry.scrape(TextFormat.CONTENT_TYPE_004);
	}

}
