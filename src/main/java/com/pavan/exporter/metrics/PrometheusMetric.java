package com.pavan.exporter.metrics;

import java.util.List;

import io.prometheus.client.Collector;

public class PrometheusMetric {

	private List<Collector.Describable> metricList;

	private List<String> labels;

	private List<String> metricColumns;

	private String type;

	private String sql;

	private Long last_time_executed;

	private Double min_time;

	private boolean initial_exec;

	public PrometheusMetric(List<Collector.Describable> metricList, List<String> labels, List<String> metricColumns,
			String type, String sql, Double min_time) {
		this.metricList = metricList;
		this.type = type;
		this.sql = sql;
		this.labels = labels;
		this.metricColumns = metricColumns;
		this.last_time_executed = System.currentTimeMillis();
		this.min_time = min_time;
		this.initial_exec = true;
	}

	public List<Collector.Describable> getMetricList() {
		return metricList;
	}

	public void setMetricList(List<Collector.Describable> metricList) {
		this.metricList = metricList;
	}

	public List<String> getLabels() {
		return labels;
	}

	public void setLabels(List<String> labels) {
		this.labels = labels;
	}

	public List<String> getMetricColumns() {
		return metricColumns;
	}

	public void setMetricColumns(List<String> metricColumns) {
		this.metricColumns = metricColumns;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getSql() {
		return sql;
	}

	public void setSql(String sql) {
		this.sql = sql;
	}

	public Long getLast_time_executed() {
		return last_time_executed;
	}

	public void setLast_time_executed(Long last_time_executed) {
		this.last_time_executed = System.currentTimeMillis();
	}

	public Double getMin_time() {
		return min_time;
	}

	public boolean isInitial_exec() {
		return initial_exec;
	}

	public void setInitial_exec(boolean initial_exec) {
		this.initial_exec = initial_exec;
	}

}
