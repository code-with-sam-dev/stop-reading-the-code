package dev.example.payments.acceptance;

import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Counts every statement the service sends to the database. */
@TestConfiguration
class QueryCounter {

    static final AtomicInteger QUERIES = new AtomicInteger();

    @Bean
    static BeanPostProcessor countQueries() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String name) {
                if (bean instanceof DataSource ds && !name.contains("Proxy")) {
                    return ProxyDataSourceBuilder.create(ds)
                            .afterQuery((exec, queries) -> QUERIES.addAndGet(queries.size()))
                            .build();
                }
                return bean;
            }
        };
    }
}
