package com.grocery.productread;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts a throw-away Elasticsearch in Docker - same version as infrastructure/docker-compose.yml,
 * with security switched off like the local one.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ElasticsearchTestcontainersConfiguration {

	@Bean
	@ServiceConnection
	ElasticsearchContainer elasticsearchContainer() {
		return new ElasticsearchContainer(DockerImageName.parse("elasticsearch:9.4.8")
				.asCompatibleSubstituteFor("docker.elastic.co/elasticsearch/elasticsearch"))
				.withEnv("xpack.security.enabled", "false")
				.withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");
	}

}
