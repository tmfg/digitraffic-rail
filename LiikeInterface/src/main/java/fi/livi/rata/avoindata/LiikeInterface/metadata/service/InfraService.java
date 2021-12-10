package fi.livi.rata.avoindata.LiikeInterface.metadata.service;

import fi.livi.rata.avoindata.LiikeInterface.domain.entities.infra.Infra;
import fi.livi.rata.avoindata.LiikeInterface.metadata.repository.InfraRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class InfraService {
    private Logger logger = LoggerFactory.getLogger(InfraService.class);

    @Autowired
    private InfraRepository infraRepository;

    public Infra getCurrentInfra() {
        final LocalDate date = LocalDate.now();
        final Infra infra = infraRepository.findInfraByDate(date);
        if (infra == null) {
            logger.error("Could not find infra for date {}", date);
        }
        return infra;
    }
}
