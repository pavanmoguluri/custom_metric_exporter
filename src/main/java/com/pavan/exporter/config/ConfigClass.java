package com.pavan.exporter.config;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;

import com.pavan.exporter.metrics.DefineMetrics;
import com.pavan.exporter.metrics.PrometheusMetric;

import io.prometheus.client.CollectorRegistry;

@Configuration
@PropertySource("file:${EXPORTER_PATH}//exporter.properties")
public class ConfigClass implements ImportBeanDefinitionRegistrar {

	@Autowired
	private Environment env;
	
	@Autowired
	private ApplicationContext applicationContext;
	
	@Autowired
	DefaultListableBeanFactory defaultListableBeanFactory;
	
	@Bean
	public Map<String, List<PrometheusMetric>> getMetrics() {
		File folder = new File(env.getProperty("metrics.directory"));
		Map<String, List<PrometheusMetric>> metricList = new HashMap<>();
		DefineMetrics df = new DefineMetrics();
		BeanDefinitionBuilder b = BeanDefinitionBuilder.rootBeanDefinition(CollectorRegistry.class);
		for (File file : folder.listFiles()) {
			String fileNameStr = file.getName().split("\\.")[0];
			defaultListableBeanFactory.registerBeanDefinition(fileNameStr, b.getBeanDefinition());
			CollectorRegistry collectorRegistry = applicationContext.getBean(fileNameStr, CollectorRegistry.class);
			df.createMetrics2(collectorRegistry, file, metricList);
			
		}
		return metricList;
	}

}
