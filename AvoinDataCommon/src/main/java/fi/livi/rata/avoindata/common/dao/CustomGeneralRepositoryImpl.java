package fi.livi.rata.avoindata.common.dao;

import java.io.Serializable;
import java.util.Collection;
import javax.persistence.EntityManager;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.support.JpaMetamodelEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;

public class CustomGeneralRepositoryImpl<T, ID extends Serializable>
        extends SimpleJpaRepository<T, ID> implements CustomGeneralRepository<T, ID> {

    private final EntityManager entityManager;

    @Autowired
    public CustomGeneralRepositoryImpl(JpaMetamodelEntityInformation domainClass, EntityManager entityManager) {
        super(domainClass, entityManager);

        // This is the recommended method for accessing inherited class dependencies.
        this.entityManager = entityManager;
    }

    @Override
    public void persist(Collection<T> objects) {

        try {
            objects.stream().forEach(x -> entityManager.persist(x));
        } catch (RuntimeException e) {
            throw e;
        }

    }
}
