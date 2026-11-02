package br.com.hubregistro.security.tenant;

import org.springframework.beans.factory.config.BeanPostProcessor;

import javax.sql.DataSource;

/** Envolve o DataSource da aplicação em {@link TenantAwareDataSource}. */
public class RlsDataSourcePostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof DataSource dataSource && !(bean instanceof TenantAwareDataSource)) {
            return new TenantAwareDataSource(dataSource);
        }
        return bean;
    }
}
