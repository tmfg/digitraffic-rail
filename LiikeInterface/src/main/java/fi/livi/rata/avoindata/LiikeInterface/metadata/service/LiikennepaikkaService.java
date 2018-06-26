package fi.livi.rata.avoindata.LiikeInterface.metadata.service;

import fi.livi.rata.avoindata.LiikeInterface.domain.entities.Liikennepaikka;
import fi.livi.rata.avoindata.LiikeInterface.metadata.repository.LiikennepaikkaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LiikennepaikkaService {
    @Autowired
    LiikennepaikkaRepository liikennepaikkaRepository;

    @Autowired
    private InfraService infraService;

    public List<Liikennepaikka> getLiikennepaikkas() {
        return liikennepaikkaRepository.findAllLiikennepaikkaOrSeisakeByInfraId(infraService.getCurrentInfra().id);
    }
}
