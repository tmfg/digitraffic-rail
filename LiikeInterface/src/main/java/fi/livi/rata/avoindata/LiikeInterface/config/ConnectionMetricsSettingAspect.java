package fi.livi.rata.avoindata.LiikeInterface.config;

import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

@Aspect
@Component
public class ConnectionMetricsSettingAspect {
    private Properties properties = new Properties();

    @Autowired
    public ConnectionMetricsSettingAspect(@Value("${liikeinterface.datasource.action:AvoinData}") String action,
            @Value("${liikeinterface.datasource.module:AvoinData}") String module,
            @Value("${liikeinterface.datasource.clientid:AvoinData}") String clientid) {
        properties.put("OCSID.ACTION", action);
        properties.put("OCSID.MODULE", module);
        properties.put("OCSID.CLIENTID", clientid);
    }

    @AfterReturning(pointcut = "execution(* com.zaxxer.hikari.HikariDataSource.getConnection(..) )", returning = "connection")
    public void setMetrics(Connection connection) throws SQLException {
        connection.setClientInfo(properties);
    }
}
