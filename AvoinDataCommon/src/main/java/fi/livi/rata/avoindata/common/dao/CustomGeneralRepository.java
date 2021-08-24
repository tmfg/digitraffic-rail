package fi.livi.rata.avoindata.common.dao;


import java.io.Serializable;
import java.util.Collection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import com.amazonaws.xray.spring.aop.XRayEnabled;

@NoRepositoryBean
@XRayEnabled
public interface CustomGeneralRepository<T, ID extends Serializable>
        extends JpaRepository<T, ID> {

    void persist(Collection<T> objects);
}
