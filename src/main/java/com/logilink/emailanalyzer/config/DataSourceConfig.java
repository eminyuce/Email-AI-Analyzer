package com.logilink.emailanalyzer.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.concurrent.TimeUnit;

@Configuration
public class DataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(DataSourceConfig.class);

    @Bean
    public DataSource dataSource(
            @Value("${spring.datasource.url}") String jdbcUrl,
            @Value("${spring.datasource.username}") String jdbcUser,
            @Value("${spring.datasource.password}") String jdbcPass,
            AppSecretsDebugProperties secretsDebug,
            @Value("${spring.datasource.hikari.pool-factor:2}") int mysqlPoolFactor,
            @Value("${spring.datasource.hikari.idle-timeout:900}") int mysqlIdlePoolTimeout,
            @Value("${spring.datasource.hikari.connection-timeout:30}") int mysqlConnectionPoolTimeout,
            @Value("${spring.datasource.hikari.prepared-cache-enabled:true}") String mysqlCachePrepStatements,
            @Value("${spring.datasource.hikari.prepared-cache-size:250}") String mysqlCachePrepStatementsCount,
            @Value("${spring.datasource.hikari.prepared-cache-sql-limit:2048}") String mysqlCachePrepStatementsSize
    ) {
        var config = new HikariConfig();
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(jdbcUser);
        config.setPassword(jdbcPass);

        int processors = Runtime.getRuntime().availableProcessors();
        config.setMinimumIdle(processors + 1);
        config.setMaximumPoolSize((processors * mysqlPoolFactor) + 1);

        config.setIdleTimeout(TimeUnit.SECONDS.toMillis(mysqlIdlePoolTimeout));
        config.setConnectionTimeout(TimeUnit.SECONDS.toMillis(mysqlConnectionPoolTimeout));
        config.setConnectionTestQuery("select 1");

        config.addDataSourceProperty("cachePrepStmts", mysqlCachePrepStatements);
        config.addDataSourceProperty("prepStmtCacheSize", mysqlCachePrepStatementsCount);
        config.addDataSourceProperty("prepStmtCacheSqlLimit", mysqlCachePrepStatementsSize);

        if (secretsDebug.isDebugLogSecrets()) {
            log.warn(
                    "DEBUG_LOG_SECRETS: spring.datasource.url=[{}], username=[{}], password=[{}]",
                    jdbcUrl,
                    jdbcUser,
                    jdbcPass
            );
        }

        log.info("DB connection pool initialized with poolSize (min: {}, max: {})",
                config.getMinimumIdle(), config.getMaximumPoolSize());

        return new HikariDataSource(config);
    }
}
