package fi.livi.rata.avoindata.common.dao;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.io.Serializable;
import java.util.Collection;

@NoRepositoryBean
public interface CustomGeneralRepository<T, ID extends Serializable>
        extends JpaRepository<T, ID> {

    void persist(Collection<T> objects);
}
