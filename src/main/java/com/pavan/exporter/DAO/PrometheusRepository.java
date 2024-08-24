package com.pavan.exporter.DAO;


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
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Repository;

import com.pavan.exporter.metrics.PrometheusMetric;
import com.pavan.exporter.util.PluggableDB;

import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.prometheus.client.Collector;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.exporter.common.TextFormat;

@Repository
public class PrometheusRepository {

	@Autowired
	private Environment env;

	public String setMetrics(PrometheusMeterRegistry registry, String database,
			Map<String, List<PrometheusMetric>> metricmap) throws SQLException {
		Connection con = null;
		Map<String, String> pdbresp = new HashMap<String,String>();
		Map<String, String> resturnResult = new HashMap<String, String>();
		String driver = env.getProperty(database + ".driver");
		String username = env.getProperty(database + ".username");
		String password = env.getProperty(database + ".password");
		String url = env.getProperty(database + ".url");
		String directory = env.getProperty("metrics.directory");
		List<String> pdbSidList = new ArrayList<String>();
		try {
			if (driver != null)
				Class.forName(driver);
			con = DriverManager.getConnection(url, username, password);
			Statement stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,ResultSet.CONCUR_READ_ONLY);
			List<PrometheusMetric> metrics = metricmap.get(database);
			ResultSet resultSet = stmt.executeQuery("select 1 as DUMMY from dual");
			double d = 0.00;
			while (resultSet.next()) {
				d = resultSet.getDouble(Arrays.asList("DUMMY").get(0));
			}
			if (d == 1.0) {
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
									String[] labelArray = Arrays.copyOf(objectArray, objectArray.length,
											String[].class);
									int metricColumnParser = 0;
									for (Collector.Describable collector : metricList) {
										Counter counter = (Counter) collector;
										if(sql.equalsIgnoreCase("select 1 as DUMMY from dual")) {
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
									String[] labelArray = Arrays.copyOf(objectArray, objectArray.length,
											String[].class);
									int metricColumnParser = 0;
									for (Collector.Describable collector : metricList) {
										Gauge gauge = (Gauge) collector;
										if(sql.equalsIgnoreCase("select 1 as DUMMY from dual")) {
											gauge.clear();
										}
										gauge.labels(labelArray)
												.set(rs.getDouble(metricColumns.get(metricColumnParser)));
										metricColumnParser++;
									}
								}
								metric.setLast_time_executed(System.currentTimeMillis());
							}
						} catch (IllegalArgumentException | SQLSyntaxErrorException e) {
							System.out.println("check the Query- " + metric.getSql());
//						e.printStackTrace();
						}
					}

					metric.setInitial_exec(false);
				}
//				pdbSidList = this.getPdbSidList(stmt);
//				PluggableDB pluggableDB = new PluggableDB ();
//				pdbresp = pluggableDB.connectPdb(url,username,password,pdbSidList,database,directory);
			} else {
				PrometheusMetric metric = metrics.get(metrics.size() - 1);
				List<String> labels = metric.getLabels();
				List<String> metricColumns = metric.getMetricColumns();
				List<Collector.Describable> metricList = metric.getMetricList();
				resultSet.beforeFirst();
				while (resultSet.next()) {
					List<String> returnedLabels = new ArrayList<String>();
					for (String label : labels) {
						returnedLabels.add(resultSet.getString(label));
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
						gauge.clear();
						System.out.println("Not Finally"+database);
						gauge.labels(labelArray).set(resultSet.getDouble(metricColumns.get(metricColumnParser)));
						metricColumnParser++;
					}
				}
				metric.setLast_time_executed(System.currentTimeMillis());
			}

		} catch (SQLException | ClassNotFoundException e) {
			
			System.out.println("FAILED TO ESTABLISH CONNECTION / connect to winfo VPN");
			e.printStackTrace();
		} finally {
			if (con != null) {
				con.close();
			}else {
				List<PrometheusMetric> metrics = metricmap.get(database);
				PrometheusMetric metric = metrics.get(metrics.size() - 1);
				List<Collector.Describable> metricList = metric.getMetricList();
					List<String> returnedLabels = new ArrayList<>();
					System.out.println("FAILED TO ESTABLISH CONNECTION");
					returnedLabels.add("0");
					Object[] objectArray = returnedLabels.toArray();
					String[] labelArray = Arrays.copyOf(objectArray, objectArray.length, String[].class);
					for (Collector.Describable collector : metricList) {
						Gauge gauge = (Gauge) collector;
						gauge.clear();
						gauge.labels(labelArray).set(0.00);
					}
				metric.setLast_time_executed(System.currentTimeMillis());
			}
		}
		String mainMetrics = registry.scrape(TextFormat.CONTENT_TYPE_OPENMETRICS_100);
		String PdbListStr = String.join(",", pdbSidList);
		resturnResult.put("pdblist",PdbListStr);
		resturnResult.put("CdbMetrcis", mainMetrics);
		resturnResult.putAll(pdbresp);
//		String finalResult = resturnResult.entrySet().stream().map(e -> e.getKey() + ":" + e.getValue())
//                .collect(Collectors.joining(";"));
		return registry.scrape(TextFormat.CONTENT_TYPE_OPENMETRICS_100);
	}
	public List<String> getPdbSidList(Statement stmt) throws SQLException {
		List<String> pdbServ = new ArrayList<String>();
		try {
			
			ResultSet resSet = stmt.executeQuery("select a.NAME as CDB_NAME, b.PDB_ID as PDB_ID, b.PDB_NAME as PDB_NAME, b.DBID as DBID , b.CON_UID as CON_UID, b.CON_ID as CON_ID  , b.APPLICATION_PDB as APPLICATION_PDB, b.APPLICATION_ROOT_CON_ID as APPLICATION_ROOT_CON_ID,  b.APPLICATION_SEED as APPLICATION_SEED, b.STATUS as STATUS from dba_pdbs b, v$database a");
//		String cdbName = null;
//		Map<String ,List<String>> pdbMap = new HashMap<>();
			while(resSet.next()) {
//			cdbName = resSet.getString("CDB_NAME");
				pdbServ.add(resSet.getString("PDB_NAME"));
			}
//		pdbMap.put(cdbName, pdbServ);
		}catch(SQLException e) {
			e.printStackTrace();
		}
		return pdbServ;
	}
}
